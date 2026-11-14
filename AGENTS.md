# AGENTS.md

Guidance for AI agents working on KaSH, a messaging aggregator in the manner of Ferdium, written in Kotlin with Compose for Desktop and Jewel.
`README.adoc` describes the project for humans, this file is about how to work on it.

## What it is

KaSH shows the web versions of messaging services (WhatsApp, and any web application as a custom service), each in a tab of a rail of icons with an unread badge, and a first tab, **Non lus** (Unread), that lists the conversations with unread messages of every service.
The design is the canvas https://claude.ai/artifact/PzwGyuaLy2yYJ7nkPfZk6q (artboards `Main`, `AddService`, `Settings`).

Each service is a Chromium profile of its own on disk (cookies, cache, IndexedDB, local storage, service workers), which nothing else shares and which is there again at the next launch.
**Persistence across restarts is the requirement that decides everything else**: never replace a service's profile by an in-memory one (an empty `cache_path`, an incognito context), even temporarily.
Chromium is JetBrains' fork of JCEF, built from the `jcef` submodule. It only runs on macOS for now.

**State of the services**: the catalog has WhatsApp and Google Chat, read by `services/whatsapp.js` and `services/google-chat.js` (see **Scripts in the pages**). A custom application tells its total by its title (`(3) Name`). Adding a service to the catalog means inspecting its page once the user is logged in (see **Inspecting the services**) and writing its script.
The automatic updates of the design (Settings › General › Updates) are not done.

## Modules

`app` → `browser` → `jcef-native` → `jcef/` (the JetBrains/jcef git submodule; not a Gradle module). `app` also uses `app-core` and `macos`.

| Module | Package | Role |
| --- | --- | --- |
| `app` | `app.kash` | The application: the main window (`MainWindow.kt`: title bar, `ServiceRail`, `InboxScreen` or a service's page; the selected service's header, `ServiceTitle` and `ServiceButtons` of `ServiceScreen.kt`, is in the title bar, so that its page has the whole height), `AddServiceWindow` (also Edit), `SettingsWindow`, `ConfirmDialog`, `ToastWindow`. `KashModel` holds the state and the loaded services, `Notifier` the notifications, `ServiceIcons` the favicons and images. `ui/` has the theme (`KashTheme`, `KashColors`: the design's colors), the icons (`KashIcons`: the design's SVGs) and the strings (`Strings`: French and English). |
| `app-core` | `app.kash.core` | What is kept and decided, with no Compose and no JCEF, unit-tested: `Model.kt` (`Service`, `Settings`, `KashState`, `ProfileId`), `StateFile`, `WindowGeometry` (`WindowFile`), `KashDirectories`, `ServiceCatalog`, `Inbox` (the rows, sorts, filter and badges of the Unread tab), `UnreadCount`, `NotificationPolicy`, `SquareColors`. |
| `browser` | `app.kash.browser` | Chromium behind a small API: `BrowserEngine` (starts and stops CEF, the message router, remote debugging), `BrowserProfile` (one request context on disk), `BrowserTab` (a page, its init scripts, the messages it sends, its `StateFlow`s, its AWT view). |
| `jcef-native` | (no code) | Builds JCEF from the submodule (`buildJcef`: CMake and Ninja), lays out the native bundle (`jcefBundle`), and exposes `jcef.jar` as its `api`. |
| `macos` | `app.kash.macos` | What macOS does that Java cannot: the Notification Center and the login item (SMAppService), in `native/kash_macos.m`, compiled into `libkash_macos.dylib` by `buildMacosLibrary` (clang of the Command Line Tools) and called through FFM. Only works in an application bundle (`inAppBundle`). |

**No `org.cef` type leaves `browser`**: `jcef-native` is only an `implementation` dependency of it. Keep it that way.

## Commands

```shell
git submodule update --init --recursive   # once: jcef, and its vcpkg submodule
brew install cmake ninja pkgconf bison     # once: what the build of JCEF needs
./gradlew build                            # everything, with the tests
./gradlew :app:run                         # opens the window
./gradlew :app:run -Pkash.inspect          # the same, with Chromium's remote debugging on 127.0.0.1:9222 (see Inspecting the services)
./gradlew :app:createDistributable         # KaSH.app, in app/build/compose/binaries/main/app
./gradlew :jcef-native:jcefBundle          # only JCEF
```

