/// <reference path="../kash-plugin.d.ts" />
// Microsoft Teams' plugin (teams.cloud.microsoft): reads the unread conversations of its chat list.
//
// Teams' page has no address per view (its path stays `/`) and no model that a script can reach, but its chat list is a tree (Fluent UI's)
// whose items say what they are: a folder (`aria-level="1"`, `data-conversation-folder`: QuickViews, Favorites, RecentChats, TeamsAndChannels,
// EngageCommunities) holds the conversations (`aria-level="2"`, `data-item-type`: `chat`, `engage-community`), whose `data-fui-tree-item-value`
// ends with the conversation's id after its last `|`: `19:…@unq.gbl.spaces` a conversation between two people, `19:…@thread.v2` a group's or a
// meeting's (`19:meeting_…`), `48:notes` the user's own notes (left out), `19:…@EngageCommunity` a Viva Engage community (the QuickViews'
// entries, `slice`, are no conversations). The elements of a row have ids made from it: `title-<kind>-list-item_<id>` its name
// (`title-chat-list-item_…`, `title-engage-community-list-item_…`), `message-preview-chat-list-item_<id>` the start of its last message and
// `time-chat-list-item_<id>` its time, both empty or absent when the list is compact (Teams' setting).
//
// An unread conversation has a dot (`[data-testid="dot-badge-container"]`), no count: it counts 1 (the page's title, `(1) …`, counts those). Its
// row is labelled by `chat_list_unread_text` too, and its name is in bold. A community only in bold is a topic: Teams' title does not count it.
// A conversation appears in Favorites and in RecentChats: once. The row's "…" menu, which Teams adds while the row is pointed at, marks it as read.
//
// Not seen yet when it was written: a channel of a team (the TeamsAndChannels folder was empty), a mention, a muted conversation, the time and the
// preview (the list was compact), the rows that the list loads as it is scrolled.

const NOTES = '48:notes';

const idOf = (row) => {
    const value = row.getAttribute('data-fui-tree-item-value') || '';
    return value.slice(value.lastIndexOf('|') + 1);
};

// The conversations of the tree: an id of Teams' (`19:…`, `48:…`), not a QuickViews' entry nor a folder's placeholder.
const rows = () => [...document.querySelectorAll('[role="treeitem"][aria-level="2"][data-fui-tree-item-value][data-item-type]')]
    .filter((row) => /^\d+:/.test(idOf(row)));

// The element of the row with an id made from its conversation's (getElementById: the id has `:` and `@`, which a selector would have to escape).
const partOf = (id, part) => document.getElementById(`${part}-chat-list-item_${id}`);

// Its name: `title-chat-list-item_<id>`, `title-engage-community-list-item_<id>`…
const titleOf = (row) => row.querySelector('[id^="title-"]');
const nameOf = (row) => kash.dom.text(titleOf(row));

const kindOf = (id) => {
    if (id.endsWith('@unq.gbl.spaces')) return 'direct';
    if (id.endsWith('@EngageCommunity') || id.endsWith('@thread.tacv2') || id.endsWith('@thread.skype')) return 'channel';
    return 'group';
};

const isUnread = (row) => !!row.querySelector('[data-testid="dot-badge-container"]')
    || (row.getAttribute('aria-labelledby') || '').split(' ').includes('chat_list_unread_text');

const isBold = (row) => {
    const title = titleOf(row);
    return !!title && parseInt(getComputedStyle(title).fontWeight, 10) >= 600;
};

// The conversations of the list, each once (a favorite is in RecentChats too), without the user's notes.
const conversationRows = () => {
    const seen = new Set();
    return rows().filter((row) => {
        const id = idOf(row);
        if (!id || id === NOTES || seen.has(id)) return false;
        seen.add(id);
        return true;
    });
};

