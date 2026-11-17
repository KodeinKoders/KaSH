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
public enum class BrowserShortcut {
    /** ⇧⌘K: the search of the contacts and topics. */
    SEARCH,
}

/** KaSH's shortcut of a key press, or null: ⇧⌘K. [character] is read as [editCommandOf]'s. */
internal fun browserShortcutOf(type: CefKeyEvent.EventType, modifiers: Int, character: Char): BrowserShortcut? {
    if (type != CefKeyEvent.EventType.KEYEVENT_RAWKEYDOWN) return null
    val shifted = EventFlags.EVENTFLAG_COMMAND_DOWN or EventFlags.EVENTFLAG_SHIFT_DOWN
    return if (modifiers and MODIFIERS == shifted && character.lowercaseChar() == 'k') BrowserShortcut.SEARCH else null
}

/**
 * The shortcuts of the pages, the client's only keyboard handler (a `CefClient` keeps the first one that it is given, and ignores the others).
 *
 * - KaSH's own ([browserShortcutOf]), before the page sees them (`onPreKeyEvent`): they go to [onShortcut], on Swing's thread, whatever the
 *   service does with them.
 * - The editing shortcuts of macOS ([editCommandOf]: ⌘C, ⌘V, ⌘X, ⌘A, ⌘Z, ⇧⌘Z), which do nothing in them otherwise. In Chrome, they are the Edit
 *   menu's: macOS gives a shortcut to the menu bar, whose item sends `copy:` to the focused view. KaSH's menu bar has no Edit menu, and Chromium's
 *   view only gets the key press, which the page does not handle: once the page had its chance (a page that handles the shortcut itself keeps
 *   it), the command is run on the focused frame, as Chrome's menu would.
 */
internal class PageShortcuts(private val onShortcut: (BrowserShortcut) -> Unit) : CefKeyboardHandlerAdapter() {
    override fun onPreKeyEvent(browser: CefBrowser, event: CefKeyEvent, isKeyboardShortcut: BoolRef): Boolean {
        val shortcut = browserShortcutOf(event.type, event.modifiers, event.unmodified_character) ?: return false
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
