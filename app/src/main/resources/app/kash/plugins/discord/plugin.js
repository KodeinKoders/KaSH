/// <reference path="../kash-plugin.d.ts" />
// Discord's plugin (discord.com/app): reads the unread direct messages and servers of its column of servers.
//
// It reads the column of servers (`[data-list-id="guildsnav"]`), whose entries (`[class*="listItem_"]`, Discord's classes are compiled: read by
// their prefix) are the servers and, at its top, the direct messages that have unread ones. Each entry has its id in
// `[data-list-item-id="guildsnav___<id>"]` (a server's, or a direct message's channel), the badge of its count (`[class*="numberBadge_"]`: a server's
// mentions, a direct message's unread messages), a pill (`[class*="overlay_"] > span`, whose class has `visible_…` when the server has unread
// messages, and `selected_…` when it is the one shown), a label for screen readers (`[class*="hiddenVisually_"]`, in Discord's language:
// "<name>, 1 message non lu", "37 mentions, <name>", "Messages non lus, <name>") and, for a server, its name (`data-dnd-name`).
//
// A direct message counts, with its badge. A server with unread messages is a topic (the user's choice), whether the user is mentioned in it (its
// badge) or not (its pill): the column tells neither which of its channels nor how many messages. The column has no time nor preview. Its
// servers and direct messages are also the search's topics and contacts (`kash.contacts`).
//
// Not seen yet when it was written: a group direct message (its image is not a user's avatar), a closed folder of servers (whose servers may not
// be in the page, and whose badge is the folder's), a muted server (Discord gives it no pill; its mentions keep their badge).

const ENTRY_PREFIX = 'guildsnav___';
// The entries of the column that are not conversations.
const NOT_CONVERSATIONS = new Set(['home', 'create-join-button', 'guild-discover-button', 'app-download-button']);

const column = () => document.querySelector('[data-list-id="guildsnav"]');

const hasClass = (element, prefix) => !!element && [...element.classList].some((name) => name.startsWith(prefix));

// The count of a badge: "3", "99+", or "1.2K" for a big one; at least 1.
const countOf = (badge) => {
    const text = kash.dom.text(badge).toUpperCase();
    const thousands = text.match(/^(\d+(?:[.,]\d+)?)\s*K/);
    if (thousands) return Math.round(Number(thousands[1].replace(',', '.')) * 1000);
    const count = parseInt(text, 10);
    return count > 0 ? count : 1;
};

kash.watch(() => {
    const nav = column();
    // The column is not there yet (the page is loading, or the user is not logged in): nothing to say.
    if (!nav) return null;
    const conversations = [];
    for (const entry of nav.querySelectorAll('[class*="listItem_"]')) {
        const item = entry.querySelector(`[data-list-item-id^="${ENTRY_PREFIX}"]`);
        if (!item) continue;
        const id = item.getAttribute('data-list-item-id').slice(ENTRY_PREFIX.length);
        if (!id || NOT_CONVERSATIONS.has(id)) continue;
        const badge = entry.querySelector('[class*="numberBadge_"]');
        const label = kash.dom.text(entry.querySelector('[class*="hiddenVisually_"]'));
        const serverName = entry.querySelector('[data-dnd-name]')?.getAttribute('data-dnd-name');

        if (!serverName) {
            // A direct message: in the column only while it has unread messages. Its name is its label without the count ("<name>, 1 message non
            // lu"), cut at the last comma, which does not depend on the language.
            if (!badge) continue;
            const comma = label.lastIndexOf(',');
            const image = entry.querySelector('img')?.getAttribute('src') || '';
            conversations.push({
                id: `@me/${id}`,
                name: comma > 0 ? label.slice(0, comma).trim() : label,
                kind: image.includes('/avatars/') ? 'direct' : 'group',
                count: countOf(badge),
            });
            continue;
        }

        // A server with unread messages: a badge (the user is mentioned in it), or its pill, or, for the server that is shown (whose pill always
        // shows), a label that says something before its name ("Messages non lus, <name>").
        const pill = entry.querySelector('[class*="overlay_"] > span');
        const unread = !!badge || (hasClass(pill, 'selected_')
            ? label !== serverName && label.endsWith(`, ${serverName}`)
            : hasClass(pill, 'visible_'));
        if (unread) conversations.push({ id, name: serverName, kind: 'channel', topic: true });
    }
    return conversations;
});

// The servers of the column and its direct messages, which the search lists: every server (in an open folder), and the direct messages that it
// shows, those with unread ones; once seen, KaSH keeps them. Not yet the list of the direct messages of `/channels/@me`, which has them all
// while it is shown: it was not inspected yet.
kash.contacts.watch(() => {
    const nav = column();
    if (!nav) return null;
    const contacts = [];
    for (const entry of nav.querySelectorAll('[class*="listItem_"]')) {
        const item = entry.querySelector(`[data-list-item-id^="${ENTRY_PREFIX}"]`);
        if (!item) continue;
        const id = item.getAttribute('data-list-item-id').slice(ENTRY_PREFIX.length);
        if (!id || NOT_CONVERSATIONS.has(id)) continue;
        const serverName = entry.querySelector('[data-dnd-name]')?.getAttribute('data-dnd-name');
        if (serverName) {
            contacts.push({ id, name: serverName, kind: 'channel' });
            continue;
        }
        const label = kash.dom.text(entry.querySelector('[class*="hiddenVisually_"]'));
        const comma = label.lastIndexOf(',');
        const image = entry.querySelector('img')?.getAttribute('src') || '';
        contacts.push({ id: `@me/${id}`, name: comma > 0 ? label.slice(0, comma).trim() : label, kind: image.includes('/avatars/') ? 'direct' : 'group' });
    }
    return contacts;
});

kash.onOpenConversation((id) => {
    // Its entry of the column, clicked as the user would: Discord shows it without loading the page again.
    const entryId = id.startsWith('@me/') ? id.slice(4) : id;
    const item = column()?.querySelector(`[data-list-item-id="${ENTRY_PREFIX}${CSS.escape(entryId)}"]`);
    if (item) {
        item.click();
        return;
    }
    // An entry that is not in the page any more: its address, `/channels/@me/<id>` or `/channels/<id>`.
    if (/^(@me\/)?\d+$/.test(id)) location.assign(`/channels/${id}`);
});
