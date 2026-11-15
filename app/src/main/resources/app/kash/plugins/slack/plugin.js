/// <reference path="../kash-plugin.d.ts" />
// Slack's plugin: reads the unread conversations of the workspace from Slack's sidebar.
//
// Slack's web application has no model that a page's script can reach; its sidebar describes each conversation with attributes that it keeps for
// its own tests (`data-qa-channel-sidebar-…`: its id, its type, whether it is muted), which are steadier than its classes. A conversation counts
// when Slack gives it a badge: a direct message (and a group one) with its count, a channel only when the user is mentioned in it. A channel with
// new messages and no mention is only in bold, without a badge, and is left out, as Slack leaves it out. A muted conversation is left out, and so
// is the user's own direct message. The replies in threads count when Slack badges its Threads entry (a mention of the user in a thread): one row,
// named as Slack names it. The sidebar has no preview and no time: the rows of the Unread tab show what the conversation is instead. The image of
// the workspace, when it has one, is the service's icon.

// The conversations of the sidebar, rendered: its sections (starred, channels, direct messages, apps) are one list.
const rows = () => [...document.querySelectorAll('[data-qa-channel-sidebar-channel-id]')];

// Slack's types: a channel (public or private), a direct message, a group direct message.
const kindOf = (row) => {
    const type = row.dataset.qaChannelSidebarChannelType;
    return type === 'channel' || type === 'mpim' ? 'group' : 'direct';
};

const nameOf = (row) => kash.dom.text(row.querySelector('[data-qa^="channel_sidebar_name_"]'));

// Slack's Threads entry, which it shows at the top of the sidebar once there is a thread to see, badged when the user is mentioned in one.
const THREADS_ID = 'threads';
const threadsEntry = () => document.querySelector('[data-qa="channel_sidebar_vall_threads"]');

// The icon of the current workspace, Slack's button that switches accounts: its image, which Slack sets as its background (avatars.slack-edge.com, public), or its
// initials (`c-team_icon--default`) when the workspace has none.
const workspaceIcon = () => document.querySelector('.p-account_switcher__team_icon--current');

// The URL of the workspace's image, null when it has none, undefined while Slack has not rendered it. Slack shows it at 88 px and has it at
// 132 px too, which a tile of KaSH needs on a Retina screen.
const workspaceImage = () => {
    const icon = workspaceIcon();
    if (!icon) return undefined;
    if (icon.classList.contains('c-team_icon--default')) return null;
    const url = getComputedStyle(icon).backgroundImage.match(/^url\("?(https:[^")]+)"?\)$/);
    return url ? url[1].replace(/_88\.png$/, '_132.png') : undefined;
};

kash.watch(() => {
    // The API sends it only when it changed.
    const image = workspaceImage();
    if (image !== undefined) kash.setIcon(image);

    const conversations = [];
    for (const row of rows()) {
        if (row.dataset.qaChannelSidebarChannelIsMuted === 'true' || row.dataset.qaChannelSidebarIsYou === 'true') continue;
        const badge = row.querySelector('[data-qa="badge"]');
        if (!badge) continue;
        const id = row.dataset.qaChannelSidebarChannelId;
        // A conversation that is starred may be in its section and in the one of its kind.
        if (conversations.some((conversation) => conversation.id === id)) continue;
        const count = parseInt(badge.textContent, 10);
        conversations.push({
            id,
            name: nameOf(row),
            kind: kindOf(row),
            // A badge without a number counts as 1.
            count: count > 0 ? count : 1,
            // A channel has a badge only when the user is mentioned in it; a group direct message has one for every message.
            mention: row.dataset.qaChannelSidebarChannelType === 'channel',
        });
    }
    const threads = threadsEntry();
    const threadsBadge = threads && threads.querySelector('[data-qa="badge"]');
    if (threadsBadge) {
        const count = parseInt(threadsBadge.textContent, 10);
        conversations.push({
            id: THREADS_ID,
            // Slack's own word, in the language of its interface ("Fils de discussion").
            name: kash.dom.text(threads.querySelector('.p-channel_sidebar__name')),
            kind: 'group',
            count: count > 0 ? count : 1,
            mention: true,
        });
    }
    return conversations;
});

kash.onOpenConversation((id) => {
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
});
