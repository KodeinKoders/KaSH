/// <reference path="../kash-plugin.d.ts" />
// Google Messages' plugin (Messages for web: the SMS and RCS of the Android phone that is paired with it): reads the unread conversations of its
// list.
//
// Messages for web has no model that a page's script can reach (Angular in production: an element's context is a number, not its component), and
// its IndexedDB holds caches, not the conversations. Its list describes each conversation with attributes that it keeps for its own tests
// (`data-e2e-…`), steadier than its classes: whether it is unread, muted, pinned, its name and its snippet. It shows no count: an unread
// conversation counts as 1. A muted one is left out. The list loads its rows as it is scrolled: an unread conversation whose row is not loaded is
// not seen.
//
// It shows no time either, only a label: "Now", "3 min" for a message of today, then the weekday ("Fri") and the date ("Nov 7") for older ones,
// in the language of the page (English in KaSH, whose Chromium is in English). The time is read from it: to the minute today, to the day before.

const rows = () => [...document.querySelectorAll('mws-conversation-list-item a[data-e2e-is-unread]')];

// The conversation's id, the last part of its row's address (`/web/u/0/conversations/<id>`).
const idOf = (row) => {
    const match = (row.getAttribute('href') || '').match(/\/conversations\/([^/?#]+)/);
    return match ? match[1] : null;
};

const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;
const WEEKDAYS = ['sunday', 'monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday'];
const MONTHS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec'];

const startOfDay = (date) => new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
const day = (date) => ({ time: startOfDay(date), timePrecision: 'day' });

// When the last message came, from the row's label, as {time, timePrecision}; null for a label that is not understood.
const timeOf = (row) => {
    const stamp = row.querySelector('mws-relative-timestamp');
    if (!stamp) return null;
    // A weekday has its whole name for screen readers ("Friday"), next to its short one ("Fri").
    const label = kash.dom.text(stamp.querySelector('.weekday-aria-label') || stamp).toLowerCase();
    const now = new Date();
    if (label === 'now' || label === 'just now') return { time: now.getTime(), timePrecision: 'minute' };
    let match = label.match(/^(\d+)\s*min/);
    if (match) return { time: now.getTime() - Number(match[1]) * MINUTE, timePrecision: 'minute' };
    match = label.match(/^(\d+)\s*(h|hr|hrs|hour|hours)\b/);
    if (match) return { time: now.getTime() - Number(match[1]) * HOUR, timePrecision: 'minute' };
    match = label.match(/^(\d{1,2}):(\d{2})\s*(am|pm)?$/);
    if (match) {
        let hours = Number(match[1]) % (match[3] ? 12 : 24);
        if (match[3] === 'pm') hours += 12;
        return { time: new Date(now.getFullYear(), now.getMonth(), now.getDate(), hours, Number(match[2])).getTime(), timePrecision: 'minute' };
    }
    if (label === 'yesterday') return day(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1));
    // A weekday: the last one before today (today's messages have a time).
    const weekday = WEEKDAYS.indexOf(label);
    if (weekday >= 0) {
        const back = (now.getDay() - weekday + 7) % 7 || 7;
        return day(new Date(now.getFullYear(), now.getMonth(), now.getDate() - back));
    }
    // A date: "Nov 7" this year (or last year, when that is still to come this year), "Nov 7, 2024" another year.
    match = label.match(/^([a-z]{3})[a-z]*\.?\s+(\d{1,2})(?:,\s*(\d{4}))?$/);
    if (match && MONTHS.includes(match[1])) {
        const year = match[3] ? Number(match[3]) : now.getFullYear();
        let date = new Date(year, MONTHS.indexOf(match[1]), Number(match[2]));
        if (!match[3] && date.getTime() > now.getTime()) date = new Date(year - 1, date.getMonth(), date.getDate());
        return day(date);
    }
    match = label.match(/^(\d{1,2})\/(\d{1,2})\/(\d{2}|\d{4})$/);
    if (match) {
        const year = Number(match[3]) + (match[3].length === 2 ? 2000 : 0);
        return day(new Date(year, Number(match[1]) - 1, Number(match[2])));
    }
    return null;
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
