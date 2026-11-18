package app.kash.browser

import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefKeyboardHandler.CefKeyEvent
import org.cef.handler.CefKeyboardHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.EventFlags
import javax.swing.SwingUtilities

/** An editing command of a page, as macOS's Edit menu has them. */
internal enum class EditCommand(val run: (CefFrame) -> Unit) {
    UNDO(CefFrame::undo),
    REDO(CefFrame::redo),
    CUT(CefFrame::cut),
    COPY(CefFrame::copy),
    PASTE(CefFrame::paste),
    SELECT_ALL(CefFrame::selectAll),
}

// The modifiers that make a shortcut: the others (caps lock, the side of the key, the mouse buttons) do not change it.
private const val MODIFIERS =
    EventFlags.EVENTFLAG_COMMAND_DOWN or EventFlags.EVENTFLAG_SHIFT_DOWN or EventFlags.EVENTFLAG_CONTROL_DOWN or EventFlags.EVENTFLAG_ALT_DOWN

/**
 * The editing command of a key press, or null: ⌘Z, ⇧⌘Z, ⌘X, ⌘C, ⌘V, ⌘A.
 *
 * [character] is the key's character without its modifiers, in the keyboard's layout: ⌘A is the key that types `a`, wherever it is (AZERTY,
 * QWERTY).
 */
internal fun editCommandOf(type: CefKeyEvent.EventType, modifiers: Int, character: Char): EditCommand? {
    if (type != CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN) return null
    val command = EventFlags.EVENTFLAG_COMMAND_DOWN
    val shifted = command or EventFlags.EVENTFLAG_SHIFT_DOWN
    return when (modifiers and MODIFIERS) {
        command -> when (character.lowercaseChar()) {
            'z' -> EditCommand.UNDO
            'x' -> EditCommand.CUT
            'c' -> EditCommand.COPY
            'v' -> EditCommand.PASTE
            'a' -> EditCommand.SELECT_ALL
            else -> null
        }
        shifted -> if (character.lowercaseChar() == 'z') EditCommand.REDO else null
        else -> null
    }
}

/** A shortcut of KaSH's that a page does not get: KaSH takes it whatever the page shows ([BrowserEngine.onShortcut]). */
public sealed interface BrowserShortcut {
    /** ⇧⌘K: the search of the contacts and topics. */
    public data object Search : BrowserShortcut

    /** ⌘0: the Unread tab. */
    public data object Inbox : BrowserShortcut

    /** ⌘1 to ⌘9: the service at [index] (from 0) of the rail. */
    public data class Service(val index: Int) : BrowserShortcut
}

// The key codes of the digits, Windows' (which Chromium gives on every platform), of the key's place: the key of `1` on an AZERTY keyboard,
// which types `&`, is 1, as Chrome reads its own ⌘1.
private const val KEY_0 = 0x30
private const val KEY_9 = 0x39

/**
 * KaSH's shortcut of a key press, or null: ⇧⌘K (the key that types `k`, [character] being read as [editCommandOf]'s), ⌘0 to ⌘9 (by
 * [keyCode], the key's place: the digits are shifted on some layouts).
 */
internal fun browserShortcutOf(type: CefKeyEvent.EventType, modifiers: Int, character: Char, keyCode: Int = 0): BrowserShortcut? {
    if (type != CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN) return null
    val command = EventFlags.EVENTFLAG_COMMAND_DOWN
    return when (modifiers and MODIFIERS) {
        command or EventFlags.EVENTFLAG_SHIFT_DOWN -> if (character.lowercaseChar() == 'k') BrowserShortcut.Search else null
        command -> when (keyCode) {
            KEY_0 -> BrowserShortcut.Inbox
            in KEY_0 + 1..KEY_9 -> BrowserShortcut.Service(keyCode - KEY_0 - 1)
            else -> null
        }
        else -> null
    }
}

/**
 * The shortcuts of the pages, the client's only keyboard handler (a `CefClient` keeps the first one that it is given, and ignores the others).
 *
 * - KaSH's own ([browserShortcutOf]: ⇧⌘K, ⌘0 to ⌘9), before the page sees them (`onPreKeyEvent`): they go to [onShortcut], on Swing's thread, whatever the
 *   service does with them.
 * - The editing shortcuts of macOS ([editCommandOf]: ⌘C, ⌘V, ⌘X, ⌘A, ⌘Z, ⇧⌘Z), which do nothing in them otherwise. In Chrome, they are the Edit
 *   menu's: macOS gives a shortcut to the menu bar, whose item sends `copy:` to the focused view. KaSH's menu bar has no Edit menu, and Chromium's
 *   view only gets the key press, which the page does not handle: once the page had its chance (a page that handles the shortcut itself keeps
 *   it), the command is run on the focused frame, as Chrome's menu would.
 */
internal class PageShortcuts(private val onShortcut: (BrowserShortcut) -> Unit) : CefKeyboardHandlerAdapter() {
    override fun onPreKeyEvent(browser: CefBrowser, event: CefKeyEvent, isKeyboardShortcut: BoolRef): Boolean {
        val shortcut = browserShortcutOf(event.type, event.modifiers, event.unmodified_character, event.windows_key_code) ?: return false
        // Chromium's thread: KaSH's state is Swing's.
        SwingUtilities.invokeLater { onShortcut(shortcut) }
        return true
    }

    override fun onKeyEvent(browser: CefBrowser, event: CefKeyEvent): Boolean {
        val command = editCommandOf(event.type, event.modifiers, event.unmodified_character) ?: return false
        val frame = browser.focusedFrame ?: browser.mainFrame ?: return false
        command.run(frame)
        return true
    }
}
