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
