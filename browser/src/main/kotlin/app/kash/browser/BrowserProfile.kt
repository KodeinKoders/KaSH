package app.kash.browser

import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefRendering
import org.cef.browser.CefRequestContext
import org.cef.browser.CefRequestContextSettings
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
    private val hiddenSize: AtomicReference<Dimension>,
    private val languages: AtomicReference<String?>,
    // Creates a tab's browser, after the ones asked before (BrowserEngine.create), and says that one will never be.
    private val create: (BrowserTab) -> Unit,
    private val creationEnded: (BrowserTab) -> Unit,
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

    // The languages that the profile was given (see applyLanguages): none yet.
    @Volatile
    private var appliedLanguages: String? = null

    @Volatile
    private var closed = false

    /**
     * Opens [url] in a new tab of this profile, with [initScripts] run in every document of the tab before the page's own scripts, and
     * [openInKash], addresses of other sites than [url]'s that its links open in KaSH rather than in the system's browser (`*` matches any run
     * of characters). The tab's
     * browser is created now, or once the browsers asked before are ([BrowserEngine.create]), hidden, so that its page runs (and notifies) before it
     * is first shown.
     */
    public fun openTab(url: String, initScripts: List<String> = emptyList(), openInKash: List<String> = emptyList()): BrowserTab {
        check(!closed) { "The profile $directory is closed" }
        // The page is loaded once the scripts are registered (BrowserTab.created): loaded right away, its first document would not have them.
        val browser = client.createBrowser(BLANK, CefRendering.DEFAULT, false, context)
        val tab = BrowserTab(browser, this, url, initScripts, openInKash.map(::addressPattern), hiddenSize) { closedTab ->
            own -= closedTab
            tabs -= closedTab.browser
            creationEnded(closedTab)
        }
        tabs[browser] = tab
        own += tab
        create(tab)
        return tab
    }

    /**
     * Gives the profile the languages of the pages ([BrowserEngine.setPageLanguages]) when they changed since: Chromium's `intl.selected_languages`
     * and `intl.accept_languages` preferences, which its `Accept-Language` header and `navigator.languages` are made from, and which it keeps in
     * the profile. Called before
     * each navigation of the profile's pages, so a page has them from its next load.
     *
     * Not with `CefRequestContextSettings.accept_language_list`: CEF prefers it to the preference, which could then not change while KaSH runs.
     *
     * **On Chromium's UI thread only** (a handler of the client): the preference cannot be set from another one.
     */
    internal fun applyLanguages() {
        val wanted = languages.get() ?: return
        if (wanted == appliedLanguages || closed) return
        appliedLanguages = wanted
        // The languages that the user chose first: Chromium makes the accepted ones from them again (LanguagePrefs), and would take the old ones
        // back.
        val error = LANGUAGE_PREFERENCES.firstNotNullOfOrNull { context.setPreference(it, wanted) }
        if (error != null) logger.error("Cannot give its languages to {}: {}", directory.fileName, error)
        else logger.debug("Languages of {}: {}", directory.fileName, wanted)
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
        val LANGUAGE_PREFERENCES = listOf("intl.selected_languages", "intl.accept_languages")
    }
}
