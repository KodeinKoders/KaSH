/// <reference path="../kash-plugin.d.ts" />
// LinkedIn's plugin (its messages, www.linkedin.com/messaging/): reads the unread conversations of its list.
//
// LinkedIn's page is an Ember application whose data is not on its elements (no link, no attribute, no component argument has a
// conversation's id), so it reads the list of conversations, there on the pages of the messages only (`/messaging/…`): each one is a `li` of
// `.msg-conversations-container__conversations-list` with its participants' names (`.msg-conversation-listitem__participant-names`), which are
// its id too (`name:<names>`: nothing else tells it), the start of its last message (`.msg-conversation-card__message-snippet`) and its time
// (`time`: "10:42", "13 nov.", read by kash.time.parse). An unread one has a badge with its count (`.msg-conversation-card__unread-count`, its
// `.notification-badge__count`); a group shows its members' images in a grid (`msg-facepile-grid--4`…; a person's is `--no-facepile`). The
// page's title counts LinkedIn's notifications too. Its rows are also the search's contacts.
//
// One is marked as read through its row's menu (`button.msg-thread-actions__control`), whose items (`.msg-thread-actions__dropdown-option`)
// have nothing but their text to tell them: "Mark as read" is found by its words in the languages of LinkedIn's interface, only for an unread row.
//
// Not seen yet when it was written: a muted conversation, a sponsored message, the rows that the list loads as it is scrolled.

const rows = () => [...document.querySelectorAll('.msg-conversations-container__conversations-list > li')]
    .filter((row) => row.querySelector('.msg-conversation-listitem__participant-names'));

const nameOf = (row) => kash.dom.text(row.querySelector('.msg-conversation-listitem__participant-names'));
const idOf = (row) => `name:${nameOf(row)}`;
const kindOf = (row) => row.querySelector('.msg-facepile-grid--no-facepile') ? 'direct' : 'group';
const badgeOf = (row) => row.querySelector('.msg-conversation-card__unread-count');

const countOf = (badge) => {
    const count = parseInt(kash.dom.text(badge.querySelector('.notification-badge__count') || badge), 10);
    return count > 0 ? count : 1;
};

const onMessaging = () => location.pathname.startsWith('/messaging/');

// A spinner over the tile until the list can be read (the page loads, the service signs in): KaSH takes it away after 2 minutes.
kash.setLoading(true);
kash.watch(() => {
    // The list is only on the pages of the messages: elsewhere (the feed, a profile), nothing to say.
    if (!onMessaging()) {
        // Nothing to wait for on a page that has no list.
        kash.setLoading(false);
        return null;
    }
    const all = rows();
    if (all.length === 0) return null;
    // The list can be read: the service loaded.
    kash.setLoading(false);
    const conversations = [];
    for (const row of all) {
        const badge = badgeOf(row);
        const name = nameOf(row);
        if (!badge || !name || conversations.some((conversation) => conversation.id === idOf(row))) continue;
        conversations.push({
            id: idOf(row),
            name,
            kind: kindOf(row),
            count: countOf(badge),
            preview: kash.dom.text(row.querySelector('.msg-conversation-card__message-snippet')),
            ...(kash.time.parse(kash.dom.text(row.querySelector('time'))) || {}),
        });
    }
    return conversations;
});

// The conversations of the list, which the search lists: the rows that it loaded.
kash.contacts.watch(() => {
    if (!onMessaging()) return null;
    const all = rows();
    if (all.length === 0) return null;
    return all.map((row) => ({ id: idOf(row), name: nameOf(row), kind: kindOf(row) }));
});

// "Mark as read" in the languages of LinkedIn's interface: its item has nothing else that tells it.
const MARK_AS_READ = new Set([
    'marquer comme lu', 'mark as read', 'als gelesen markieren', 'marcar como leído', 'marcar como lida', 'segna come letto', 'markeren als gelezen',
]);
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

kash.onMarkRead(async (id) => {
    const row = rows().find((candidate) => idOf(candidate) === id && badgeOf(candidate));
    const trigger = row && row.querySelector('button.msg-thread-actions__control');
    if (!trigger) return;
    trigger.click();
    for (let tries = 0; tries < 20; tries++) {
        await wait(100);
        const options = [...row.querySelectorAll('.msg-thread-actions__dropdown-option')];
        if (options.length === 0) continue;
        const item = options.find((option) => MARK_AS_READ.has(kash.dom.text(option).toLowerCase()));
        // No such item (another language): the menu is closed again, as its button closes it.
        if (item) item.click();
        else trigger.click();
        return;
    }
});

kash.onOpenConversation((id) => {
    // Its row, clicked as the user would: LinkedIn opens it without loading the page again. A conversation whose row is not loaded cannot be,
    // having no address that KaSH knows.
    const row = rows().find((candidate) => idOf(candidate) === id);
    const target = row && (row.querySelector('.msg-conversation-listitem__link') || row.querySelector('.msg-conversation-card__content--selectable') || row);
    if (target) target.click();
});
