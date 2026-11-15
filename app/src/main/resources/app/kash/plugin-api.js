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

// A conversation as KaSH reads it, or null when it is not one (it is then left out).
const conversationOf = (value) => {
    if (value === null || typeof value !== 'object') return null;
    const id = typeof value.id === 'number' ? String(value.id) : value.id;
    if (typeof id !== 'string' || id === '') return null;
    if (!KINDS.includes(value.kind)) return null;
    const count = Math.floor(Number(value.count));
    if (!Number.isFinite(count) || count < 1) return null;
    const time = value.time === undefined || value.time === null ? null : Number(value.time);
    return {
        id,
        name: typeof value.name === 'string' ? value.name : '',
        kind: value.kind,
        count,
        mention: value.mention === true,
        preview: typeof value.preview === 'string' ? value.preview : '',
        time: Number.isFinite(time) ? time : null,
        timePrecision: value.timePrecision === 'day' && Number.isFinite(time) ? 'day' : 'minute',
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
            warn('a conversation without an id, a kind among direct, group and channel, or a count of at least 1 was left out');
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

let openHandler = null;
Object.defineProperty(window, '__kashOpenConversation', {
    value: (id) => {
        if (typeof openHandler !== 'function') return;
        try {
            openHandler(String(id));
        } catch (e) {
            console.error(`[KaSH] ${config.plugin.id}: opening a conversation failed`, e);
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

const kash = Object.freeze({
    apiVersion: config.apiVersion,
    plugin: Object.freeze({ ...config.plugin }),
    options: Object.freeze({ ...config.options }),
    language: config.language,
    unread: Object.freeze({ set, setCount, clear: () => set([]) }),
    setIcon,
    watch,
    onOpenConversation: (handler) => {
        if (typeof handler !== 'function') throw new TypeError('kash.onOpenConversation() takes a function');
        openHandler = handler;
    },
    dom: Object.freeze({ text: (element) => (element ? clean(shown(element)) : '') }),
});

try {
    run(kash);
} catch (e) {
    console.error(`[KaSH] The plugin ${config.plugin.id} failed`, e);
}
