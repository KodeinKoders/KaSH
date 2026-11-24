/// <reference path="../kash-plugin.d.ts" />
// WhatsApp's plugin: reads the unread conversations from WhatsApp Web's live model (`window.require('WAWebCollections').Chat`, the module system
// of the page, which whatsapp-web.js uses too).
//
// Muted conversations (until a date, forever, or auto-muted by WhatsApp) and archived ones are left out, as WhatsApp leaves them out of its own
// count. A conversation marked as unread by hand (its count is -1) counts as 1. A group that was never named is named by its members, without
// the user, as WhatsApp shows it. The preview is the start of the last message as WhatsApp shows it in the row of the conversation (with the
// sender in a group), read from the rows that are rendered and kept while the row is scrolled out; no other message is read. Every conversation
// is a contact or a topic of the search (`kash.contacts`), from the same model, and one is marked as read through it (`sendSeen`).

// The modules exist once WhatsApp is loaded and logged in: before that, `require` throws, and there is nothing to read.
const module = (name) => {
    try {
        return typeof window.require === 'function' ? window.require(name) : null;
    } catch (e) {
        return null;
    }
};

const kind = (id) => id.endsWith('@g.us') ? 'group' : id.endsWith('@newsletter') ? 'channel' : 'direct';

const muted = (chat) => {
    if (chat.isAutoMuted) return true;
    const expiration = chat.mute ? chat.mute.expiration : 0;
    return expiration === -1 || expiration * 1000 > Date.now();
};

// The user's own ids (their LID and their phone number), which a group's members are compared with.
const me = () => {
    const prefs = module('WAWebUserPrefsMeUser');
    if (!prefs) return [];
    return [prefs.getMaybeMeLidUser && prefs.getMaybeMeLidUser(), prefs.getMaybeMePnUser && prefs.getMaybeMePnUser()]
        .filter(Boolean)
        .map((user) => user._serialized || String(user));
};

// A member's name as WhatsApp shows it in a group: the first name of the contact, else their name, the name they chose, or their LID's.
const memberName = (contact) => (contact && (contact.shortName || contact.name || contact.pushname || contact.displayNameLID)) || '';

// Names joined as WhatsApp joins them, in the language of its interface: `Alice, Bruno et Chloé` in French.
const list = (names) => {
    try {
        return new Intl.ListFormat(document.documentElement.lang || undefined, { style: 'long', type: 'conjunction' }).format(names);
    } catch (e) {
        return names.join(', ');
    }
};

// A group that was never named has no title: WhatsApp shows its members' names instead, without the user's.
const membersOf = (chat, myIds) => {
    const participants = chat.groupMetadata && chat.groupMetadata.participants;
    if (!participants || typeof participants.getModelsArray !== 'function') return '';
    return list(participants.getModelsArray()
        .filter((participant) => !myIds.includes(participant.id._serialized))
        .map((participant) => memberName(participant.contact))
        .filter(Boolean));
};

// Without the marks of direction (U+202A…U+202E, U+2066…U+2069) that WhatsApp puts around names in attributes.
const clean = (text) => (text || '').replace(/[‪-‮⁦-⁩]/g, '').trim();

// The previews of the last messages, as WhatsApp shows them in its rows (its own wording for calls, photos, group events…, and the sender in a
// group), by the name that the row shows: only the rows that are rendered (the list is virtualized) have one.
const renderedPreviews = () => {
    const previews = new Map();
    for (const row of document.querySelectorAll('#pane-side [role="row"]')) {
        const title = row.querySelector('[data-testid="cell-frame-title"] [title]');
        const preview = row.querySelector('[data-testid="last-msg-status"]');
        if (title && preview) previews.set(clean(title.getAttribute('title')), kash.dom.text(preview));
    }
    return previews;
};

// The last preview of each unread conversation, kept while its row is not rendered (scrolled out of the list).
const previewOf = new Map();

kash.watch(() => {
    const collections = module('WAWebCollections');
    if (!collections || !collections.Chat) return null;
    // A model that WhatsApp changed (a method or a field renamed) throws: nothing is sent, rather than every chat taken for unread.
    if (typeof collections.Chat.getModelsArray !== 'function') throw new Error('WAWebCollections.Chat.getModelsArray is not a function any more');
    const myIds = me();
    const rendered = renderedPreviews();
    const chats = collections.Chat.getModelsArray();
    if (chats.some((chat) => typeof chat.unreadCount !== 'number')) throw new Error('A chat of WhatsApp\'s model has no unreadCount any more');
    const conversations = chats
        .filter((chat) => chat.unreadCount !== 0 && !chat.archive && !muted(chat))
        .map((chat) => {
            const id = chat.id._serialized;
            const name = chat.formattedTitle || (kind(id) === 'group' ? membersOf(chat, myIds) : '');
            if (rendered.has(name)) previewOf.set(id, rendered.get(name));
            return {
                id,
                name,
                kind: kind(id),
                count: chat.unreadCount > 0 ? chat.unreadCount : 1,
                mention: !!chat.hasUnreadMention,
                preview: previewOf.get(id) || '',
                // The time of its last message (the chat's last activity, in seconds), in milliseconds.
                time: typeof chat.t === 'number' ? chat.t * 1000 : null,
            };
        });
    // A conversation that is read again forgets its preview.
    for (const id of [...previewOf.keys()]) {
        if (!conversations.some((conversation) => conversation.id === id)) previewOf.delete(id);
    }
    return conversations;
});

// Every conversation, which the search lists: the model has all of them (archived and muted too), the most recent first. Not the status updates
// nor the broadcast lists, which open no conversation.
kash.contacts.watch(() => {
    const collections = module('WAWebCollections');
    if (!collections || !collections.Chat) return null;
    const myIds = me();
    return collections.Chat.getModelsArray()
        .filter((chat) => !chat.id._serialized.endsWith('@broadcast'))
        .sort((a, b) => (b.t || 0) - (a.t || 0))
        .map((chat) => {
            const id = chat.id._serialized;
            return { id, name: chat.formattedTitle || (kind(id) === 'group' ? membersOf(chat, myIds) : ''), kind: kind(id) };
        })
        // A conversation without a name cannot be searched for.
        .filter((contact) => contact.name);
}, { complete: true });

// Marks a conversation as read, as opening it would: its messages are seen (WhatsApp sends its read receipts, as its own "Mark as read" does).
// One marked as unread by hand (its count is -1) has no message to see: its mark is taken off.
kash.onMarkRead(async (id) => {
    const collections = module('WAWebCollections');
    const chat = collections && collections.Chat && collections.Chat.get(id);
    if (!chat) return;
    if (chat.unreadCount === -1) {
        const commands = module('WAWebCmd');
        if (commands && commands.Cmd) commands.Cmd.markChatUnread(chat, false);
        return;
    }
    const seen = module('WAWebUpdateUnreadChatAction');
    if (!seen) return;
    // Its argument was the chat, and is now an object that holds it, as openChatBottom's.
    try {
        await seen.sendSeen({ chat });
    } catch (e) {
        await seen.sendSeen(chat);
    }
});

kash.onOpenConversation((id) => {
    const collections = module('WAWebCollections');
    const commands = module('WAWebCmd');
    const chat = collections && collections.Chat && collections.Chat.get(id);
    if (!chat || !commands || !commands.Cmd) return;
    // Its argument was the chat, and is now an object that holds it: whatsapp-web.js calls it both ways depending on the version.
    try {
        commands.Cmd.openChatBottom({ chat });
    } catch (e) {
        commands.Cmd.openChatBottom(chat);
    }
});
