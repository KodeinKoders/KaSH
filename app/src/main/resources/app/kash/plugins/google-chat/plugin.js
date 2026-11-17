/// <reference path="../kash-plugin.d.ts" />
// Google Chat's plugin: reads the unread conversations from Google Chat's side panel.
//
// Google Chat has no model that a page's script can reach (its code is compiled, and it keeps nothing in IndexedDB): the side panel is what it
// shows, and what it counts. A conversation counts when its row has Google Chat's badge: a direct message (and a message request, from an account
// outside the organization) with its count, a space only when the user is mentioned in it. A space with new messages and no mention is only in
// bold in Google Chat, without a number: a topic, which nothing counts. The preview is the one of the Home list, when it is rendered, and kept
// while it is not. Its people and spaces are the search's contacts and topics (`kash.contacts`), from the same rows.
//
// Not seen yet when it was written: a space in bold. A row with new messages has the compiled class `H7du2` (seen on the direct messages with a
// badge, not on the read rows), and its row of the Home list `data-is-unread="true"`; either makes a row without a badge a topic.

// The badge of a row (its count, or a mark), as Google Chat draws it: its classes are compiled names, the only thing that tells it.
const BADGE = '.SaMfhe';

// A row with new messages, in bold: a compiled name too.
const UNREAD = 'H7du2';

// The rows of the side panel: its sections (starred, direct messages, spaces, apps) are groups, the Home list is not in one.
const panelRows = () => [...document.querySelectorAll('[role="group"] [data-group-id]')];

// A row's name: a person's name is marked as not to be translated, a space's is the first line of its link (a direct message's first line is the
// person's status, for screen readers).
const nameOf = (row) => {
    const person = row.querySelector('[translate="no"]');
    if (person) return kash.dom.text(person);
    const link = row.querySelector('[role="link"]');
    return link ? link.innerText.split('\n')[0].trim() : '';
};

// A message request has no conversation yet: its row is one for all of them, and the sender's name tells it.
const idOf = (row) => row.dataset.groupId.startsWith('pinned-item') ? `request:${nameOf(row)}` : row.dataset.groupId;

const kindOf = (row) => row.dataset.groupId.startsWith('space/') ? 'group' : 'direct';

// When the last message came, in milliseconds: the row's time, or the one that a message request shows.
const timeOf = (row) => {
    const time = Number(row.dataset.displayTimestamp || row.querySelector('[data-absolute-timestamp]')?.dataset.absoluteTimestamp);
    return Number.isFinite(time) && time > 0 ? time : null;
};

// The rows of the Home list, by conversation: only the rows that are rendered (the list is virtualized, and is there only on Home). A row of a
// thread is a reply in a conversation, whose own row has the preview of the conversation.
const homeRows = () => {
    const rows = new Map();
    for (const row of document.querySelectorAll('[data-group-id][data-is-unread]')) {
        if (row.closest('[role="group"]') || row.dataset.topicId) continue;
        rows.set(row.dataset.groupId, row);
    }
    return rows;
};

// Its text with the sender of the last message, without what is only read to screen readers (`Message` before it).
const previewOfRow = (row) => {
    const preview = row && row.querySelector('.Hkj4n');
    return preview ? kash.dom.text(preview) : null;
};

// The last preview of each unread conversation, kept while the Home list is not rendered.
const previewOf = new Map();

kash.watch(() => {
    const home = homeRows();
    const conversations = [];
    for (const row of panelRows()) {
        const badge = row.querySelector(BADGE);
        const homeRow = home.get(row.dataset.groupId);
        const bold = row.classList.contains(UNREAD) || (homeRow !== undefined && homeRow.dataset.isUnread === 'true');
        if (!badge && !bold) continue;
        const id = idOf(row);
        // A conversation that is starred is in its section and in the one of its kind.
        if (conversations.some((conversation) => conversation.id === id)) continue;
        const preview = previewOfRow(homeRow);
        if (preview !== null) previewOf.set(id, preview);
        if (!badge) {
            conversations.push({ id, name: nameOf(row), kind: kindOf(row), topic: true, preview: previewOf.get(id) || '', time: timeOf(row) });
            continue;
        }
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
    return conversations;
});

// The people and spaces of the side panel, which the search lists: the rows that it loaded (it loads them as it is scrolled), each once (a starred
// one is in two sections). Not the message requests, which have no conversation yet.
kash.contacts.watch(() => {
    const rows = panelRows();
    if (rows.length === 0) return null;
    return rows
        .filter((row) => /^(dm|space)\//.test(row.dataset.groupId))
        .map((row) => ({ id: row.dataset.groupId, name: nameOf(row), kind: kindOf(row) }));
});

kash.onOpenConversation((id) => {
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
});
