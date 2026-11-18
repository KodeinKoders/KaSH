// The API that KaSH gives to the script of a plugin, as `kash` (see PLUGINS.adoc).
//
// The script is the body of a function of `kash`: it runs in the top frame of the pages that the plugin's `matches` give, once per document,
// before the page's own scripts. Reference this file at the top of a script for the completion of an editor:
//     /// <reference path="../kash-plugin.d.ts" />

declare namespace KashPlugin {
    /** What a conversation is in its service. */
    type ConversationKind = 'direct' | 'group' | 'channel';

    /** A conversation with unread messages. */
    interface Conversation {
        /** The service's own id: given back as is to onOpenConversation. Not empty, unique in the state. */
        id: string;
        /** The name to show; '' when the service gives none (KaSH then shows "Unnamed group"…). */
        name: string;
        kind: ConversationKind;
        /** Unread messages, at least 1 (a conversation marked as unread by hand: 1). Not given, and ignored, for a topic. */
        count?: number;
        /** Whether one of the unread messages mentions the user. false by default; ignored for a topic. */
        mention?: boolean;
        /**
         * Whether the service shows new messages in it without counting them (a channel in bold, without a badge, and no mention): KaSH lists it
         * in the Topics section of its Unread tab, without a count, and no badge counts it. false by default.
         */
        topic?: boolean;
        /** The start of the last message, as the service shows it. '' by default. */
        preview?: string;
        /** When the last message came, in milliseconds since the epoch. null by default (not known). */
        time?: number | null;
        /**
         * How precise time is: 'minute' by default; 'day' for a service that only shows the day of an older message, time being the start of
         * that day (KaSH then shows the day without a time).
         */
        timePrecision?: 'minute' | 'day';
    }

    /**
     * A contact or a topic that the service gives access to (a person, a group, a channel, a space, a server), which KaSH's search lists by its
     * name and opens with onOpenConversation(id): a direct one is a contact, the others are topics.
     */
    interface Contact {
        /** The same id as its conversation's (Conversation.id), which onOpenConversation is given. */
        id: string | number;
        /** What the search finds it by: an entry without one is left out. */
        name: string;
        kind: ConversationKind;
    }

    interface ContactsWatchOptions {
        /** How often the page is read, in milliseconds; 10000 by default, 1000 at least. */
        interval?: number;
        /** Whether read() gives every contact (contacts.set), or what the page shows (contacts.add, the default). */
        complete?: boolean;
    }

    interface MarkReadOptions {
        /**
         * The service has no "Mark as read", and the handler opens the conversation, waits for it to be read, and goes back to what was open
         * before: KaSH says so in its item, "Mark as read (by opening it)".
         */
        byOpening?: boolean;
    }

    interface WatchOptions {
        /** How often the page is read, in milliseconds; 2000 by default, 500 at least. */
        interval?: number;
    }

    interface Kash {
        /** 3 since onMarkRead, 2 since contacts; 1 before. */
        readonly apiVersion: number;
        readonly plugin: { readonly id: string; readonly version: string };
        /** The values of the service's options: strings for text and choice, booleans for boolean. */
        readonly options: Readonly<Record<string, string | boolean>>;
        /** The language of KaSH's interface: 'en', 'fr'. */
        readonly language: string;

        readonly unread: {
            /**
             * Replaces the whole state of the unread conversations. A state equal to the last one is not sent again.
             * Invalid entries are left out, with a warning in the console.
             */
            set(conversations: readonly Conversation[]): void;
            /** For a service that does not list its conversations: only the total. */
            setCount(count: number): void;
            /** Nothing is unread any more (the same as set([])). */
            clear(): void;
        };

        /** The contacts and topics of the service, which KaSH's search lists (since apiVersion 2). The same list is not sent again. */
        readonly contacts: {
            /** Every contact of the service: KaSH replaces what it knew, and forgets those that are not there any more. */
            set(contacts: readonly Contact[]): void;
            /**
             * What the page shows of them (a virtual list, the rows it loaded): KaSH adds them to those it saw before, from a launch to the next,
             * and takes their new names; it forgets none.
             */
            add(contacts: readonly Contact[]): void;
            /**
             * Calls read() every interval ms (and once 3 s after it starts) and sends what it returns with set or add. null or undefined: nothing
             * is sent. An exception is caught. Returns a function that stops reading.
             */
            watch(read: () => readonly Contact[] | null | undefined, options?: ContactsWatchOptions): () => void;
        };

        /**
         * The image of the service (an account's or a workspace's own), which its tile shows in place of the plugin's icon or the site's (not
         * of an image or a square that the user chose): an https URL, which KaSH downloads without the service's cookies, or the data: URL of
         * an image (PNG, JPEG, GIF, WebP, ICO; not SVG), of 1 MB at most. null: the service has none, and its tile shows the plugin's icon or
         * the site's. KaSH keeps it from a launch to the next. The same source is not sent again.
         */
        setIcon(source: string | null): void;

        /**
         * Calls read() every interval ms and sends what it returns with unread.set.
         * null or undefined means that the page is not ready: nothing is sent.
         * An exception is caught (the page changed its structure): nothing is sent, and reading goes on.
         * Returns a function that stops reading.
         */
        watch(read: () => readonly Conversation[] | null | undefined, options?: WatchOptions): () => void;

        /**
         * Called when the user marks the conversation id as read from KaSH (the Unread tab, a notification), without showing the service
         * (since apiVersion 3). KaSH offers it only for a service whose plugin calls onMarkRead. The handler marks it as the user would (the
         * service's model, the item of a row's menu); it may be async.
         */
        onMarkRead(handler: (id: string) => void | Promise<void>, options?: MarkReadOptions): void;

        /**
         * Called when the user opens the conversation id from the Unread tab or from the search (KaSH already shows the service): it may not be
         * unread, nor shown by the page (a row of a virtual list that is not rendered).
         */
        onOpenConversation(handler: (id: string) => void): void;

        readonly dom: {
            /**
             * The text that an element shows: its texts and the alt of its images (emojis), without the marks of direction, with one space
             * between words. '' for no element.
             */
            text(element: Element | null | undefined): string;
        };

        readonly time: {
            /**
             * The time of a label that a service shows instead of it, in the language of its page (or language: 'fr', 'en-US'): "Now", "3 min",
             * "10:42 AM", "10 h 42" give it to the minute, today; "Yesterday", a weekday ("Friday", "ven."), a date ("Nov 7", "7 nov.",
             * "Nov 7, 2024", "11/7/24", "19/09/2025") give the start of the day, with timePrecision 'day'. A date without its year is the last
             * one that has passed. null for a label that is not one of those. The words are the browser's for that language (Intl).
             */
            parse(label: string | null | undefined, language?: string): { time: number; timePrecision: 'minute' | 'day' } | null;
        };
    }
}

/** KaSH's API, given to a plugin's script. */
declare const kash: KashPlugin.Kash;
