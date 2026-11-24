// Injected by KaSH in every document of every service, before the page's own scripts (Page.addScriptToEvaluateOnNewDocument).
//
// Chromium embedded in KaSH does not show the notifications of a page (the Web Notification API) by itself: this replaces `Notification` (and
// the service worker's `showNotification`) by an implementation that sends them to KaSH through `window.cefQuery`, which shows them on the
// desktop. The permission is always granted: KaSH decides whether a notification is shown. When the user clicks it, KaSH calls
// `__kashNotificationClick(id)`, which fires the notification's `click` event, as a browser does (a messaging service opens the conversation).
// When the page closes a notification (`close()`), KaSH is told (`notification-close`), with how long after it was shown.
// It is the same for every service: it does not read anything of the page.
(() => {
    if (window.__kashNotificationClick) return;

    const send = (message) => {
        try {
            if (typeof window.cefQuery === 'function') {
                window.cefQuery({ request: JSON.stringify(message), persistent: false, onSuccess() {}, onFailure() {} });
            }
        } catch (e) {
            // A notification that cannot be sent is lost: the page must not break for it.
        }
    };

    const shown = new Map();
    let next = 0;

    const fire = (target, type) => {
        const event = new Event(type, { cancelable: type === 'click' });
        target.dispatchEvent(event);
        const handler = target['on' + type];
        if (typeof handler === 'function') handler.call(target, event);
    };

    class KashNotification extends EventTarget {
        constructor(title, options = {}) {
            super();
            this.title = String(title);
            this.body = options.body ? String(options.body) : '';
            this.tag = options.tag ? String(options.tag) : '';
            this.icon = options.icon ? String(options.icon) : '';
            this.data = options.data;
            this.silent = !!options.silent;
            this.onclick = null;
            this.onclose = null;
            this.onerror = null;
            this.onshow = null;
            this.__kashId = 'n' + (++next);
            this.__kashShownAt = Date.now();
            shown.set(this.__kashId, this);
            send({ type: 'notification', id: this.__kashId, title: this.title, body: this.body, tag: this.tag, silent: this.silent });
            setTimeout(() => fire(this, 'show'), 0);
        }

        // The page closes it (what it says was read, here or elsewhere; or its time is up): KaSH is told, as Chrome would take it away.
        close() {
            if (!shown.delete(this.__kashId)) return;
            send({ type: 'notification-close', id: this.__kashId, after: Date.now() - this.__kashShownAt });
            fire(this, 'close');
        }

        static get permission() {
            return 'granted';
        }

        static requestPermission(callback) {
            if (typeof callback === 'function') callback('granted');
            return Promise.resolve('granted');
        }

        static get maxActions() {
            return 0;
        }
    }

    Object.defineProperty(window, 'Notification', { value: KashNotification, writable: true, configurable: true });

    Object.defineProperty(window, '__kashNotificationClick', {
        value: (id) => {
            const notification = shown.get(id);
            if (notification) fire(notification, 'click');
        },
    });

    if (navigator.permissions && navigator.permissions.query) {
        const query = navigator.permissions.query.bind(navigator.permissions);
        navigator.permissions.query = (descriptor) => descriptor && descriptor.name === 'notifications'
            ? Promise.resolve({ name: 'notifications', state: 'granted', onchange: null, addEventListener() {}, removeEventListener() {} })
            : query(descriptor);
    }

    if (window.ServiceWorkerRegistration) {
        ServiceWorkerRegistration.prototype.showNotification = function (title, options) {
            new KashNotification(title, options || {});
            return Promise.resolve();
        };
    }
})();
