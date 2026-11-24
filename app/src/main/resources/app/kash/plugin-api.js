// The API of KaSH's plugins (see plugins/kash-plugin.d.ts and PLUGINS.adoc), injected in every document of a plugin's service, before the page's
// own scripts, with the plugin's script.
//
// KaSH injects this file as the body of a function of two arguments (PluginScripts.kt):
// - `config`: {apiVersion, plugin: {id, version}, options, language, matches}, the plugin and the service's values;
// - `run`: the plugin's script, as the body of a function of `kash`.
// The script only runs in the top frame of a page whose address matches the plugin's `matches`, once per document. What it gives is checked
// here, so that a plugin cannot send KaSH what it does not read; an exception of the script is caught, so that the page does not break.

if (window.top !== window || window.__kashOpenConversation) return;

// `*` matches any run of characters; the rest is literal.
const pattern = (match) => new RegExp('^' + match.split('*').map((part) => part.replace(/[.+?^${}()|[\]\\]/g, '\\$&')).join('.*') + '$');
if (!config.matches.some((match) => pattern(match).test(location.href))) return;

const send = (message) => {
    try {
        if (typeof window.cefQuery === 'function') {
            window.cefQuery({ request: JSON.stringify(message), persistent: false, onSuccess() {}, onFailure() {} });
        }
    } catch (e) {
        // KaSH misses this update; the next one carries the same state.
    }
};

// Each warning once: a plugin reads its page every few seconds, and the same mistake would fill the console.
const warned = new Set();
const warn = (message) => {
    if (warned.has(message)) return;
    warned.add(message);
    console.warn(`[KaSH] ${config.plugin.id}: ${message}`);
};

const KINDS = ['direct', 'group', 'channel'];

// A conversation as KaSH reads it, or null when it is not one (it is then left out). A topic (new messages that the service does not count: a
// channel in bold, without a badge) has no count and no mention.
const conversationOf = (value) => {
    if (value === null || typeof value !== 'object') return null;
    const id = typeof value.id === 'number' ? String(value.id) : value.id;
    if (typeof id !== 'string' || id === '') return null;
    if (!KINDS.includes(value.kind)) return null;
    const topic = value.topic === true;
    const count = topic ? 0 : Math.floor(Number(value.count));
    if (!topic && (!Number.isFinite(count) || count < 1)) return null;
    const time = value.time === undefined || value.time === null ? null : Number(value.time);
    return {
        id,
        name: typeof value.name === 'string' ? value.name : '',
        kind: value.kind,
        count,
        mention: !topic && value.mention === true,
        preview: typeof value.preview === 'string' ? value.preview : '',
        // In whole milliseconds: KaSH reads an integer, and a time with a fraction (seconds * 1000) was lost.
        time: Number.isFinite(time) ? Math.round(time) : null,
        timePrecision: value.timePrecision === 'day' && Number.isFinite(time) ? 'day' : 'minute',
        topic,
    };
};

// What was sent last: the same state is not sent again.
let last = null;
const sendIfChanged = (message) => {
    const json = JSON.stringify(message);
    if (json === last) return;
    last = json;
    send(message);
};

const set = (conversations) => {
    if (!Array.isArray(conversations)) {
        warn('unread.set() takes an array of conversations');
        return;
    }
    const ids = new Set();
    const valid = [];
    for (const value of conversations) {
        const conversation = conversationOf(value);
        if (conversation === null) {
            warn('a conversation without an id, a kind among direct, group and channel, or a count of at least 1 (or topic: true) was left out');
            continue;
        }
        if (ids.has(conversation.id)) {
            warn(`the conversation ${conversation.id} was given twice: the second one was left out`);
            continue;
        }
        ids.add(conversation.id);
        valid.push(conversation);
    }
    sendIfChanged({ type: 'unread', conversations: valid });
};

const setCount = (count) => {
    const total = Math.floor(Number(count));
    if (!Number.isFinite(total) || total < 0) {
        warn('unread.setCount() takes a number of at least 0');
        return;
    }
    sendIfChanged({ type: 'unreadCount', count: total });
};

