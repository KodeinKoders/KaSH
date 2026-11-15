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
        /** Unread messages, at least 1 (a conversation marked as unread by hand: 1). */
        count: number;
        /** Whether one of the unread messages mentions the user. false by default. */
        mention?: boolean;
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

    interface WatchOptions {
        /** How often the page is read, in milliseconds; 2000 by default, 500 at least. */
        interval?: number;
    }

    interface Kash {
        readonly apiVersion: 1;
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

        /** Called when the user opens the conversation id from the Unread tab (KaSH already shows the service). */
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
