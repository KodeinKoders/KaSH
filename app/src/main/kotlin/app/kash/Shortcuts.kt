package app.kash

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import app.kash.browser.BrowserShortcut

// The keys of the digits, by their place (AWT's key codes on macOS: the key of 1 on an AZERTY keyboard, which types `&`, is One).
private val DIGITS = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

/** Where a shortcut of KaSH's came from: one of its windows ([windowShortcutOf]), or a page (`PageShortcuts`, before the page). */
enum class ShortcutSource { WINDOW, PAGE }

// How long after a shortcut its twin from the other source can come: both come from the same key press, a few milliseconds apart; a key held
// down repeats every 30 ms at the fastest, from each source.
private const val TWIN_NANOS = 150_000_000L

/**
 * Lets a shortcut through once when a key press brings it from both sources: a key with ⌘ pressed in a page goes to KaSH's window (AppKit gives it
 * to AWT's view first, which Chromium's is inside, and AWT gives it to Compose) and to the page's `onPreKeyEvent`. Without it, ⇧⌘K opened the
 * search and closed it at once. One that comes from one source only (a key that only one of them reads, the search's own window) goes through.
 * On Swing's thread.
 */
class ShortcutTwins(private val clock: () -> Long = System::nanoTime) {
    private var last: Triple<BrowserShortcut, ShortcutSource, Long>? = null

    /** Whether [shortcut], from [source], is to be done: not when it is the twin of the one before. */
    fun admit(shortcut: BrowserShortcut, source: ShortcutSource): Boolean {
        val now = clock()
        val before = last
        if (before != null && before.first == shortcut && before.second != source && now - before.third < TWIN_NANOS) {
            last = null
            return false
        }
        last = Triple(shortcut, source, now)
        return true
    }
}

/**
 * KaSH's shortcut of a key press in one of its windows, or null: the same as a page's (`PageShortcuts`, which takes them before the page),
 * ⇧⌘K for the search ([isSearchShortcut]), ⌘0 for the Unread tab, ⌘1 to ⌘9 for the services of the rail, ⌘+ and ⌘− for the zoom of the
 * service shown, ⌘R, ⌘[, ⌘] and ⌥⌘I for its page (reload, back, forward, the developer tools).
 */
fun windowShortcutOf(event: KeyEvent): BrowserShortcut? {
    if (isSearchShortcut(event)) return BrowserShortcut.Search
    // ⌘+ (or ⌘=, with ⇧ or not) and ⌘−, as Chrome's, and the keypad's.
    if (event.type == KeyEventType.KeyDown && event.isMetaPressed && !event.isAltPressed && !event.isCtrlPressed) {
        when (event.key) {
            Key.Equals, Key.Plus, Key.NumPadAdd -> return BrowserShortcut.ZoomIn
            Key.Minus, Key.NumPadSubtract -> return BrowserShortcut.ZoomOut
        }
    }
    if (event.type != KeyEventType.KeyDown || !event.isMetaPressed || event.isShiftPressed || event.isCtrlPressed) return null
    if (event.isAltPressed) return if (event.key == Key.I) BrowserShortcut.DevTools else null
    return when (val digit = DIGITS.indexOf(event.key)) {
        -1 -> when (event.key) {
            Key.R -> BrowserShortcut.Reload
            Key.LeftBracket -> BrowserShortcut.Back
            Key.RightBracket -> BrowserShortcut.Forward
            else -> null
        }
        0 -> BrowserShortcut.Inbox
        else -> BrowserShortcut.Service(digit - 1)
    }
}
