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
// A conversation appears in Favorites and in RecentChats: once.
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

kash.watch(() => {
    const all = conversationRows();
    // No chat list (another of Teams' apps is shown, the calendar, the calls): nothing to say.
    if (all.length === 0) return null;
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

kash.onOpenConversation((id) => {
    // Its row, clicked as the user would: Teams has no address per conversation that the page follows.
    const row = rows().find((candidate) => idOf(candidate) === id);
    const target = row && (row.querySelector('[data-inp]') || row);
    if (target) target.click();
});
