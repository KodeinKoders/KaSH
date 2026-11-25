package app.kash.browser

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.cef.browser.CefBrowser
import org.slf4j.LoggerFactory
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

private val logger = LoggerFactory.getLogger(BrowserTab::class.java)

/** The color scheme that a page is told the system has (`prefers-color-scheme`). */
public enum class ColorScheme { LIGHT, DARK }

/** A page of a [BrowserProfile], shown by its [component]. */
public class BrowserTab internal constructor(
    internal val browser: CefBrowser,
    internal val profile: BrowserProfile,
    internal val initialUrl: String,
    private val initScripts: List<String>,
    // The addresses of other sites that the page opens in KaSH (its plugin's openInKash, as patterns: addressPattern).
    internal val openInKash: List<Regex>,
    private val hiddenSize: AtomicReference<Dimension>,
    private val onClose: (BrowserTab) -> Unit,
) {
    private val _url = MutableStateFlow(initialUrl)
    private val _title = MutableStateFlow("")
    private val _loading = MutableStateFlow(false)
    private val _canGoBack = MutableStateFlow(false)
    private val _canGoForward = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    private val _navigations = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    private val _created = MutableStateFlow(false)

    @Volatile
    private var colorScheme: ColorScheme? = null

    // The zoom of the page, Chromium's level (0 is 100 %, each level 20 % more): given again at the end of each load, since Chromium keeps a
    // zoom per host, and a page that goes to another one (a sign-in) would come back at that host's.
    @Volatile
    private var zoomLevel = 0.0

    // Whether the page has a renderer to give a size to: its first load (of the service's address) ended. Given at onAfterCreated, before that,
    // the size crashes Chromium (a null view in ExecuteDevToolsMethod).
    @Volatile
    private var rendered = false

    // Whether the service's address is loading: the blank page that the tab is created on ends its load first.
    @Volatile
    private var loadingService = false

    // Whether the page's size is emulated (applyHiddenSize): only until it is first shown.
    @Volatile
    private var sizeEmulated = false

    @Volatile
    private var shownOnce = false

    init {
        // While the page is shown, its size is the view's: it is the size of the pages that are not (hiddenSize). Shown or hidden, its size is
        // given again (see applyHiddenSize).
        browser.uiComponent.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                val component = e.component
                if (component.isShowing && component.width > 0 && component.height > 0) hiddenSize.set(Dimension(component.width, component.height))
            }
        })
        browser.uiComponent.addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
                if (browser.uiComponent.isShowing) shownOnce = true
                if (rendered) applyHiddenSize()
            }
        }
    }

    /** The address of the page, as Chromium has it (after redirects). */
    public val url: StateFlow<String> = _url.asStateFlow()

    /** The title of the page, empty when it has none. */
    public val title: StateFlow<String> = _title.asStateFlow()

    public val loading: StateFlow<Boolean> = _loading.asStateFlow()
    public val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()
    public val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    /** What the page sends with `window.cefQuery({request: …})`: the scripts of [BrowserProfile.openTab] use it to talk to KaSH. */
    public val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * Each navigation of the page to another document that stays in KaSH (a reload, a link, a redirect to a sign-in page), before it asks for
     * anything: before the scripts of the new document run, and so before what they send ([messages]).
     */
    public val navigations: SharedFlow<Unit> = _navigations.asSharedFlow()

    /**
     * Whether Chromium created the browser: show [component] only then. Shown before, its native view is parented once it exists, but JCEF drops
     * what it is told before (`setWindowVisibility`, when the view becomes showing): the view stays hidden, gray, until it is removed and added
     * again.
     */
    public val created: StateFlow<Boolean> = _created.asStateFlow()

    private val _failed = MutableStateFlow(false)

    /** Whether Chromium refused to create the browser (a profile that it could not make): the tab will never show a page. */
    public val failed: StateFlow<Boolean> = _failed.asStateFlow()

    /** Chromium refused to create the browser ([BrowserEngine]'s watch of the creation). */
    internal fun creationFailed() {
        _failed.value = true
    }

    /**
     * The AWT view of the page: a native view of Chromium, which keeps its page while it is removed from a window and added to another one.
     */
    public val component: Component get() = browser.uiComponent

    public fun back(): Unit = browser.goBack()
    public fun forward(): Unit = browser.goForward()
    public fun reload(): Unit = browser.reload()

    /** Runs [code] in the page's main frame, without waiting for it. */
    public fun execute(code: String) {
        browser.executeJavaScript(code, browser.url ?: "", 0)
    }

    /**
     * Makes the page see [scheme] as the system's color scheme (`prefers-color-scheme`), instead of the system's own: KaSH's theme. Given before
     * the browser is created, it is applied before its page is loaded; after, at once, and a page that listens to the media query follows it.
     */
    public fun setColorScheme(scheme: ColorScheme) {
        colorScheme = scheme
        if (_created.value) applyColorScheme()
    }

    /**
     * Emulates the color scheme through the browser's own DevTools session, which lives as long as the browser: it holds for every page that the
     * tab loads.
     */
    private fun applyColorScheme(): CompletableFuture<*> {
        val scheme = colorScheme ?: return CompletableFuture.completedFuture(Unit)
        val devTools = browser.devToolsClient ?: return CompletableFuture.completedFuture(Unit)
        val value = if (scheme == ColorScheme.DARK) "dark" else "light"
        return devTools.executeDevToolsMethod("Emulation.setEmulatedMedia", """{"features":[{"name":"prefers-color-scheme","value":"$value"}]}""")
            .exceptionally { logger.error("Cannot give the color scheme to {}", initialUrl, it); null }
    }

    /**
     * A page that was never shown has no size (its native view was never in a window), and is laid out at 0×0: a service that draws only the
     * rows that fit (Slack's sidebar, a virtual list) then draws none, and its plugin has nothing to read until the user shows it. Until the page
     * is first shown, it is laid out at the size of the last page shown ([hiddenSize]) through the browser's DevTools session; then at its view's,
     * which it keeps while it is hidden (its native view is moved out of the window as it is).
     *
     * Only once: removing the emulated size makes Chromium lay the page out again and wait for a new frame of it, even at the same size, and the
     * page was empty for half a second whenever it was shown.
     *
     * Called on Chromium's thread (the end of the first load) and on Swing's (the page shown): what it reads and decides, and the command that it
     * sends, are one under the tab's lock, so the commands go in the order of the decisions. Without it, Swing's thread could see the size not
     * emulated yet and leave it, then Chromium's emulate it on a page that was shown: fixed, it did not follow the window any more.
     */
    @Synchronized
    private fun applyHiddenSize(): CompletableFuture<*> {
        val devTools = browser.devToolsClient ?: return CompletableFuture.completedFuture(Unit)
        val future = if (browser.uiComponent.isShowing) {
            if (!sizeEmulated) return CompletableFuture.completedFuture(Unit)
            sizeEmulated = false
            devTools.executeDevToolsMethod("Emulation.clearDeviceMetricsOverride")
        } else {
            // A page that was shown keeps its own size.
            if (shownOnce) return CompletableFuture.completedFuture(Unit)
            sizeEmulated = true
            val size = hiddenSize.get()
            devTools.executeDevToolsMethod(
                "Emulation.setDeviceMetricsOverride",
                """{"width":${size.width},"height":${size.height},"deviceScaleFactor":0,"mobile":false}""",
            )
        }
        return future.exceptionally { logger.error("Cannot give its size to {}", initialUrl, it); null }
    }

    /** Opens Chrome's DevTools on the page, in a window of their own: in the page's profile, so they show its cookies and storage. */
    public fun openDevTools(): Unit = browser.openDevTools()

    /** Zooms the page to Chromium's [level] (0 is 100 %, each level 20 % more: `Zoom.chromiumLevel`), now and at the end of each load. */
    public fun setZoomLevel(level: Double) {
        zoomLevel = level
        if (_created.value) browser.setZoomLevel(level)
    }

    /** Closes the page: its browser is closed by the engine, now or once Chromium created it (BrowserEngine.tabClosed). */
    public fun close() {
        browser.closeDevTools()
        onClose(this)
    }

    /**
     * Chromium created the browser (on `about:blank`): the color scheme is applied and the scripts are registered for every next document, then the
     * tab's page is loaded. A script that cannot be registered is logged, and the page is loaded without it.
     *
     * The scripts are registered through the browser's own DevTools session (JCEF's `CefDevToolsClient`), which lives as long as the browser, so they
     * stay registered for every page the tab loads.
     */
    internal fun created() {
        _created.value = true
        val devTools = browser.devToolsClient
        if (devTools == null) logger.error("No DevTools client for {}: its page is loaded without its scripts", initialUrl)
        val scheme = applyColorScheme()
        val registered = if (devTools == null || initScripts.isEmpty()) {
            scheme
        } else {
            // Blink evaluates the scripts of a session only while its Page domain is enabled.
            devTools.executeDevToolsMethod("Page.enable").exceptionally { logger.error("Cannot enable the Page domain in {}", initialUrl, it); null }
            CompletableFuture.allOf(
                scheme,
                *initScripts.map { script ->
                    devTools.executeDevToolsMethod("Page.addScriptToEvaluateOnNewDocument", """{"source":${jsonString(script)}}""")
                        .exceptionally { logger.error("Cannot register a script in {}", initialUrl, it); null }
                }.toTypedArray(),
            )
        }
        // Not in the DevTools callback itself: Chromium is notifying its observers then, and loading from there re-enters their list
        // ("Check failed: ... ObserverListReentrancyPolicy").
        registered.whenCompleteAsync { _, _ ->
            loadingService = true
            browser.loadURL(initialUrl)
        }
    }

    internal fun messageReceived(request: String) {
        _messages.tryEmit(request)
    }

    internal fun navigating() {
        _navigations.tryEmit(Unit)
    }

    internal fun addressChanged(url: String) {
        // The blank page that the tab is created on is not the service's address.
        if (url != "about:blank") _url.value = url
    }

    internal fun titleChanged(title: String) {
        _title.value = title
    }

    internal fun loadingStateChanged(loading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
        _loading.value = loading
        if (!loading) browser.setZoomLevel(zoomLevel)
        if (!loading && loadingService && !rendered) {
            rendered = true
            applyHiddenSize()
        }
        _canGoBack.value = canGoBack
        _canGoForward.value = canGoForward
    }
}

/** [value] as a JSON string literal. */
internal fun jsonString(value: String): String = buildString {
    append('"')
    for (c in value) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' || c == ' ' || c == ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
    append('"')
}
