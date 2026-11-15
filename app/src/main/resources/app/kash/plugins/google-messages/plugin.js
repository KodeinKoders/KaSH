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
// in the language of the page, which is KaSH's ("Maintenant", "ven.", "7 nov."). The time is read from it, with the words of that language that
// the browser has (Intl): to the minute today, to the day before.

const rows = () => [...document.querySelectorAll('mws-conversation-list-item a[data-e2e-is-unread]')];

// The conversation's id, the last part of its row's address (`/web/u/0/conversations/<id>`).
const idOf = (row) => {
    const match = (row.getAttribute('href') || '').match(/\/conversations\/([^/?#]+)/);
    return match ? match[1] : null;
};

const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;

// A label's words, to compare them with a language's: lowercased, without accents, dots and repeated spaces ("Févr." is "fevr").
const normalize = (text) => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/\./g, '').replace(/\s+/g, ' ').trim();

// The words of the page's language that its labels are made of, from the browser's own (Intl), which Messages' are too: the weekdays from
// Sunday and the months, long and short, "now" and "yesterday", and whether a date starts with its day.
const wordsCache = new Map();
const wordsOf = (language) => {
    if (!wordsCache.has(language)) {
        const names = (count, date, option) => Array.from({ length: count }, (_, i) =>
            ['long', 'short'].map((width) => normalize(new Intl.DateTimeFormat(language, { [option]: width }).format(date(i)))));
        const relative = new Intl.RelativeTimeFormat(language, { numeric: 'auto' });
        const first = new Intl.DateTimeFormat(language).formatToParts(new Date(2024, 10, 7)).find((part) => part.type === 'day' || part.type === 'month');
        wordsCache.set(language, {
            // 7 January 2024 was a Sunday.
            weekdays: names(7, (i) => new Date(2024, 0, 7 + i), 'weekday'),
            months: names(12, (i) => new Date(2024, i, 15), 'month'),
            now: [normalize(relative.format(0, 'second')), 'now', 'just now'],
            yesterday: [normalize(relative.format(-1, 'day')), 'yesterday'],
            dayFirst: first ? first.type === 'day' : false,
        });
    }
    return wordsCache.get(language);
};

// The month that a word names (its long name, its short one, or the start of its long one: "sept" and "sep"), or -1.
const monthOf = (word, months) => months.findIndex(([long, short]) => word === long || word === short || (word.length >= 3 && long.startsWith(word)));

const startOfDay = (date) => new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
const day = (date) => ({ time: startOfDay(date), timePrecision: 'day' });

// A day of a year: the year before when it is not given and would be to come.
const dateOf = (year, month, dayOfMonth, now) => {
    if (month < 0 || month > 11 || dayOfMonth < 1 || dayOfMonth > 31) return null;
    let date = new Date(year ?? now.getFullYear(), month, dayOfMonth);
    if (year == null && date.getTime() > now.getTime()) date = new Date(date.getFullYear() - 1, month, dayOfMonth);
    return day(date);
};

// When the last message came, from the row's label, as {time, timePrecision}; null for a label that is not understood.
const timeOf = (row) => {
    const stamp = row.querySelector('mws-relative-timestamp');
    if (!stamp) return null;
    // A weekday has its whole name for screen readers ("Friday"), next to its short one ("Fri").
    const label = normalize(kash.dom.text(stamp.querySelector('.weekday-aria-label') || stamp));
    const words = wordsOf(document.documentElement.lang || navigator.language || 'en');
    const now = new Date();
    if (words.now.includes(label)) return { time: now.getTime(), timePrecision: 'minute' };
    let match = label.match(/^(\d+) ?min/);
    if (match) return { time: now.getTime() - Number(match[1]) * MINUTE, timePrecision: 'minute' };
    // A time of today: "10:42 am", "10:42", "10 h 42".
    match = label.match(/^(\d{1,2}) ?(?::|h) ?(\d{2}) ?(am|pm)?$/);
    if (match) {
        let hours = Number(match[1]) % (match[3] ? 12 : 24);
        if (match[3] === 'pm') hours += 12;
        return { time: new Date(now.getFullYear(), now.getMonth(), now.getDate(), hours, Number(match[2])).getTime(), timePrecision: 'minute' };
    }
    match = label.match(/^(\d+) ?(h|hr|hrs|hour|hours|heure|heures)$/);
    if (match) return { time: now.getTime() - Number(match[1]) * HOUR, timePrecision: 'minute' };
    if (words.yesterday.includes(label)) return day(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1));
    // A weekday: the last one before today (today's messages have a time).
    const weekday = words.weekdays.findIndex((names) => names.includes(label));
    if (weekday >= 0) {
        const back = (now.getDay() - weekday + 7) % 7 || 7;
        return day(new Date(now.getFullYear(), now.getMonth(), now.getDate() - back));
    }
    // A date with its month's name, in the language's order: "Nov 7", "7 nov", "Nov 7, 2024", "7 nov 2024".
    const tokens = label.split(/[ ,]+/).filter(Boolean);
    const month = tokens.map((token) => monthOf(token, words.months)).find((index) => index >= 0);
    if (month !== undefined && tokens.length <= 3) {
        const numbers = tokens.filter((token) => /^\d+$/.test(token)).map(Number);
        const year = numbers.find((number) => number > 31);
        const dayOfMonth = numbers.find((number) => number <= 31);
        if (dayOfMonth !== undefined) return dateOf(year, month, dayOfMonth, now);
    }
    // A date in digits, in the language's order: "11/7/24", "07/11/2024".
    match = label.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2}|\d{4})$/);
    if (match) {
        const year = Number(match[3]) + (match[3].length === 2 ? 2000 : 0);
        const [dayOfMonth, monthNumber] = words.dayFirst ? [match[1], match[2]] : [match[2], match[1]];
        return dateOf(year, Number(monthNumber) - 1, Number(dayOfMonth), now);
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
