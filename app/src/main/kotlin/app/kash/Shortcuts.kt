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
 * ⇧⌘K for the search ([isSearchShortcut]), ⌘0 for the Unread tab, ⌘1 to ⌘9 for the services of the rail.
 */
fun windowShortcutOf(event: KeyEvent): BrowserShortcut? {
    if (isSearchShortcut(event)) return BrowserShortcut.Search
    if (event.type != KeyEventType.KeyDown || !event.isMetaPressed || event.isShiftPressed || event.isAltPressed || event.isCtrlPressed) return null
    return when (val digit = DIGITS.indexOf(event.key)) {
        -1 -> null
        0 -> BrowserShortcut.Inbox
        else -> BrowserShortcut.Service(digit - 1)
    }
}
