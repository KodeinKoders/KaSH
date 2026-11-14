// Injected by KaSH in the Google Chat services only, in every document, before the page's own scripts (see notification-shim.js).
//
// Reads the unread conversations from Google Chat's side panel, and sends them to KaSH through `window.cefQuery` when they change:
//     {type: "unread", conversations: [{id, name, kind: "direct" | "group", count, mention, preview, time}]}
// Google Chat has no model that a page's script can reach (its code is compiled, and it keeps nothing in IndexedDB): the side panel is what it
// shows, and what it counts. A conversation counts when its row has Google Chat's badge: a direct message (and a message request, from an account
// outside the organization) with its count, a space only when the user is mentioned in it. A space with new messages and no mention is only in bold
// in Google Chat, without a number, and is left out as Google Chat leaves it out. The preview is the one of the Home list, when it is rendered, and
// kept while it is not.
//
// KaSH calls `__kashOpenConversation(id)` to open a conversation, when its row of the Unread tab is clicked.
(() => {
    if (window.top !== window || location.hostname !== 'chat.google.com' || window.__kashOpenConversation) return;

    // How often the side panel is read.
    const PERIOD_MS = 2000;

    // The badge of a row (its count, or a mark), as Google Chat draws it: its classes are compiled names, the only thing that tells it.
    const BADGE = '.SaMfhe';

    const send = (message) => {
        try {
            if (typeof window.cefQuery === 'function') {
                window.cefQuery({ request: JSON.stringify(message), persistent: false, onSuccess() {}, onFailure() {} });
            }
        } catch (e) {
            // KaSH misses this update; the next one carries the same state.
        }
    };

    // Without the marks of direction that names and previews may have, and with one space between words.
    const clean = (text) => (text || '').replace(/[‪-‮⁦-⁩]/g, '').replace(/\s+/g, ' ').trim();

    // What an element shows as text: its text, and its emojis, which Google Chat draws as images whose `alt` is the emoji.
    const shown = (element) => {
        let text = '';
        for (const node of element.childNodes) {
            if (node.nodeType === Node.TEXT_NODE) text += node.textContent;
            else if (node.nodeName === 'IMG') text += node.getAttribute('alt') || '';
            else if (node.nodeType === Node.ELEMENT_NODE) text += shown(node);
        }
        return text;
    };

    // The rows of the side panel: its sections (starred, direct messages, spaces, apps) are groups, the Home list is not in one.
    const panelRows = () => [...document.querySelectorAll('[role="group"] [data-group-id]')];

    // A row's name: a person's name is marked as not to be translated, a space's is the first line of its link (a direct message's first line is
    // the person's status, for screen readers).
    const nameOf = (row) => {
        const person = row.querySelector('[translate="no"]');
        if (person) return clean(shown(person));
        const link = row.querySelector('[role="link"]');
        return link ? clean(link.innerText.split('\n')[0]) : '';
    };

    // A message request has no conversation yet: its row is one for all of them, and the sender's name tells it.
    const idOf = (row) => row.dataset.groupId.startsWith('pinned-item') ? `request:${nameOf(row)}` : row.dataset.groupId;

    const kindOf = (row) => row.dataset.groupId.startsWith('space/') ? 'group' : 'direct';

    // When the last message came, in milliseconds: the row's time, or the one that a message request shows.
    const timeOf = (row) => {
        const time = Number(row.dataset.displayTimestamp || row.querySelector('[data-absolute-timestamp]')?.dataset.absoluteTimestamp);
        return Number.isFinite(time) && time > 0 ? time : null;
    };

    // The previews of the Home list, by conversation: only the rows that are rendered have one (the list is virtualized, and is there only on Home).
    // A row of a thread is a reply in a conversation, whose own row has the preview of the conversation.
    const renderedPreviews = () => {
        const previews = new Map();
        for (const row of document.querySelectorAll('[data-group-id][data-is-unread]')) {
            if (row.closest('[role="group"]') || row.dataset.topicId) continue;
            // Its text with the sender of the last message, without what is only read to screen readers (`Message` before it).
            const preview = row.querySelector('.Hkj4n');
            if (preview) previews.set(row.dataset.groupId, clean(shown(preview)));
        }
        return previews;
    };

    // The last preview of each unread conversation, kept while the Home list is not rendered.
    const previewOf = new Map();

    let last = null;

    const read = () => {
        const rendered = renderedPreviews();
        const conversations = [];
        for (const row of panelRows()) {
            const badge = row.querySelector(BADGE);
            if (!badge) continue;
            const id = idOf(row);
            // A conversation that is starred is in its section and in the one of its kind.
            if (conversations.some((conversation) => conversation.id === id)) continue;
            if (rendered.has(row.dataset.groupId)) previewOf.set(id, rendered.get(row.dataset.groupId));
            const count = parseInt(badge.textContent, 10);
            const kind = kindOf(row);
            conversations.push({
                id,
                name: nameOf(row),
                kind,
                // A badge without a number (a mark) counts as 1.
                count: count > 0 ? count : 1,
                // A space has a badge only when the user is mentioned in it.
                mention: kind === 'group',
                preview: previewOf.get(id) || '',
                time: timeOf(row),
            });
        }
        // A conversation that is read again forgets its preview.
        for (const id of [...previewOf.keys()]) {
            if (!conversations.some((conversation) => conversation.id === id)) previewOf.delete(id);
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
            // A version of Google Chat that changed its page: KaSH shows nothing rather than break the page.
        }
    }, PERIOD_MS);

    Object.defineProperty(window, '__kashOpenConversation', {
        value: (id) => {
            // Its row of the side panel, clicked as the user would: Google Chat opens it without loading the page again.
            const row = panelRows().find((candidate) => idOf(candidate) === id);
            const target = row && (row.querySelector('[role="link"]') || row.querySelector('[role="button"]'));
            if (target) {
                target.click();
                return;
            }
            // A conversation whose row is not loaded (the side panel loads its rows as it is scrolled): its address, in the same account.
            const [type, key] = id.split('/');
            if ((type === 'dm' || type === 'space') && key) {
                const account = location.pathname.match(/^\/u\/\d+/);
                location.assign(`${account ? account[0] : ''}/app/chat/${key}`);
            }
        },
    });
})();