// The service's icon: an https URL, which KaSH downloads, or the data: URL of an image (not SVG: KaSH cannot draw it). KaSH checks it again.
const ICON = /^(https:\/\/[^/?#\s]+|data:image\/(png|jpeg|gif|webp|x-icon|vnd\.microsoft\.icon);base64,)/i;
let lastIcon;
const setIcon = (source) => {
    if (source !== null && (typeof source !== 'string' || !ICON.test(source))) {
        warn('setIcon() takes an https URL, the data: URL of an image (PNG, JPEG, GIF, WebP, ICO), or null');
        return;
    }
    if (source === lastIcon) return;
    lastIcon = source;
    send({ type: 'icon', source });
};

const watch = (read, options) => {
    if (typeof read !== 'function') throw new TypeError('kash.watch() takes a function');
    const interval = Math.max(500, Number(options && options.interval) || 2000);
    const timer = setInterval(() => {
        let conversations;
        try {
            conversations = read();
        } catch (e) {
            // A version of the service that changed its page: KaSH shows nothing new rather than break the page, and reading goes on.
            return;
        }
        if (conversations !== null && conversations !== undefined) set(conversations);
    }, interval);
    return () => clearInterval(timer);
};

// A contact or a topic that the service gives access to (a person, a group, a channel, a space, a server), as KaSH reads it, or null when it is
// not one: the search lists them, by their names.
const contactOf = (value) => {
    if (value === null || typeof value !== 'object') return null;
    const id = typeof value.id === 'number' ? String(value.id) : value.id;
    if (typeof id !== 'string' || id === '') return null;
    if (!KINDS.includes(value.kind)) return null;
    const name = typeof value.name === 'string' ? clean(value.name) : '';
    if (name === '') return null;
    return { id, name, kind: value.kind };
};

// What was sent last of the contacts, apart from the unread conversations: the two alternate, and each one would be sent again every time.
let lastContacts = null;
const sendContacts = (contacts, complete) => {
    if (!Array.isArray(contacts)) {
        warn(`contacts.${complete ? 'set' : 'add'}() takes an array of contacts`);
        return;
    }
    const ids = new Set();
    const valid = [];
    for (const value of contacts) {
        const contact = contactOf(value);
        if (contact === null) {
            warn('a contact without an id, a name, or a kind among direct, group and channel was left out');
            continue;
        }
        if (ids.has(contact.id)) continue;
        ids.add(contact.id);
        valid.push(contact);
    }
    const message = { type: 'contacts', complete, contacts: valid };
    const json = JSON.stringify(message);
    if (json === lastContacts) return;
    lastContacts = json;
    send(message);
};

const watchContacts = (read, options) => {
    if (typeof read !== 'function') throw new TypeError('kash.contacts.watch() takes a function');
    const interval = Math.max(1000, Number(options && options.interval) || 10000);
    const complete = !!(options && options.complete);
    const tick = () => {
        let contacts;
        try {
            contacts = read();
        } catch (e) {
            return;
        }
        if (contacts !== null && contacts !== undefined) sendContacts(contacts, complete);
    };
    // Once the page had time to show its lists, then every interval.
    const first = setTimeout(tick, Math.min(interval, 3000));
    const timer = setInterval(tick, interval);
    return () => {
        clearTimeout(first);
        clearInterval(timer);
    };
};

// Marking a conversation as read, from KaSH (its Unread tab, a notification), when the plugin can: KaSH offers it only once the plugin said so.
// KaSH takes the conversation out of its unread ones at once, and the next state says whether it was read: once the handler is done, that state
// is sent even when it is the same as the last one (a handler that could do nothing: no such row, no such item), which puts the conversation
// back. While the handler runs (marking by opening takes seconds), nothing changes.
let markReadHandler = null;
Object.defineProperty(window, '__kashMarkRead', {
    value: (id) => {
        if (typeof markReadHandler !== 'function') return;
        const failed = (e) => console.error(`[KaSH] ${config.plugin.id}: marking a conversation as read failed`, e);
        const done = () => { last = null; };
        try {
            const running = markReadHandler(String(id));
            if (running && typeof running.then === 'function') Promise.resolve(running).catch(failed).finally(done);
            else done();
        } catch (e) {
            failed(e);
            done();
        }
    },
});

// Marking a conversation as read by opening it (kash.markReadByOpening), for a service that has no other way (Instagram, Google Messages): its
// conversation is opened (the plugin's `open`), and once the plugin says it is read (`isUnread`), the page goes back to what was open before
// (history.back()), unless the user went elsewhere meanwhile. One at a time: two that come together would cross. Opening a conversation may load a
// document of its own (Instagram's, from its list), which ended the script: what is being marked as read is kept in the tab's sessionStorage, and
// the document that opening it loads finishes it (this script runs again in it), first in the queue.
const BY_OPENING = 'kash.markReadByOpening';
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const here = () => location.pathname + location.search;
let byOpening = null;
let markingByOpening = Promise.resolve();

// Waits for the conversation being marked as read to be read (6 s at most), then goes back to what was open before, unless the user went elsewhere
// meanwhile; nothing when there is none, or it is old (a document loaded long after).
const finishMarkingByOpening = async () => {
    let pending = null;
    try {
        pending = JSON.parse(sessionStorage.getItem(BY_OPENING) || 'null');
    } catch (e) {
        pending = null;
    }
    if (!pending || Date.now() - pending.at > 30000) {
        sessionStorage.removeItem(BY_OPENING);
        return;
    }
    let opened = null;
    for (let tries = 0; tries < 30; tries++) {
        await wait(200);
        if (opened === null && here() !== pending.before) opened = here();
        if (opened !== null && byOpening.isUnread(pending.id) === false) break;
    }
    sessionStorage.removeItem(BY_OPENING);
    if (opened !== null && here() === opened) history.back();
};

const markByOpening = async (id) => {
    if (byOpening.isUnread(id) !== true) return;
    sessionStorage.setItem(BY_OPENING, JSON.stringify({ id, before: here(), at: Date.now() }));
    if (!byOpening.open(id)) {
        sessionStorage.removeItem(BY_OPENING);
        return;
    }
    await finishMarkingByOpening();
};

const markReadByOpening = (handlers) => {
    if (!handlers || typeof handlers.open !== 'function' || typeof handlers.isUnread !== 'function') {
        throw new TypeError('kash.markReadByOpening() takes { open(id), isUnread(id) }');
    }
    byOpening = handlers;
    // A document that opening a conversation loaded finishes marking it as read.
    markingByOpening = finishMarkingByOpening().catch(() => {});
    kash.onMarkRead((id) => {
        markingByOpening = markingByOpening.then(() => markByOpening(id)).catch(() => {});
        return markingByOpening;
    }, { byOpening: true });
};

let openHandler = null;
Object.defineProperty(window, '__kashOpenConversation', {
    value: (id) => {
        if (typeof openHandler !== 'function') return;
        const failed = (e) => console.error(`[KaSH] ${config.plugin.id}: opening a conversation failed`, e);
        try {
            const opening = openHandler(String(id));
            // An asynchronous handler's rejection too.
            if (opening && typeof opening.then === 'function') Promise.resolve(opening).catch(failed);
        } catch (e) {
            failed(e);
        }
    },
});

// Without the marks of direction (U+202A…U+202E, U+2066…U+2069) that services put around names and previews, with one space between words.
const clean = (text) => text.replace(/[\u202A-\u202E\u2066-\u2069]/g, '').replace(/\s+/g, ' ').trim();

// What an element shows as text: its texts, and its emojis, which services draw as images whose `alt` is the emoji.
const shown = (element) => {
    let text = '';
    for (const node of element.childNodes) {
        if (node.nodeType === Node.TEXT_NODE) text += node.textContent;
        else if (node.nodeName === 'IMG') text += node.getAttribute('alt') || '';
        else if (node.nodeType === Node.ELEMENT_NODE) text += shown(node);
    }
    return text;
};

// The time of a label that a service shows instead of it, in the language of its page: "Now", "3 min", "10:42 AM", "10 h 42" (today, to the
// minute), "Yesterday", a weekday, "Nov 7", "7 nov.", "Nov 7, 2024", "11/7/24", "07/11/2024" (to the day). The words are the browser's own for
// that language (Intl), which the services' are too.
const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;

// A label's words, to compare them with a language's: lowercased, without accents, dots and repeated spaces ("Févr." is "fevr").
const normalize = (text) => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/\./g, '').replace(/\s+/g, ' ').trim();

// The words of a language that labels are made of: the weekdays from Sunday and the months, long and short, "now" and "yesterday", and whether
// a date starts with its day.
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

const parseTime = (text, language) => {
    if (typeof text !== 'string') return null;
    const label = normalize(text);
    if (!label) return null;
    let words;
    try {
        words = wordsOf(language || window.document.documentElement.lang || window.navigator.language || 'en');
    } catch (e) {
        // A language that Intl does not know.
        words = wordsOf('en');
    }
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
    // A date with its month's name, in any order: "Nov 7", "7 nov", "Nov 7, 2024", "7 nov 2024".
    const tokens = label.split(/[ ,]+/).filter(Boolean);
    const month = tokens.map((token) => monthOf(token, words.months)).find((index) => index >= 0);
    if (month !== undefined && tokens.length <= 3) {
        const numbers = tokens.filter((token) => /^\d+$/.test(token)).map(Number);
        const year = numbers.find((number) => number > 31);
        const dayOfMonth = numbers.find((number) => number <= 31);
        if (dayOfMonth !== undefined) return dateOf(year, month, dayOfMonth, now);
    }
    // A date in digits, in the language's order: "11/7/24", "07/11/2024", "19.09.2025". Read on the label as it is: its normalized form has no dots.
    match = text.trim().match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2}|\d{4})\.?$/);
    if (match) {
        const year = Number(match[3]) + (match[3].length === 2 ? 2000 : 0);
        const [dayOfMonth, monthNumber] = words.dayFirst ? [match[1], match[2]] : [match[2], match[1]];
        return dateOf(year, Number(monthNumber) - 1, Number(dayOfMonth), now);
    }
    return null;
};

