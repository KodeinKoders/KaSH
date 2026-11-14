// Injected by KaSH in the Slack services only, in every document, before the page's own scripts (see notification-shim.js).
//
// Reads the unread conversations of the workspace from Slack's sidebar, and sends them to KaSH through `window.cefQuery` when they change:
//     {type: "unread", conversations: [{id, name, kind: "direct" | "group", count, mention, preview, time}]}
// Slack's web application has no model that a page's script can reach; its sidebar describes each conversation with attributes that it keeps for
// its own tests (`data-qa-channel-sidebar-…`: its id, its type, whether it is muted), which are steadier than its classes. A conversation counts
// when Slack gives it a badge: a direct message (and a group one) with its count, a channel only when the user is mentioned in it. A channel with
// new messages and no mention is only in bold, without a badge, and is left out, as Slack leaves it out. A muted conversation is left out, and so
// is the user's own direct message. The replies in threads count when Slack badges its Threads entry (a mention of the user in a thread): one row,
// named as Slack names it. The sidebar has no preview and no time: the rows of the Unread tab show what the conversation is instead.
//
// KaSH calls `__kashOpenConversation(id)` to open a conversation, when its row of the Unread tab is clicked.
(() => {
    if (window.top !== window || location.hostname !== 'app.slack.com' || window.__kashOpenConversation) return;

    // How often the sidebar is read.
    const PERIOD_MS = 2000;

    const send = (message) => {
        try {
            if (typeof window.cefQuery === 'function') {
                window.cefQuery({ request: JSON.stringify(message), persistent: false, onSuccess() {}, onFailure() {} });
            }
        } catch (e) {
            // KaSH misses this update; the next one carries the same state.
        }
    };

    // Without the marks of direction that names may have, and with one space between words.
    const clean = (text) => (text || '').replace(/[‪-‮⁦-⁩]/g, '').replace(/\s+/g, ' ').trim();

    // The conversations of the sidebar, rendered: its sections (starred, channels, direct messages, apps) are one list.
    const rows = () => [...document.querySelectorAll('[data-qa-channel-sidebar-channel-id]')];

    // Slack's types: a channel (public or private), a direct message, a group direct message.
    const kindOf = (row) => row.dataset.qaChannelSidebarChannelType === 'channel' ? 'group'
        : row.dataset.qaChannelSidebarChannelType === 'mpim' ? 'group'
        : 'direct';

    const nameOf = (row) => {
        const name = row.querySelector('[data-qa^="channel_sidebar_name_"]');
        return name ? clean(name.textContent) : '';
    };

    // Slack's Threads entry, which it shows at the top of the sidebar once there is a thread to see, badged when the user is mentioned in one.
    const THREADS_ID = 'threads';
    const threadsEntry = () => document.querySelector('[data-qa="channel_sidebar_vall_threads"]');

    let last = null;

    const read = () => {
        const conversations = [];
        for (const row of rows()) {
            if (row.dataset.qaChannelSidebarChannelIsMuted === 'true' || row.dataset.qaChannelSidebarIsYou === 'true') continue;
            const badge = row.querySelector('[data-qa="badge"]');
            if (!badge) continue;
            const id = row.dataset.qaChannelSidebarChannelId;
            // A conversation that is starred may be in its section and in the one of its kind.
            if (conversations.some((conversation) => conversation.id === id)) continue;
            const count = parseInt(badge.textContent, 10);
            const type = row.dataset.qaChannelSidebarChannelType;
            conversations.push({
                id,
                name: nameOf(row),
                kind: kindOf(row),
                // A badge without a number counts as 1.
                count: count > 0 ? count : 1,
                // A channel has a badge only when the user is mentioned in it; a group direct message has one for every message.
                mention: type === 'channel',
                preview: '',
                time: null,
            });
        }
        const threads = threadsEntry();
        const threadsBadge = threads && threads.querySelector('[data-qa="badge"]');
        if (threadsBadge) {
            const count = parseInt(threadsBadge.textContent, 10);
            const name = threads.querySelector('.p-channel_sidebar__name');
            conversations.push({
                id: THREADS_ID,
                // Slack's own word, in the language of its interface ("Fils de discussion").
                name: clean(name ? name.textContent : ''),
                kind: 'group',
                count: count > 0 ? count : 1,
                mention: true,
                preview: '',
                time: null,
            });
        }
        const json = JSON.stringify(conversations);
        if (json === last) return;
        last = json;
        send({ type: 'unread', conversations });
    };

    setInterval(() => {
        try {
            read();
        } catch (e) {
            // A version of Slack that changed its page: KaSH shows nothing rather than break the page.
        }
    }, PERIOD_MS);

    Object.defineProperty(window, '__kashOpenConversation', {
        value: (id) => {
            if (id === THREADS_ID) {
                const threads = threadsEntry();
                if (threads) (threads.querySelector('.p-channel_sidebar__name') || threads).click();
                return;
            }
            // Its row of the sidebar, clicked as the user would: Slack opens it without loading the page again.
            const row = rows().find((candidate) => candidate.dataset.qaChannelSidebarChannelId === id);
            if (row) {
                (row.querySelector('[data-qa^="channel_sidebar_name_"]') || row).click();
                return;
            }
            // A conversation whose row is not rendered (the sidebar is a virtual list): its address in the same workspace.
            const team = location.pathname.match(/^\/client\/([A-Z0-9]+)/);
            if (team && /^[A-Z0-9]+$/.test(id)) location.assign(`/client/${team[1]}/${id}`);
        },
    });
})();
