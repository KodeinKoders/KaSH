/// <reference path="../kash-plugin.d.ts" />
// Instagram's plugin (instagram.com, its direct messages): reads the unread conversations of the list of its inbox (`/direct/…`).
//
// Instagram's page is Meta's (Comet: compiled classes, no attribute kept for tests), so it reads the list of conversations, which is there on
// the pages of the direct messages only: each row is a `[role="button"]` whose React component (`IGDThreadListItemCellPressableOffMsys`) has
// the conversation's id (`threadKeyForSelection`, the one of its address, `/direct/t/<id>/`) and, in its parent's `viewerThreadRef`, its kind
// (`thread_subtype`: `IG_ONLY_ONE_TO_ONE`, `IG_BUSINESS_ACCOUNT_ONE_TO_ONE`…). Its texts are its name, the start of its last message, and the
// time of it (`abbr`: "5 min", "2 h", "3 j", "32 sem.", in the page's language). An unread conversation is in bold, with a blue dot: Instagram
// does not count its messages, so it counts 1. Its rows are also the search's contacts (`kash.contacts`). It has no "Mark as read" (a
// conversation's options are Pin, Mute, Delete): a conversation is marked as read by opening it, and going back.
//
// Not seen yet when it was written: a group (a `thread_subtype` with GROUP in it, a guess), a muted conversation, a message request, a long list
// (whether it renders only the rows that fit).

// The React fiber of an element, whose props hold what Instagram knows of a row.
const fiberOf = (element) => {
    const key = Object.keys(element).find((name) => name.startsWith('__reactFiber'));
    return key ? element[key] : null;
};

// The props of the first component above [element] (up to a few levels) that has [name].
const propsWith = (element, name) => {
    let fiber = fiberOf(element);
    for (let depth = 0; fiber && depth < 12; depth++, fiber = fiber.return) {
        const props = fiber.memoizedProps;
        if (props && typeof props === 'object' && props[name] != null) return props;
    }
    return null;
};

// The rows of the list of conversations, with their ids and kinds.
const rows = () => {
    const found = [];
    for (const element of document.querySelectorAll('[role="button"]')) {
        const props = propsWith(element, 'threadKeyForSelection');
        if (!props) continue;
        const thread = propsWith(element, 'viewerThreadRef')?.viewerThreadRef;
        const subtype = String((thread && thread.thread_subtype) || '');
        found.push({ element, id: String(props.threadKeyForSelection), kind: subtype.includes('GROUP') ? 'group' : 'direct' });
    }
    return found;
};

// The texts of a row, in their order: its name, the start of its last message, "·", its time.
const textsOf = (element) => [...element.querySelectorAll('span')]
    .filter((span) => [...span.childNodes].some((node) => node.nodeType === Node.TEXT_NODE && node.textContent.trim()));

// A row in bold is an unread conversation (Instagram also gives it a blue dot, which has nothing to tell it but its color).
const unread = (texts) => texts.length > 0 && Number(getComputedStyle(texts[0]).fontWeight) >= 600;

// The time of the last message, from its label: "5 min" and "2 h" (kash.time.parse), "3 j", "2 d", "32 sem.", "5 w" (to the day). A year is
// too far to say.
const DAY = 24 * 60 * 60 * 1000;
const timeOf = (element) => {
    const label = kash.dom.text(element.querySelector('abbr'));
    if (!label) return null;
    const parsed = kash.time.parse(label);
    if (parsed) return parsed;
    const match = label.toLowerCase().match(/^(\d+) ?(j|d|sem|w)\b/);
    if (!match) return null;
    const days = Number(match[1]) * (match[2] === 'sem' || match[2] === 'w' ? 7 : 1);
    const date = new Date(Date.now() - days * DAY);
    return { time: new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime(), timePrecision: 'day' };
};

kash.watch(() => {
    // The list is only on the pages of the direct messages: elsewhere (the feed, a profile), nothing to say.
    if (!location.pathname.startsWith('/direct/')) return null;
    const all = rows();
    if (all.length === 0) return null;
    const conversations = [];
    for (const { element, id, kind } of all) {
        const texts = textsOf(element);
        if (!unread(texts) || conversations.some((conversation) => conversation.id === id)) continue;
        conversations.push({
            id,
            name: kash.dom.text(texts[0]),
            kind,
            count: 1,
            preview: texts.length > 1 ? kash.dom.text(texts[1]) : '',
            ...(timeOf(element) || {}),
        });
    }
    return conversations;
});

// The conversations of the list, which the search lists: the rows that it rendered.
kash.contacts.watch(() => {
    if (!location.pathname.startsWith('/direct/')) return null;
    const all = rows();
    if (all.length === 0) return null;
    return all.map(({ element, id, kind }) => ({ id, name: kash.dom.text(textsOf(element)[0]), kind }));
});

// Marks a conversation as read by opening it, which Instagram has instead of a "Mark as read": KaSH's API (kash.markReadByOpening) opens it, waits
// for it to be read and goes back to what was open before, one at a time, also across the document that opening it from the list
// (`/direct/inbox/`) loads. KaSH says it in its item, "Mark as read (by opening it)".
const rowOf = (id) => rows().find((candidate) => candidate.id === id);
kash.markReadByOpening({
    open: (id) => {
        const row = rowOf(id);
        if (!row) return false;
        row.element.click();
        return true;
    },
    isUnread: (id) => {
        const row = rowOf(id);
        return row ? unread(textsOf(row.element)) : null;
    },
});

kash.onOpenConversation((id) => {
    // Its row, clicked as the user would: Instagram opens it without loading the page again.
    const row = rows().find((candidate) => candidate.id === id);
    if (row) {
        row.element.click();
        return;
    }
    // A conversation whose row is not there (another page of Instagram than the direct messages): its address.
    if (/^\d+$/.test(id)) location.assign(`/direct/t/${id}/`);
});