const kash = Object.freeze({
    apiVersion: config.apiVersion,
    plugin: Object.freeze({ ...config.plugin }),
    options: Object.freeze({ ...config.options }),
    language: config.language,
    unread: Object.freeze({ set, setCount, clear: () => set([]) }),
    contacts: Object.freeze({
        set: (contacts) => sendContacts(contacts, true),
        add: (contacts) => sendContacts(contacts, false),
        watch: watchContacts,
    }),
    setIcon,
    watch,
    onOpenConversation: (handler) => {
        if (typeof handler !== 'function') throw new TypeError('kash.onOpenConversation() takes a function');
        openHandler = handler;
    },
    // options.byOpening: the service has no other way than opening the conversation (KaSH says so in its item, "Mark as read (by opening
    // it)"); the handler opens it, waits for it to be read, and goes back to what was open before.
    onMarkRead: (handler, options) => {
        if (typeof handler !== 'function') throw new TypeError('kash.onMarkRead() takes a function');
        // KaSH offers to mark the service's conversations as read from then on, in this document.
        if (markReadHandler === null) send({ type: 'capabilities', markRead: true, byOpening: !!(options && options.byOpening) });
        markReadHandler = handler;
    },
    markReadByOpening: (handlers) => markReadByOpening(handlers),
    dom: Object.freeze({ text: (element) => (element ? clean(shown(element)) : '') }),
    time: Object.freeze({ parse: parseTime }),
});

try {
    run(kash);
} catch (e) {
    console.error(`[KaSH] The plugin ${config.plugin.id} failed`, e);
}
