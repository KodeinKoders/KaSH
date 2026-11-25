/// <reference path="../kash-plugin.d.ts" />
// Messenger's plugin (Facebook's messages, www.facebook.com/messages/: messenger.com sends there): reads the unread conversations of its list.
//
// Facebook's page is Meta's (Comet: compiled classes, no React props on its elements, its modules out of a steady reach), so it reads the list
// of conversations, there on the pages of the messages only: each conversation is a `[role="row"]` with a link to it, `/messages/t/<id>/`, or
// `/messages/e2ee/t/<id>/` for one encrypted end to end, whose path after `/messages/` is its id. Its texts are its name, the start of its last
// message, "·", and the time of it ("3 h", "13 sem.", in the page's language). An unread conversation is in bold (its name's `font-weight` 600,
// 500 read), with a blue dot: Facebook does not count its messages, so it counts 1 (the page's title counts Facebook's notifications too). A
// group's image is its members' (two images side by side), a person's one image. One is marked as read through its row's menu.
//
// Not seen yet when it was written: a muted conversation, a message request, the rows that the list loads as it is scrolled (all of them were
// there).

const rows = () => [...document.querySelectorAll('[role="row"]')]
    .map((row) => ({ row, link: row.querySelector('a[href*="/messages/t/"], a[href*="/messages/e2ee/t/"]') }))
    .filter(({ link }) => link);

// The conversation's id: its address's path after /messages/, `t/<id>` or `e2ee/t/<id>`.
const idOf = (link) => {
    const match = (link.getAttribute('href') || '').match(/\/messages\/((?:e2ee\/)?t\/[^/?#]+)/);
    return match ? match[1] : null;
};

// The texts of a row, in their order: its name, the start of its last message, "·", its time.
const textsOf = (link) => [...link.querySelectorAll('span')]
    .filter((span) => [...span.childNodes].some((node) => node.nodeType === Node.TEXT_NODE && node.textContent.trim()));

const unread = (texts) => texts.length > 0 && Number(getComputedStyle(texts[0]).fontWeight) >= 600;

// A group shows its members' images, side by side; a person, one image. The small ones (16 px) are those of who saw the last message, and
// emojis.
const kindOf = (row) => [...row.querySelectorAll('img, image')].filter((image) => image.getBoundingClientRect().width > 24).length > 1 ? 'group' : 'direct';

// The time of the last message, from its label: "5 min" and "3 h" (kash.time.parse), "2 j", "13 sem.", "4 d", "2 w" (to the day). A year is
// too far to say.
const DAY = 24 * 60 * 60 * 1000;
const timeOf = (texts) => {
    const label = texts.length > 2 ? kash.dom.text(texts[texts.length - 1]) : '';
    if (!label) return null;
    const parsed = kash.time.parse(label);
    if (parsed) return parsed;
    const match = label.toLowerCase().match(/^(\d+) ?(j|d|sem|w)\b/);
    if (!match) return null;
    const days = Number(match[1]) * (match[2] === 'sem' || match[2] === 'w' ? 7 : 1);
    const date = new Date(Date.now() - days * DAY);
    return { time: new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime(), timePrecision: 'day' };
};

const onMessages = () => location.pathname.startsWith('/messages/');

// A spinner over the tile until the list can be read (the page loads, the service signs in): KaSH takes it away after 2 minutes.
kash.setLoading(true);
kash.watch(() => {
    // The list is only on the pages of the messages: elsewhere (the feed, a profile), nothing to say.
    if (!onMessages()) {
        // Nothing to wait for on a page that has no list.
        kash.setLoading(false);
        return null;
    }
    const all = rows();
    if (all.length === 0) return null;
    // The list can be read: the service loaded.
    kash.setLoading(false);
    const conversations = [];
    for (const { row, link } of all) {
        const id = idOf(link);
        const texts = textsOf(link);
        if (!id || !unread(texts) || conversations.some((conversation) => conversation.id === id)) continue;
        conversations.push({
            id,
            name: kash.dom.text(texts[0]),
            kind: kindOf(row),
            count: 1,
            preview: texts.length > 1 ? kash.dom.text(texts[1]) : '',
            ...(timeOf(texts) || {}),
        });
    }
    return conversations;
});

// The conversations of the list, which the search lists: the rows that it loaded.
kash.contacts.watch(() => {
    if (!onMessages()) return null;
    const all = rows();
    if (all.length === 0) return null;
    return all.map(({ row, link }) => ({ id: idOf(link), name: kash.dom.text(textsOf(link)[0]), kind: kindOf(row) })).filter((contact) => contact.id);
});

// Marks a conversation as read as the user would: its row's menu (its button `[aria-haspopup="menu"]`), whose first item is "Mark as read"
// while it is unread ("Mark as unread" once it is read, in the language of Facebook's interface): only an unread row's is clicked.
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

kash.onMarkRead(async (id) => {
    const found = rows().find(({ link }) => idOf(link) === id);
    if (!found || !unread(textsOf(found.link))) return;
    const button = found.row.querySelector('[role="button"][aria-haspopup="menu"]');
    if (!button) return;
    // The menu that the click shows, not another one that the document still has (an earlier one, a hidden one): a menu that was not shown
    // before the click (a new one, or one that the page shows again).
    const shown = (menu) => menu.getBoundingClientRect().width > 0;
    const before = new Set([...document.querySelectorAll('[role="menu"]')].filter(shown));
    button.click();
    for (let tries = 0; tries < 20; tries++) {
        await wait(100);
        const menu = [...document.querySelectorAll('[role="menu"]')].find((candidate) => !before.has(candidate) && shown(candidate));
        const item = menu && menu.querySelector('[role="menuitem"]');
        if (item) {
            item.click();
            return;
        }
    }
});

kash.onOpenConversation((id) => {
    // Its link, clicked as the user would: Facebook opens it without loading the page again.
    const found = rows().find(({ link }) => idOf(link) === id);
    if (found) {
        found.link.click();
        return;
    }
    // A conversation whose row is not there (another page of Facebook than the messages): its address.
    if (/^(e2ee\/)?t\/[\w.-]+$/.test(id)) location.assign(`/messages/${id}/`);
});
