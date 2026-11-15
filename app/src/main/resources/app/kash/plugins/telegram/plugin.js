/// <reference path="../kash-plugin.d.ts" />
// Telegram's plugin (Telegram Web K, web.telegram.org/k/): reads the unread conversations of its chat list.
//
// Telegram Web K keeps its state in its own modules and workers, out of a page script's reach, so it reads the chat list. Each row
// (`a.chatlist-chat`) is a conversation, whose id is its peer's (`data-peer-id`: a person's is positive, a group's or a channel's negative, and
// the row's address is `#<id>`): its name (`.dialog-title .peer-title`), its last message (`.dialog-subtitle`), the label of its time
// (`.message-time`: "01:46" today, "Oct 28" this year, "09/19/2025" another one, in the language of Telegram's interface) and its unread badge
// (`.dialog-subtitle-badge-unread`, with its count). The archive is a row of its own (`archive-dialog`), left out, as Telegram leaves its chats out
// of its counts. The list is virtual: a conversation whose row is not rendered is not seen; it is the list of the folder that is shown.
//
// Not seen yet when it was written: a group, a channel, a mention, a muted conversation (Telegram gives its badge a gray class).

const rows = () => [...document.querySelectorAll('a.chatlist-chat[data-peer-id]')];

const visible = (badge) => badge && badge.classList.contains('is-visible');

// The count of a badge: "3", or "1.2K" for a big one (the count is then at least 1000, which is enough to show).
const countOf = (badge) => {
    const text = kash.dom.text(badge).toUpperCase();
    const match = text.match(/^(\d+(?:[.,]\d+)?)\s*K$/);
    if (match) return Math.round(Number(match[1].replace(',', '.')) * 1000);
    const count = parseInt(text, 10);
    return count > 0 ? count : 1;
};

kash.watch(() => {
    const all = rows();
    // The list is not there yet (the page is loading, or the user is not logged in): nothing to say.
    if (all.length === 0) return null;
    const conversations = [];
    for (const row of all) {
        const id = row.dataset.peerId;
        if (!id || conversations.some((conversation) => conversation.id === id)) continue;
        const unread = row.querySelector('.dialog-subtitle-badge-unread');
        const mention = row.querySelector('.dialog-subtitle-badge-mention');
        if (!visible(unread) && !visible(mention)) continue;
        // A muted conversation, as Telegram leaves it out of its own count.
        if (row.classList.contains('is-muted') || (unread && unread.classList.contains('badge-gray'))) continue;
        conversations.push({
            id,
            name: kash.dom.text(row.querySelector('.dialog-title .peer-title')),
            // A group and a channel are not told apart by their row: both are groups until a channel is seen.
            kind: id.startsWith('-') ? 'group' : 'direct',
            count: visible(unread) ? countOf(unread) : 1,
            mention: visible(mention),
            preview: kash.dom.text(row.querySelector('.dialog-subtitle-parts') || row.querySelector('.dialog-subtitle')),
            ...(kash.time.parse(kash.dom.text(row.querySelector('.message-time'))) || {}),
        });
    }
    return conversations;
});

kash.onOpenConversation((id) => {
    // Its row, clicked as the user would.
    const row = rows().find((candidate) => candidate.dataset.peerId === id);
    if (row) {
        row.click();
        return;
    }
    // A conversation whose row is not rendered: its address, which Telegram follows.
    location.hash = `#${id}`;
});
