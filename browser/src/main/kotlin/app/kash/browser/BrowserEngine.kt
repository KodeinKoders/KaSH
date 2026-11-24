package app.kash.browser

import java.awt.Dimension
import java.util.concurrent.atomic.AtomicReference
import com.jetbrains.cef.JCefAppConfig
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.SystemBootstrap
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefAppHandlerAdapter
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = LoggerFactory.getLogger(BrowserEngine::class.java)

/**
 * Chromium, through JetBrains' JCEF, with a profile of its own on disk for each service ([profile]).
 *
 * There is one engine in a process: Chromium cannot be started twice.
 */
public class BrowserEngine private constructor(
    private val app: CefApp,
    private val profilesRoot: Path,
    private val terminated: CountDownLatch,
) {
    private val client: CefClient = app.createClient()
    private val tabs = ConcurrentHashMap<CefBrowser, BrowserTab>()
    private val profiles = CopyOnWriteArrayList<BrowserProfile>()

    // The size of the pages that are not shown (see BrowserTab.applyHiddenSize): the last size a page was shown at, the main window's size until then.
    private val hiddenSize = AtomicReference(Dimension(1280, 800))

    // The tabs whose browser is to be created, one after the other (see create), and the one being created.
    private val pendingCreations = ArrayDeque<BrowserTab>()
    private var creating: BrowserTab? = null

    // The tabs' browsers that Chromium created, and those of tabs closed while they were being created, which are closed once they are (see
    // tabClosed): guarded by pendingCreations, as the queue.
    private val createdBrowsers = HashSet<CefBrowser>()
    private val closeOnCreation = HashMap<CefBrowser, BrowserTab>()

    // Watches the browser being created (see watchCreation): one that Chromium refuses never comes to onAfterCreated.
    private val creationWatch = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "KaSH-browser-creation").apply { isDaemon = true } }

    // The languages of the pages (see setPageLanguages): Chromium's own until they are given.
    private val languages = AtomicReference<String?>(null)

    /**
     * The size that the pages that were never shown are laid out at, until one is shown: the window that will show them. A page that is not shown
     * has no size of its own, and a service's page may then render nothing to read (a virtual list draws the rows that fit: none).
     */
    public fun setHiddenPageSize(width: Int, height: Int) {
        if (width > 0 && height > 0) hiddenSize.set(Dimension(width, height))
    }

    /** Called on Swing's thread when one of KaSH's shortcuts ([BrowserShortcut]) is pressed in a page, which does not get it. */
    @Volatile
    public var onShortcut: (BrowserShortcut) -> Unit = {}

    /**
     * The languages that every page is told the user reads, in this order (`fr-FR,fr,en-US,en`): its `Accept-Language` header and
     * `navigator.languages`. A site that follows the browser's language shows itself in the first one it has. A page has them from its next load:
     * one that is already loaded keeps its own until it is loaded again. Chromium's own otherwise (`en-US,en`).
     */
    public fun setPageLanguages(list: String) {
        languages.set(list)
    }

    // What the pages send to KaSH: `window.cefQuery({request: "…"})`, which Chromium's renderer defines in every frame. A page that is not one of
    // the tabs (a popup, DevTools) is answered too, and ignored.
    private val router: CefMessageRouter = CefMessageRouter.create().apply {
        addHandler(object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(
                browser: CefBrowser, frame: CefFrame, queryId: Long, request: String, persistent: Boolean, callback: CefQueryCallback,
            ): Boolean {
                val tab = tabs[browser]
                // Only the service's own page speaks for it: its main frame, on its site. Any frame has `cefQuery`, and so has a page of another
                // site in the tab (a sign-in, where a redirection led): their messages are left out (never their addresses in the log).
                if (tab != null) {
                    if (frame.isMain && isServicePage(frame.url, tab.initialUrl)) tab.messageReceived(request)
                    else logger.debug("A message of a frame that is not the service's page was left out")
                }
                callback.success("")
                return true
            }
        }, true)
    }

    /** The version of Chromium, for the log and the About window. */
    public val chromiumVersion: String = app.version?.chromeVersion ?: "unknown"

    init {
        // One client for all the tabs: its handlers find the tab of a browser in [tabs]. A browser that is not there is a DevTools window or a
        // popup that Chromium opened on its own, which has nothing to report.
        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onAddressChange(browser: CefBrowser, frame: CefFrame, url: String) {
                if (frame.isMain) tabs[browser]?.addressChanged(url)
            }

            override fun onTitleChange(browser: CefBrowser, title: String?) {
                tabs[browser]?.titleChanged(title.orEmpty())
            }
        })
        client.addMessageRouter(router)
        client.addKeyboardHandler(PageShortcuts(isTab = { browser -> tabs.containsKey(browser) }) { shortcut -> onShortcut(shortcut) })
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onAfterCreated(browser: CefBrowser) {
                // A tab closed while its browser was being created: closed now that it can be, and the next one is created.
                val closed = synchronized(pendingCreations) {
                    closeOnCreation.remove(browser) ?: null.also { if (tabs.containsKey(browser)) createdBrowsers += browser }
                }
                if (closed != null) {
                    browser.close(true)
                    creationEnded(closed)
                    return
                }
                val tab = tabs[browser] ?: return
                tab.created()
                creationEnded(tab)
            }

            // A window that a service's page opens: a link to another site goes to the system's browser (see popupTarget). A window that a popup
            // or DevTools opens is theirs.
            override fun onBeforePopup(browser: CefBrowser, frame: CefFrame?, targetUrl: String?, targetFrameName: String?): Boolean {
                val tab = tabs[browser] ?: return false
                if (targetUrl == null) return false
                return when (popupTarget(targetUrl, tab.initialUrl, tab.openInKash)) {
                    LinkTarget.KASH -> false
                    // Only from a page that the user looks at, in KaSH's active window: JCEF does not say whether a gesture opened the window, and
                    // a hidden page (all of them are loaded at launch) could open the browser, the mail, FaceTime or the phone without a click.
                    LinkTarget.SYSTEM -> true.also {
                        val active = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow != null
                        if (systemPopupAllowed(tab.browser.uiComponent.isShowing, active)) openInSystem(targetUrl)
                        else logger.info("A window to the system ({}:) that a page not shown opened was not opened", targetUrl.substringBefore(':').take(32))
                    }
                    LinkTarget.NOWHERE -> true.also { notLoaded(targetUrl) }
                }
            }
        })
        // A link that the user clicks in a service's page, to another site: it goes to the system's browser, and the page stays (see
        // navigationTarget).
        client.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(browser: CefBrowser, frame: CefFrame, request: CefRequest, userGesture: Boolean, isRedirect: Boolean): Boolean {
                val tab = tabs[browser] ?: return false
                // On Chromium's UI thread, before the page asks for anything: the languages that changed hold from this load.
                tab.profile.applyLanguages()
                val target = navigationTarget(request.url, tab.initialUrl, frame.url, userGesture, isRedirect, request.method, tab.openInKash)
                // A frame of the page only loads what it can: an application's address, which some pages open in a hidden frame, is not; the mail
                // or the phone that the user clicked in it goes to the system (loaded in the frame, it failed there).
                if (!frame.isMain) {
                    if (target == LinkTarget.SYSTEM && isSystemAddress(request.url)) return true.also { openInSystem(request.url) }
                    return (target == LinkTarget.NOWHERE).also { if (it) notLoaded(request.url) }
                }
                return when (target) {
                    LinkTarget.KASH -> false
                    LinkTarget.SYSTEM -> true.also { openInSystem(request.url) }
                    LinkTarget.NOWHERE -> true.also { notLoaded(request.url) }
                }
            }
        })
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(browser: CefBrowser, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
                tabs[browser]?.loadingStateChanged(isLoading, canGoBack, canGoForward)
            }
        })
        // The popups that stay in KaSH (the service's site, sign-in pages) are Chromium's windows, in the request context of the page that opened
        // them, so in the same profile, and with `window.opener`, which sign-in popups need.
    }

    /**
     * The profile in [directory], which must be a direct child of the profiles root that the engine was started with: Chromium creates a profile
     * only there, and silently uses one that is not written to disk anywhere else.
     */
    public fun profile(directory: Path): BrowserProfile {
        val absolute = directory.toAbsolutePath().normalize()
        require(absolute.parent == profilesRoot) { "A profile must be a direct child of $profilesRoot, not $absolute" }
        return BrowserProfile(absolute, client, tabs, hiddenSize, languages, ::create, ::tabClosed) { profiles -= it }.also { profiles += it }
    }

    /**
     * Creates the browser of [tab], once the browsers asked before are created: **one at a time**.
     *
     * JCEF gives a browser that Chromium created to the Java object of the oldest browser that was asked and not created yet (a queue,
     * `LifeSpanHandler::OnAfterCreated`), as if Chromium created them in that order. It does not for browsers of different profiles: each profile
     * is read from disk first, and the one read first has its browser first. At a launch, every service's page was then in another service's
     * profile (WhatsApp's in Google Chat's…), each logged out. Created one at a time, the browser created is always the one asked.
     */
    internal fun create(tab: BrowserTab) {
        val now = synchronized(pendingCreations) {
            if (creating == null) true.also { creating = tab } else false.also { pendingCreations += tab }
        }
        if (now) start(tab)
    }

    // Asks Chromium for the browser of [tab], and watches that it comes.
    private fun start(tab: BrowserTab) {
        tab.browser.createImmediately()
        watchCreation(tab)
    }

    /**
     * Every second while [tab]'s browser is being created: one that Chromium refused (JCEF logs "refusing to create browser with an unresolved
     * request context", a profile that it could not make, and marks the browser closed) never comes to `onAfterCreated`, and every tab after it
     * waited for good, gray. It is given up, and the next one is created. One that is only slow is waited for: creating another one meanwhile is
     * what gave a browser to the wrong profile.
     */
    private fun watchCreation(tab: BrowserTab) {
        creationWatch.schedule({
            val (refused, waiting) = synchronized(pendingCreations) {
                val waiting = creating == tab && !tab.created.value
                (waiting && tab.browser.isClosed) to waiting
            }
            when {
                refused -> {
                    synchronized(pendingCreations) { closeOnCreation.remove(tab.browser) }
                    logger.error("Chromium refused to create the page of the profile {}: the next ones are created", tab.profile.directory.fileName)
                    tab.creationFailed()
                    creationEnded(tab)
                }
                waiting -> watchCreation(tab)
            }
        }, 1, TimeUnit.SECONDS)
    }

    /**
     * [tab] is closed: its browser is closed when Chromium created it; when it is being created, once it is (JCEF's own `close` before that marks
     * it closing, and its deferred close then does nothing: the native browser stayed, and Chromium never stopped at quit); when its creation did
     * not start, never (there is no browser to close, and JCEF would keep the handler that it adds for it).
     */
    internal fun tabClosed(tab: BrowserTab) {
        val closeNow = synchronized(pendingCreations) {
            when {
                createdBrowsers.remove(tab.browser) -> true
                creating == tab -> {
                    closeOnCreation[tab.browser] = tab
                    return
                }
                else -> false
            }
        }
        if (closeNow) tab.browser.close(true)
        creationEnded(tab)
    }

    /** The browser of [tab] is created, or will never be (the tab is closed): the next one is created, on Swing's thread, where they all are. */
    internal fun creationEnded(tab: BrowserTab) {
        val next = synchronized(pendingCreations) {
            pendingCreations -= tab
            if (creating != tab) return
            pendingCreations.removeFirstOrNull().also { creating = it }
        } ?: return
        javax.swing.SwingUtilities.invokeLater { start(next) }
    }

    /**
     * Closes every profile, which writes what they still hold to disk, then stops Chromium and waits for it to stop, [timeout] at most.
     *
     * **Not on the Swing thread**: JCEF finishes the shutdown there, and this waits for it.
     */
    public fun shutdown(timeout: Duration = 20.seconds) {
        check(!javax.swing.SwingUtilities.isEventDispatchThread()) { "BrowserEngine.shutdown() waits for the Swing thread: call it from another one" }
        // Each step is logged with how long it took: KaSH sometimes could not be quit, and this tells which step it was stuck in.
        val start = System.nanoTime()
        val elapsed = { (System.nanoTime() - start) / 1_000_000 }
        // Each step goes on when the one before fails: Chromium is stopped whatever happened to a profile.
        val step = { name: String, action: () -> Unit -> runCatching(action).onFailure { logger.error("{} failed", name, it) } }
        creationWatch.shutdownNow()
        logger.info("Closing {} profiles", profiles.size)
        // Every profile's cookies are written at once, and each profile waits for its own, 5 s at most in all: one after the other, 5 s each, many
        // services went past the watchdog of the quit (haltIfNotQuitIn), which stopped the process before Chromium.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        val flushes = profiles.toList().map { profile -> profile to runCatching { profile.startFlush() }.getOrElse { CountDownLatch(0) } }
        flushes.forEach { (profile, flushed) -> step("Closing ${profile.directory.fileName}") { profile.close(flushed, deadline) } }
        logger.info("Profiles closed after {} ms, stopping Chromium", elapsed())
        step("Disposing the message router") { router.dispose() }
        step("Disposing the client") { client.dispose() }
        step("Stopping Chromium") { app.dispose() }
        if (terminated.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
            logger.info("Chromium stopped after {} ms", elapsed())
        } else {
            logger.warn("Chromium did not stop in {}", timeout)
        }
    }

    /** Logs that a page was not sent to [url], an application's address: its scheme only, the rest is the user's (a sign-in's token). */
    private fun notLoaded(url: String) {
        logger.info("An address of another application ({}:) was not loaded", url.substringBefore(':').take(32))
    }

    /**
     * Opens [url] in the system's browser (or the application of its scheme: the mail, the phone), off Chromium's thread: the system can take its
     * time. The address is not logged: it is the user's.
     */
    private fun openInSystem(url: String) {
        // Read as the links are (parseAddress): an address that Chromium keeps with `|` or `{` opens, escaped.
        val uri = parseAddress(url) ?: return logger.warn("A link that is not an address was not opened")
        thread(isDaemon = true, name = "KaSH-open-link") {
            runCatching {
                check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) { "The system cannot open links" }
                Desktop.getDesktop().browse(uri)
            }.onFailure { logger.warn("A {} link could not be opened in the system", uri.scheme, it) }
        }
    }

    public companion object {
        private const val FRAMEWORK_SWITCH = "--framework-dir-path="

        /**
         * Starts Chromium from the JCEF native bundle in [bundle] (see the `jcef-native` module), with its profiles in [profilesRoot].
         *
         * [inspectPort] opens Chromium's remote debugging on 127.0.0.1, which lets a DevTools client (chrome-devtools-mcp) read and drive every
         * page of every profile, logged in: only for development, never in the application that is distributed (the application decides).
         *
         * [locale] is Chromium's own (`fr`, `en-US`: its menus, its error pages, `Intl`'s default in the pages), its system's when it is null. The
         * languages that the pages are told the user reads are [setPageLanguages]'.
         *
         * Called before the application's window is shown, and **not on the Swing thread**: JCEF initializes on it, and this waits for it.
         */
        public fun start(bundle: Path, profilesRoot: Path, inspectPort: Int? = null, locale: String? = null, timeout: Duration = 30.seconds): BrowserEngine {
            // A JetBrains Runtime with JCEF (jbr_jcef, jbrsdk_jcef) has a `jcef` module of its own, whose `org.cef` classes would be used instead
            // of the ones that were built with the native bundle, and do not match it.
            check(ModuleLayer.boot().findModule("jcef").isEmpty) {
                "This Java runtime has its own JCEF (${System.getProperty("java.home")}): KaSH needs a JetBrains Runtime without it"
            }
            require(Files.isDirectory(bundle)) { "No JCEF native bundle in $bundle" }
            val root = profilesRoot.toAbsolutePath().normalize()
            Files.createDirectories(root)

            val config = JCefAppConfig.getInstance(bundle.toAbsolutePath().toString(), false)
            // The framework is next to the helpers in KaSH's bundle, where they load it from, not inside `cef_server.app` where JCefAppConfig
            // looks for it in JetBrains' bundle (see jcef-native's `jcefBundle`).
            val framework = bundle.resolve("Frameworks").resolve("Chromium Embedded Framework.framework").toAbsolutePath()
            require(Files.isDirectory(framework)) { "No CEF framework in $framework" }
            val args = config.appArgs
                .map { if (it.startsWith(FRAMEWORK_SWITCH)) "$FRAMEWORK_SWITCH$framework" else it }
                .toTypedArray()
            CefApp.setIsRemoteEnabled(false)
            SystemBootstrap.setLoader(config.loader)
            CefApp.startup(args)

            val settings = config.cefSettings.apply {
                // The browsers are native views in the window, not drawn off screen.
                windowless_rendering_enabled = false
                // Chromium's root_cache_path is this one when it is not given, and every profile has to be a direct child of it. The global
                // context's own profile (`Default`) is not used by any tab.
                cache_path = root.toString()
                // Cookies without an expiry are kept too: an environment is a browser that is never quit, from the user's point of view.
                persist_session_cookies = true
                log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING
                if (locale != null) this.locale = locale
                // Chromium only listens on 127.0.0.1.
                if (inspectPort != null) remote_debugging_port = inspectPort
            }
            if (inspectPort != null) logger.warn("Remote debugging of every page on http://127.0.0.1:{}", inspectPort)
            val terminated = CountDownLatch(1)
            CefApp.addAppHandler(object : CefAppHandlerAdapter(args) {
                override fun stateHasChanged(state: CefApp.CefAppState) {
                    if (state == CefApp.CefAppState.TERMINATED) terminated.countDown()
                }
            })
            val app = CefApp.getInstance(args, settings, null)
            val initialized = CountDownLatch(1)
            app.onInitialization { initialized.countDown() }
            check(initialized.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) { "Chromium did not start in $timeout" }
            return BrowserEngine(app, root, terminated).also { logger.info("Chromium {} started, profiles in {}", it.chromiumVersion, root) }
        }
    }
}
