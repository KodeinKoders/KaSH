package app.kash.browser

import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefRendering
import org.cef.browser.CefRequestContext
import org.cef.browser.CefRequestContextSettings
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger(BrowserProfile::class.java)

/**
 * A Chromium profile on disk: cookies, cache, IndexedDB, local storage and service workers that no other profile sees, and that are there again
 * at the next launch. One service of KaSH.
 */
public class BrowserProfile internal constructor(
    /** Where the profile is. */
    public val directory: Path,
    private val client: CefClient,
    private val tabs: ConcurrentHashMap<CefBrowser, BrowserTab>,
    private val onClose: (BrowserProfile) -> Unit,
) {
    private val context: CefRequestContext = CefRequestContext.createContext(
        CefRequestContextSettings().apply {
            cache_path = directory.toString()
            persist_session_cookies = true
        },
        null,
    )
    private val own = ConcurrentHashMap.newKeySet<BrowserTab>()

    @Volatile
    private var closed = false

    /**
     * Opens [url] in a new tab of this profile, with [initScripts] run in every document of the tab before the page's own scripts. The tab's
     * browser is created now, hidden, so that its page runs (and notifies) before it is first shown.
     */
    public fun openTab(url: String, initScripts: List<String> = emptyList()): BrowserTab {
        check(!closed) { "The profile $directory is closed" }
        // The page is loaded once the scripts are registered (BrowserTab.created): loaded right away, its first document would not have them.
        val browser = client.createBrowser(BLANK, CefRendering.DEFAULT, false, context)
        val tab = BrowserTab(browser, url, initScripts) { closedTab ->
            own -= closedTab
            tabs -= closedTab.browser
        }
        tabs[browser] = tab
        own += tab
        browser.createImmediately()
        return tab
    }

    /** Empties the HTTP cache of the profile: what was downloaded. The cookies and the storage are kept, so the service stays logged in. */
    public fun clearHttpCache(onDone: () -> Unit = {}) {
        context.clearHttpCache { onDone() }
    }

    /**
     * Closes the tabs of the profile, after writing its cookies to disk (Chromium writes them a while after they change, and the last ones would
     * be lost when it stops).
     */
    public fun close() {
        if (closed) return
        closed = true
        val flushed = CountDownLatch(1)
        if (context.cookieManager?.flushStore { flushed.countDown() } != true) flushed.countDown()
        if (!flushed.await(5, TimeUnit.SECONDS)) logger.warn("The cookies of {} were not written in 5 s", directory)
        own.toList().forEach { it.close() }
        context.dispose()
        onClose(this)
    }

    private companion object {
        const val BLANK = "about:blank"
    }
}
