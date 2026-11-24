/// <reference path="../kash-plugin.d.ts" />
// Google Messages' plugin (Messages for web: the SMS and RCS of the Android phone that is paired with it): reads the unread conversations of its
// list.
//
// Messages for web has no model that a page's script can reach (Angular in production: an element's context is a number, not its component), and
// its IndexedDB holds caches, not the conversations. Its list describes each conversation with attributes that it keeps for its own tests
// (`data-e2e-…`), steadier than its classes: whether it is unread, muted, pinned, its name and its snippet. It shows no count: an unread
// conversation counts as 1. A muted one is left out. The list loads its rows as it is scrolled: an unread conversation whose row is not loaded is
// not seen. Its rows are also the search's contacts (`kash.contacts`).
//
// It shows no time either, only a label: "Now", "3 min" for a message of today, then the weekday ("Fri") and the date ("Nov 7") for older ones,
// in the language of the page, which is KaSH's ("Maintenant", "ven.", "7 nov."). The time is read from it (`kash.time.parse`): to the minute
// today, to the day before.

const rows = () => [...document.querySelectorAll('mws-conversation-list-item a[data-e2e-is-unread]')];

// The conversation's id, the last part of its row's address (`/web/u/0/conversations/<id>`).
const idOf = (row) => {
    const match = (row.getAttribute('href') || '').match(/\/conversations\/([^/?#]+)/);
    return match ? match[1] : null;
};

// When the last message came, from the row's label, as {time, timePrecision}; null for a label that is not understood.
const timeOf = (row) => {
    const stamp = row.querySelector('mws-relative-timestamp');
    // A weekday has its whole name for screen readers ("Friday"), next to its short one ("Fri").
    return stamp ? kash.time.parse(kash.dom.text(stamp.querySelector('.weekday-aria-label') || stamp)) : null;
};

kash.watch(() => {
    const all = rows();
    // The list is not there yet (the page is loading, or the phone is not paired): nothing to say.
    if (all.length === 0) return null;
    const conversations = [];
    for (const row of all) {
        if (row.dataset.e2eIsUnread !== 'true' || row.dataset.e2eIsMuted === 'true') continue;
        const id = idOf(row);
        if (!id || conversations.some((conversation) => conversation.id === id)) continue;
        conversations.push({
            id,
            name: kash.dom.text(row.querySelector('[data-e2e-conversation-name]')),
            // A group has not been seen yet: every conversation is a direct one until it is.
            kind: 'direct',
            count: 1,
            preview: kash.dom.text(row.querySelector('[data-e2e-conversation-snippet]')),
            ...(timeOf(row) || {}),
        });
    }
    return conversations;
});

// The conversations of the list, which the search lists: the rows that it loaded (it loads them as it is scrolled), muted ones too.
kash.contacts.watch(() => {
    const all = rows();
    if (all.length === 0) return null;
    return all.map((row) => ({ id: idOf(row), name: kash.dom.text(row.querySelector('[data-e2e-conversation-name]')), kind: 'direct' }));
});

// Marks a conversation as read by opening it, which Messages has instead of a "Mark as read" (its menu only has "Mark as unread"): KaSH's API
// (kash.markReadByOpening) opens it, waits for it to be read and goes back to what was open before, one at a time. KaSH says it in its item,
// "Mark as read (by opening it)".
const rowOf = (id) => rows().find((candidate) => idOf(candidate) === id);
kash.markReadByOpening({
    open: (id) => {
        const row = rowOf(id);
        if (!row) return false;
        row.click();
        return true;
    },
    isUnread: (id) => {
        const row = rowOf(id);
        return row ? row.dataset.e2eIsUnread === 'true' : null;
    },
});

kash.onOpenConversation((id) => {
    // Its row, clicked as the user would: Messages opens it without loading the page again.
    const row = rows().find((candidate) => idOf(candidate) === id);
    if (row) {
        row.click();
        return;
    }
    // A conversation whose row is not loaded: its address, in the same account.
    const account = location.pathname.match(/^\/web\/u\/(\d+)\//);
    location.assign(`/web/u/${account ? account[1] : 0}/conversations/${encodeURIComponent(id)}`);
});