// A spinner over the tile until the list can be read (the page loads, the service signs in): KaSH takes it away after 2 minutes.
kash.setLoading(true);
kash.watch(() => {
    const all = conversationRows();
    // No chat list (another of Teams' apps is shown, the calendar, the calls): nothing to say.
    if (all.length === 0) return null;
    // The list can be read: the service loaded.
    kash.setLoading(false);
    const conversations = [];
    for (const row of all) {
        const id = idOf(row);
        const name = nameOf(row);
        if (!name) continue;
        const kind = kindOf(id);
        if (isUnread(row)) {
            conversations.push({
                id,
                name,
                kind,
                count: 1,
                preview: kash.dom.text(partOf(id, 'message-preview')) || undefined,
                ...(kash.time.parse(kash.dom.text(partOf(id, 'time'))) || {}),
            });
        } else if (kind === 'channel' && isBold(row)) {
            // New posts that Teams does not count (its title does not): a topic.
            conversations.push({ id, name, kind, topic: true });
        }
    }
    return conversations;
});

// The conversations of the list, which the search lists: the rows that it loaded.
kash.contacts.watch(() => {
    const all = conversationRows();
    if (all.length === 0) return null;
    return all.map((row) => {
        const id = idOf(row);
        return { id, name: nameOf(row), kind: kindOf(id) };
    }).filter((contact) => contact.name);
});

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// The pointer over the row (or out of it), as React reads it: Teams adds the row's "…" button only while it is pointed at.
const point = (target, over) => {
    const box = target.getBoundingClientRect();
    const at = { bubbles: true, view: window, clientX: box.x + box.width / 2, clientY: box.y + box.height / 2 };
    for (const type of over ? ['pointerover', 'mouseover', 'pointermove', 'mousemove'] : ['pointerout', 'mouseout']) {
        target.dispatchEvent(new (type.startsWith('pointer') ? PointerEvent : MouseEvent)(type, at));
    }
};

// Marked as read through the row's "…" menu (`more-menu-trigger`), whose first item is "Mark as read" for an unread conversation
// (`change-read-status-menu-item`; "Mark as unread", `change-unread-status-menu-item`, for a read one) and for a community
// (`mark-as-read-engage-community-menu-item`). The menu is a layer of its own, out of the row.
const MARK_AS_READ = '[data-tid="change-read-status-menu-item"], [data-testid="mark-as-read-engage-community-menu-item"]';

kash.onMarkRead(async (id) => {
    const row = rows().find((candidate) => idOf(candidate) === id && (isUnread(candidate) || (kindOf(id) === 'channel' && isBold(candidate))));
    if (!row) return;
    const target = row.querySelector('[data-inp]') || row;
    point(target, true);
    let trigger = null;
    for (let tries = 0; tries < 20 && !trigger; tries++) {
        await wait(50);
        trigger = row.querySelector('[data-testid="more-menu-trigger"]');
    }
    if (!trigger) return point(target, false);
    // The menu that the click shows, not another one that the document still has (an earlier one, a hidden one): a menu that was not shown
    // before the click (a new one, or one that the page shows again).
    const shown = (menu) => menu.getBoundingClientRect().width > 0;
    const before = new Set([...document.querySelectorAll('[role="menu"]')].filter(shown));
    trigger.click();
    for (let tries = 0; tries < 20; tries++) {
        await wait(100);
        const menu = [...document.querySelectorAll('[role="menu"]')].find((candidate) => !before.has(candidate) && shown(candidate));
        if (!menu) continue;
        const item = menu.querySelector(MARK_AS_READ);
        // No such item (the row was read meanwhile, a kind of conversation whose menu has none): the menu is closed again.
        if (item) item.click();
        else menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        break;
    }
    point(target, false);
});

kash.onOpenConversation((id) => {
    // Its row, clicked as the user would: Teams has no address per conversation that the page follows.
    const row = rows().find((candidate) => idOf(candidate) === id);
    const target = row && (row.querySelector('[data-inp]') || row);
    if (target) target.click();
});