`-Dkash.support=<dir>` (a system property of the JVM that runs the app, for instance through `JAVA_TOOL_OPTIONS`) makes KaSH use another directory than `~/Library/Application Support/KaSH`: use it to test without touching the user's services.

## Inspecting the services

To write the script that reads a service's unread conversations, the service has to be inspected **once the user is logged in to it**, which only the user can do:

1. The user runs `./gradlew :app:run -Pkash.inspect` (port 9222, or `-Pkash.inspect=<port>`) and logs in to the service in KaSH.
2. The `kash-devtools` MCP server of `.mcp.json` (chrome-devtools-mcp, pinned, its usage statistics, CrUX lookups and update checks off) connects to `http://127.0.0.1:9222`. Claude Code loads it at its start: it has to be restarted after the app is running.
3. Read the **structure** of the page (the DOM, the elements that hold the unread counts and the conversations' names), never more of the user's messages than that takes.

The option is given only to the run task, as a system property (Compose replaces the run task's `jvmArgs`), and `Main.kt` refuses it in the application bundle: remote debugging lets any program of the machine read and drive the logged-in services.
`curl http://127.0.0.1:9222/json/list` lists the pages, one per service. A page can be read without the MCP server with the DevTools protocol over its `webSocketDebuggerUrl` (Node 22+ has `WebSocket` built in).

## JCEF

- **The jar and the native libraries come from the same commit**: the jar calls them through JNI. `jcef-native` builds both, and nothing takes `jcef.jar` from JetBrains' Maven repository.
- **To update JCEF**: move the submodule (`git -C jcef fetch --depth 1 origin <commit> && git -C jcef checkout <commit>`), on JetBrains' `dev` branch or a release tag of JBR that has `CefRequestContext.createContext(CefRequestContextSettings, …)`, then `./gradlew :jcef-native:jcefBundle` and check that the services still persist (see **Checking persistence**).
- The build uses Ninja, not JetBrains' `jb/tools/mac/build.sh`, which needs full Xcode and runs `brew link --force bison`. It downloads CEF and builds vcpkg's dependencies **inside the submodule**, which `.gitmodules` ignores (`ignore = untracked`). `jcef-native/python/sitecustomize.py` makes JCEF's tools accept the submodule's `.git` file as a checkout.
- **The CEF framework must be next to the helpers** (`Frameworks/Chromium Embedded Framework.framework`): a helper loads it from there, and Chromium aborts with "GPU process isn't usable" when it is missing. `BrowserEngine.start` gives CEF that path with `--framework-dir-path`, since JCefAppConfig expects JetBrains' layout (inside `cef_server.app`). The out-of-process server is not built into the bundle.
- **The JetBrains Runtime must not have JCEF** (`jbr_jcef`, `jbrsdk_jcef`): its `jcef` module would win over `jcef.jar`. `BrowserEngine.start` refuses to start on one. Foojay serves `jbrsdk_jcef` for the JetBrains vendor on macOS, so a machine without a plain JBR 25 installed gets the wrong one.
- JCEF needs `--add-exports` for `sun.awt`, `sun.lwawt`, `sun.lwawt.macosx` and `--enable-native-access=ALL-UNNAMED`: `app/build.gradle.kts` gives them to the run task and the packaged application.
- On ⌘Q, `Exception in thread "AppKit Thread" java.lang.NoSuchMethodError: handleBeforeTerminate` is printed: a bug of JetBrains' JCEF, whose native code (`native/client_app.cpp`, `util_mac.mm`) still calls `CefApp.handleBeforeTerminate()`, removed from its Java in 2023 (`7e14f0c1`). It is harmless (the exception is printed and cleared, and the quit goes on to KaSH's own shutdown): left as is, by the user's choice.
- **jpackage drops the executable bit** of the resources it copies into the bundle, and Chromium cannot start its helpers without it: `restoreExecutableBits` (after `createDistributable`) gives it back. `packageDmg` runs jpackage again and does not have that fix yet.

## Scripts in the pages

- A tab is created on `about:blank`; its init scripts are registered with `Page.addScriptToEvaluateOnNewDocument` through the browser's own DevTools session (`CefDevToolsClient`, which lives as long as the browser), then the service's address is loaded (`BrowserTab.created`). Loaded right away, the first document would not have them.
- **Blink only evaluates a session's scripts while its `Page` domain is enabled**: `Page.enable` is sent first. Without it the registration succeeds (`{"identifier":"1"}`) and the script never runs.
- The page is loaded **off** the DevTools callback (`whenCompleteAsync`): loading from it re-enters Chromium's observer list ("Check failed: … ObserverListReentrancyPolicy").
- **The pages see KaSH's theme as the system's**: `BrowserTab.setColorScheme` emulates `prefers-color-scheme` (`Emulation.setEmulatedMedia`) through the same DevTools session, before the first load and again whenever the theme changes (`KashModel.colorScheme`, set by `Main.kt`). A service whose own theme follows the system (WhatsApp's "System default") has KaSH's; one set to light or dark in its own settings keeps it. Popups that a page opens are pages of their own, without it.
- A page talks to KaSH with `window.cefQuery({request: "<JSON>"})` (CEF's message router, `BrowserEngine`), which arrives in `BrowserTab.messages`.
- `window.cefQuery` is **read-only** and not configurable (CEF defines it so): a test cannot wrap it to see what a script sends. Check what KaSH receives with a page that sends it (a scratch KaSH, a service of the right kind on a local page) and the `app.kash` DEBUG log.
- The scripts are in `app/src/main/resources/app/kash/`, and `Main.kt` says which kind of service gets which (`scriptsFor`):
  - `notification-shim.js`, for every service: Chromium embedded here has **no** Web Notification API, and the script provides one that sends `{type: "notification", id, title, body, tag, silent}`. KaSH calls `__kashNotificationClick(id)` back when the notification is clicked, which fires its `click` event (a messaging service opens the conversation). A notification that a service worker shows itself (from a push) does not go through it.
  - `services/whatsapp.js`, for WhatsApp, in the top frame of `web.whatsapp.com` only: every 2 s, it reads the unread conversations from WhatsApp Web's live model (`window.require('WAWebCollections').Chat`, as whatsapp-web.js does), and sends `{type: "unread", conversations: [{id, name, kind, count, mention, preview}]}` when they changed. `time` is the chat's last activity (`chat.t`, seconds, sent in milliseconds), which the Unread tab shows over the count (`TimeLabel`: the time today, "Hier" and the time yesterday, the date and the time on another day); the messages that the model holds in memory are not all of them, and are not that. The preview is what the row of the conversation shows in WhatsApp's list (`[data-testid="last-msg-status"]`: the text with the sender in a group, and the emojis, which are images, by their `alt`; its `title` attribute has neither), matched by the name in the row (`cell-frame-title`): the list is virtualized, so a conversation whose row was never rendered has none, and the row of the Unread tab shows what the conversation is instead. The model's last message is not that preview (a call, a group event, an internal placeholder, which WhatsApp words itself). Muted conversations (until a date, forever, auto-muted) and archived ones are left out, as WhatsApp leaves them out of its title's count; one marked as unread by hand (`unreadCount` -1) counts as 1. A group that was never named (no `formattedTitle`) is named by its members' first names without the user (found through `WAWebUserPrefsMeUser`), joined in the language of WhatsApp's interface (`Intl.ListFormat`: `Alice, Bruno et Chloé`), as WhatsApp shows it. `__kashOpenConversation(id)` opens one (`WAWebCmd.Cmd.openChatBottom`), for the rows of the Unread tab. The model's names are internal to WhatsApp: when a version changes them, the script sends nothing and the page keeps working, and it has to be inspected again. IndexedDB (`model-storage`, store `chat`) holds the same data, but is written later than the model.
  - `services/google-chat.js`, for Google Chat, in the top frame of `chat.google.com` only (the catalog's `mail.google.com/chat/u/0/` redirects there): Google Chat has no model that a script can reach (compiled code, nothing in IndexedDB), so every 2 s it reads the **side panel** (`[role="group"] [data-group-id]`: the sections starred, direct messages, spaces, apps; the Home list is not in a group), and sends the same `unread` message. A row counts when it has Google Chat's badge (`.SaMfhe`, a compiled class name, the only thing that tells it): a direct message with its count, a **message request** (an account outside the organization: a row `data-group-id="pinned-item"`, one for all of them, whose id KaSH makes from the sender's name), a space only when the user is mentioned in it. A space with new messages and no mention is only in bold, without a badge, and is left out, as Google Chat leaves it out (the user's choice). The name of a person is the row's `[translate="no"]`, the name of a space the first line of its `[role="link"]` (a direct message's first line is the person's status, for screen readers). `time` is the row's `data-display-timestamp` (milliseconds). The preview is the Home list's (`[data-is-unread]` rows, `.Hkj4n`, without the rows of threads, `data-topic-id`), only while Home is shown, kept afterwards. The side panel loads its rows as it is scrolled: an unread conversation whose row is not loaded is not seen. `__kashOpenConversation(id)` clicks its row, or loads `/app/chat/<id>` when the row is not loaded. **Not seen yet when it was written**: a mention in a space, and a muted conversation (whether Google Chat gives it a badge); check them when they happen.
- `KashModel.received` reads the messages: `unread` updates the service's conversations, `notification` goes to the `Notifier`. Its log has counts, never names or messages.

## Profiles

- **A profile is a direct child of the profiles root** (`KashDirectories.profile`, `BrowserEngine.profile` checks it). Chromium creates a profile only there: given `profiles/envs/<id>` it logs "Cannot create profile at path" and silently uses one that is not written to disk, **while `CefRequestContext.getCachePath()` still returns the path it was given**. Do not trust that method to check a profile.
- A profile id starts with `env-`, so that it is never one of the directories that Chromium keeps in the same root (`Default`, `Safe Browsing`…). A service's id and its profile's are distinct: **Erase all data** gives the service a new profile.
- A deleted service's profile, and an erased one, are deleted at the next launch, before Chromium starts (`KashState.deletedProfiles`): Chromium may still hold their files while it runs.
- The state of the first version of KaSH (a generic browser, `environments` in `state.json`) is migrated by deleting its profiles (`StateFile`).

## Notifications

`Notifier` applies `NotificationPolicy` (notifications on, Do Not Disturb off, service enabled, not muted, its notifications on), then posts to macOS's Notification Center (`MacosNotifications`) when KaSH runs as KaSH.app, or shows `ToastWindow` (a window at the top right of the screen that never takes the focus) otherwise.
The Notification Center needs the bundle to be **launched by Launch Services** (`open`, the Finder, the Dock): launched by running its executable from a terminal, macOS answers "Notifications are not allowed for this application" without asking.
Its log never says what a notification says: that is the user's messages.

## The window

The main window opens where it was, as big and in the mode it was in when KaSH was closed, as KaTTY's does (the same code): `WindowFile` keeps it in `window.json`, apart from `state.json`, since it changes whenever the window moves. `keepWindowGeometry` (`WindowPersistence.kt`) writes it 500 ms after the last change and when KaSH quits. The position and the size kept are the floating window's, also while it is maximized or full screen (what it goes back to); a minimized window changes nothing, and a place that is on no screen any more (`sanitized`) becomes the platform's.

## Threads

- JCEF initializes and finishes its shutdown **on Swing's thread**. `BrowserEngine.start` (in `main`, before Compose starts) and `BrowserEngine.shutdown` (from `Dispatchers.IO` when quitting) wait for it, so they must never run on Swing's thread.
- Closing a profile waits for its cookies to be written: `KashModel` does it on `Dispatchers.IO`.
- Quitting (the close button, ⌘Q, the Dock) goes through `Kash`'s `quit`: it saves the state, closes each profile, stops Chromium, then exits. The system's Quit is intercepted for that: ending the process without it can lose the last writes of the profiles.
- **KaSH sometimes could not be quit** (⌘Q did nothing, nor the menu's Quit, nor IntelliJ's stop button; only the system's Force Quit), and it could not be made to happen again. The cause is not known yet. `Hangs.kt` is there to find it:
  - `watchEventThread` writes what the threads do (whole Java stacks, locks) to `<support>/hangs/<time>-event-thread-stuck.txt` when Swing's thread has not answered for 10 s: the system's Quit goes through it, so a stuck Swing thread makes ⌘Q do nothing.
  - `haltIfNotQuitIn` stops the process 60 s after the quit started, with `<time>-quit-stuck.txt` (the state and the window are saved before). `BrowserEngine.shutdown` logs each step with how long it took.
  - When it happens, read those files and the log. While KaSH is stuck, `jcmd <pid> Thread.print` gives the Java stacks and `sample <pid> 3` the native ones (the main thread of AppKit, which Java does not show).

- The handlers of `BrowserEngine` and the callbacks of `libkash_macos` run on threads of their own: they only set `StateFlow`s or post to Swing's thread.

## The page view

A service's page is Chromium's native view (`CefRendering.DEFAULT`) in a `SwingPanel`: native input, IME, scrolling, context menus and popups come with it. It is drawn **over** anything Compose draws in its area, so:

- **The menus are windows of their own** (`ui/WindowMenu.kt`: Jewel's `MenuContent` in an undecorated window under its button, which closes when it loses the focus or on Escape), never Jewel's `PopupMenu`, which is drawn in the window, under the page. Compose's `compose.layers.type=WINDOW` was meant to do that, but the window it made for a menu stayed 1×1 pixel at the top left of the screen (Compose 1.12.2, Jewel 0.42, JBR 25): the menus did not open. Its frame (`MenuFrame`: Jewel's menu background, a border, rounded corners, a shadow in a transparent margin of the window) is drawn by KaSH: Jewel's `MenuContent` draws only the items. A menu needs a width of its own (the design's), and a button that toggles it goes through `MenuState`, so that clicking it while the menu is open closes the menu instead of reopening it. A window that shows a `menuAnchor` provides `LocalAwtWindow`.
- Tooltips are still Compose's popups: one that would go over a page is drawn under it.
- A test cannot check that a menu stays open by launching KaSH from a terminal: macOS (14 and later) gives the focus back to the terminal, which closes the menu. Only a real click tells.
- A confirmation is a dialog window (`ConfirmDialog`), never something drawn over the page.

Every enabled service is loaded at launch (`KashModel`), hidden, so that it notifies before it is first shown: its view is created by `CefBrowser.createImmediately()` and moved into the `SwingPanel` of whichever screen shows it.
Popups that a page opens (`window.open`) are native windows of Chromium, in the same profile. DevTools open in a window of their own (the "…" menu).

## Checking persistence

The tests do not start Chromium. After a change to `browser`, `jcef-native` or the submodule, check by hand, with `-Dkash.support` on a scratch directory: a custom application on a fixed local origin writes a cookie, localStorage and IndexedDB, KaSH is quit with ⌘Q, started again, and the page reads them back in its service and not in another one.

## Running the app

`./gradlew :app:run` starts the JVM from `app/build/dev/KaSH.app` (`devBundle`), a bundle whose executable is a copy of the toolchain's `java` launcher, with links to the JDK's `lib`, `conf`, `legal` and `release` next to it: started as `java`, macOS takes the process for the JetBrains Runtime's (`com.jetbrains.jbr.java`), and ⌘Tab says "java" whatever `-Xdock:name` says. Its identifier, `app.kash.dev`, is not the application's (`app.kash`): `inAppBundle` is false in it, so the inspection port is allowed and the notifications take the fallback window. Gradle refuses an executable that is not its toolchain's, so the run task gets a `JavaLauncher` whose installation is the bundle's `Contents`, and its executable is set again in `doFirst` (Compose sets it after the script).
**Never delete those links with `File.deleteRecursively()`**: it follows them, and emptied the JDK's own `lib`, `conf` and `legal` once (the JBR had to be restored from the same build). `devBundle` deletes the links as links first.

Starting the app opens a window on the user's desktop.
**One KaSH per support directory**: `Main.kt` locks `kash.lock` in it, and a second KaSH on the same directory shows "KaSH is already open" and exits. Without it, the second one could not use the profiles (Chromium locks them for the first one): its services were not the logged-in ones and showed nothing. Before launching KaSH, check that none runs on the same directory (`pgrep -f app.kash.MainKt`, and the instance's `kash.support`).
Never type into it with synthesized keystrokes, never take a full-screen screenshot, and stop only the instance you started (by process id). Quit it as the Dock does (`NSRunningApplication.terminate()`), not with a signal, which skips the clean shutdown.

## Build conventions

The Gradle build follows the Kodein conventions (the `kodein-gradle-kotlin-project` skill): Kotlin DSL, everything from `gradle/libs.versions.toml`, typed project accessors, `jvmToolchain` from `jvm.toolchain` in every module, repositories in `settings.gradle.kts` only, every catalog plugin declared in the root with `apply false`, and a comment for every line that is not obvious.
Every task that a build script registers has a `group` and a `description`.
