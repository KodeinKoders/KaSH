// Injected by KaSH in the WhatsApp services only, in every document, before the page's own scripts (see notification-shim.js).
//
// Reads the unread conversations from WhatsApp Web's live model (`window.require('WAWebCollections').Chat`, the module system of the page, which
// whatsapp-web.js uses too), and sends them to KaSH through `window.cefQuery` when they change:
//     {type: "unread", conversations: [{id, name, kind: "direct" | "group" | "channel", count, mention, preview, time}]}
// Muted conversations (until a date, forever, or auto-muted by WhatsApp) and archived ones are left out, as WhatsApp leaves them out of its own
// count. A conversation marked as unread by hand (its count is -1) counts as 1. A group that was never named is named by its members, without
// the user, as WhatsApp shows it. The preview is the start of the last message as WhatsApp shows it in the row of the conversation (with the
// sender in a group), read from the rows that are rendered and kept while the row is scrolled out; no other message is read.
//
// KaSH calls `__kashOpenConversation(id)` to open a conversation, when its row of the Unread tab is clicked.
(() => {
    if (window.top !== window || location.hostname !== 'web.whatsapp.com' || window.__kashOpenConversation) return;

    // How often the model is read: WhatsApp's events have changed names across versions, a read of a few hundred chats has not.
    const PERIOD_MS = 2000;

    const send = (message) => {
        try {
            if (typeof window.cefQuery === 'function') {
                window.cefQuery({ request: JSON.stringify(message), persistent: false, onSuccess() {}, onFailure() {} });
            }
        } catch (e) {
            // KaSH misses this update; the next one carries the same state.
        }
    };

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

    // Without the marks of direction (U+202A…U+202E, U+2066…U+2069) that WhatsApp puts around names and previews.
    const clean = (text) => (text || '').replace(/[‪-‮⁦-⁩]/g, '').trim();

    // What an element shows as text: its text, and its emojis, which WhatsApp draws as images whose `alt` is the emoji.
    const shown = (element) => {
        let text = '';
        for (const node of element.childNodes) {
            if (node.nodeType === Node.TEXT_NODE) text += node.textContent;
            else if (node.nodeName === 'IMG') text += node.getAttribute('alt') || '';
            else if (node.nodeType === Node.ELEMENT_NODE) text += shown(node);
        }
        return text;
    };

    // The previews of the last messages, as WhatsApp shows them in its rows (its own wording for calls, photos, group events…, and the sender
    // in a group), by the name that the row shows: only the rows that are rendered (the list is virtualized) have one.
    const renderedPreviews = () => {
        const previews = new Map();
        for (const row of document.querySelectorAll('#pane-side [role="row"]')) {
            const title = row.querySelector('[data-testid="cell-frame-title"] [title]');
            const preview = row.querySelector('[data-testid="last-msg-status"]');
            if (title && preview) previews.set(clean(title.getAttribute('title')), clean(shown(preview)).replace(/\s+/g, ' '));
        }
        return previews;
    };

    // The last preview of each unread conversation, kept while its row is not rendered (scrolled out of the list).
    const previewOf = new Map();

    let last = null;

    const read = () => {
        const collections = module('WAWebCollections');
        if (!collections || !collections.Chat) return;
        const myIds = me();
        const rendered = renderedPreviews();
        const conversations = collections.Chat.getModelsArray()
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
        const json = JSON.stringify(conversations);
        if (json === last) return;
        last = json;
        send({ type: 'unread', conversations });
    };

    setInterval(() => {
        try {
            read();
        } catch (e) {
            // A version of WhatsApp that changed its model: KaSH shows nothing rather than break the page.
        }
    }, PERIOD_MS);

    Object.defineProperty(window, '__kashOpenConversation', {
        value: (id) => {
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
        },
    });
})();
