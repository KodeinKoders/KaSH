package app.kash.browser

import org.cef.handler.CefKeyboardHandler.CefKeyEvent.EventType
import org.cef.misc.EventFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageShortcutsTest {
    private val command = EventFlags.EVENTFLAG_COMMAND_DOWN
    private val down = EventType.KEYEVENT_RAWKEYDOWN

    @Test
    fun theShortcutsOfTheEditMenuAreItsCommands() {
        assertEquals(EditCommand.COPY, editCommandOf(down, command, 'c'))
        assertEquals(EditCommand.PASTE, editCommandOf(down, command, 'v'))
        assertEquals(EditCommand.CUT, editCommandOf(down, command, 'x'))
        assertEquals(EditCommand.SELECT_ALL, editCommandOf(down, command, 'a'))
        assertEquals(EditCommand.UNDO, editCommandOf(down, command, 'z'))
        assertEquals(EditCommand.REDO, editCommandOf(down, command or EventFlags.EVENTFLAG_SHIFT_DOWN, 'z'))
    }

    @Test
    fun capsLockAndTheSideOfTheKeyChangeNothing() {
        val flags = command or EventFlags.EVENTFLAG_CAPS_LOCK_ON or EventFlags.EVENTFLAG_IS_LEFT
        assertEquals(EditCommand.COPY, editCommandOf(down, flags, 'C'))
    }

    @Test
    fun otherKeysModifiersAndEventsAreNotCommands() {
        assertNull(editCommandOf(down, command, 'q'))
        // ⇧⌘V (paste and match style in some applications) and ⌥⌘C are the page's.
        assertNull(editCommandOf(down, command or EventFlags.EVENTFLAG_SHIFT_DOWN, 'v'))
        assertNull(editCommandOf(down, command or EventFlags.EVENTFLAG_ALT_DOWN, 'c'))
        assertNull(editCommandOf(down, EventFlags.EVENTFLAG_CONTROL_DOWN, 'c'))
        assertNull(editCommandOf(down, EventFlags.EVENTFLAG_NONE, 'c'))
        // Only the press: the release and the character would run it again.
        assertNull(editCommandOf(EventType.KEYEVENT_KEYUP, command, 'c'))
        assertNull(editCommandOf(EventType.KEYEVENT_CHAR, command, 'c'))
    }

    @Test
    fun shiftCommandKIsTheSearchWhateverTheLayoutAndCapsLock() {
        val shifted = command or EventFlags.EVENTFLAG_SHIFT_DOWN
        assertEquals(BrowserShortcut.Search, browserShortcutOf(down, shifted, 'k'))
        assertEquals(BrowserShortcut.Search, browserShortcutOf(down, shifted or EventFlags.EVENTFLAG_CAPS_LOCK_ON, 'K'))
        // ⌘K is the page's own (Slack's quick switcher), ⌥⇧⌘K and ⌃⇧K are not KaSH's.
        assertNull(browserShortcutOf(down, command, 'k'))
        assertNull(browserShortcutOf(down, shifted or EventFlags.EVENTFLAG_ALT_DOWN, 'k'))
        assertNull(browserShortcutOf(down, EventFlags.EVENTFLAG_CONTROL_DOWN or EventFlags.EVENTFLAG_SHIFT_DOWN, 'k'))
        assertNull(browserShortcutOf(down, shifted, 'f'))
        assertNull(browserShortcutOf(EventType.KEYEVENT_KEYUP, shifted, 'k'))
        assertNull(browserShortcutOf(EventType.KEYEVENT_CHAR, shifted, 'k'))
    }

    @Test
    fun commandAndADigitAreTheUnreadTabAndTheServicesByTheKeysPlace() {
        // The key of 1, whatever it types (`&` on an AZERTY keyboard).
        assertEquals(BrowserShortcut.Service(0), browserShortcutOf(down, command, '&', 0x31))
        assertEquals(BrowserShortcut.Service(8), browserShortcutOf(down, command, '9', 0x39))
        assertEquals(BrowserShortcut.Inbox, browserShortcutOf(down, command, '0', 0x30))
        // With another modifier, or without ⌘, they are the page's.
        assertNull(browserShortcutOf(down, command or EventFlags.EVENTFLAG_SHIFT_DOWN, '1', 0x31))
        assertNull(browserShortcutOf(down, command or EventFlags.EVENTFLAG_ALT_DOWN, '1', 0x31))
        assertNull(browserShortcutOf(down, EventFlags.EVENTFLAG_NONE, '1', 0x31))
        assertNull(browserShortcutOf(down, command, 'a', 0x41))
        assertNull(browserShortcutOf(EventType.KEYEVENT_KEYUP, command, '1', 0x31))
    }
}
