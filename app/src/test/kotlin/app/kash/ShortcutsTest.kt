@file:OptIn(ExperimentalTestApi::class)

package app.kash

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import app.kash.browser.BrowserShortcut
import kotlin.test.Test
import kotlin.test.assertEquals

class ShortcutsTest {
    // The shortcuts that the keys pressed in a focused view give, as the main window reads them.
    private fun shortcutsOf(keys: KeyInjectionScope.() -> Unit): List<BrowserShortcut> {
        val found = mutableListOf<BrowserShortcut>()
        runComposeUiTest {
            setContent {
                val focus = remember { FocusRequester() }
                Box(
                    Modifier.size(10.dp).testTag("view").focusRequester(focus).focusable()
                        .onPreviewKeyEvent { event -> windowShortcutOf(event)?.also { found += it } != null },
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
            onNodeWithTag("view").performKeyInput(keys)
        }
        return found
    }

    @Test
    fun commandAndADigitAreTheUnreadTabAndTheServices() {
        val found = shortcutsOf {
            withKeyDown(Key.MetaLeft) {
                pressKey(Key.Zero)
                pressKey(Key.One)
                pressKey(Key.Nine)
            }
            withKeyDown(Key.MetaLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.K) } }
        }
        assertEquals(listOf(BrowserShortcut.Inbox, BrowserShortcut.Service(0), BrowserShortcut.Service(8), BrowserShortcut.Search), found)
    }

    @Test
    fun commandPlusAndMinusZoomTheServiceShown() {
        val found = shortcutsOf {
            withKeyDown(Key.MetaLeft) {
                pressKey(Key.Equals)
                pressKey(Key.Minus)
                pressKey(Key.NumPadAdd)
            }
        }
        assertEquals(listOf(BrowserShortcut.ZoomIn, BrowserShortcut.ZoomOut, BrowserShortcut.ZoomIn), found)
    }

    @Test
    fun commandRBracketsAndOptionCommandIAreThoseOfThePage() {
        val found = shortcutsOf {
            withKeyDown(Key.MetaLeft) {
                pressKey(Key.R)
                pressKey(Key.LeftBracket)
                pressKey(Key.RightBracket)
                withKeyDown(Key.AltLeft) { pressKey(Key.I) }
            }
        }
        assertEquals(listOf(BrowserShortcut.Reload, BrowserShortcut.Back, BrowserShortcut.Forward, BrowserShortcut.DevTools), found)
    }

    @Test
    fun otherKeysAndModifiersAreNotKashs() {
        val found = shortcutsOf {
            pressKey(Key.One)
            withKeyDown(Key.MetaLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.One) } }
            withKeyDown(Key.MetaLeft) { withKeyDown(Key.AltLeft) { pressKey(Key.One) } }
            withKeyDown(Key.MetaLeft) { pressKey(Key.A) }
            withKeyDown(Key.MetaLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.R) } }
            withKeyDown(Key.MetaLeft) { withKeyDown(Key.AltLeft) { pressKey(Key.R) } }
        }
        assertEquals(emptyList(), found)
    }

    @Test
    fun aShortcutFromTheWindowAndThePageIsDoneOnce() {
        var now = 0L
        val twins = ShortcutTwins { now }
        assertEquals(true, twins.admit(BrowserShortcut.Search, ShortcutSource.WINDOW))
        now += 5_000_000
        assertEquals(false, twins.admit(BrowserShortcut.Search, ShortcutSource.PAGE))
        // The next press: once again, whichever source comes first.
        now += 400_000_000
        assertEquals(true, twins.admit(BrowserShortcut.Search, ShortcutSource.PAGE))
        now += 2_000_000
        assertEquals(false, twins.admit(BrowserShortcut.Search, ShortcutSource.WINDOW))
    }

    @Test
    fun aKeyHeldDownRepeatsItsShortcut() {
        var now = 0L
        val twins = ShortcutTwins { now }
        // ⌘+ held: each repeat from both sources, every 30 ms.
        val done = (0 until 4).count { repeat ->
            now = repeat * 30_000_000L
            val window = twins.admit(BrowserShortcut.ZoomIn, ShortcutSource.WINDOW)
            now += 1_000_000
            val page = twins.admit(BrowserShortcut.ZoomIn, ShortcutSource.PAGE)
            window && !page
        }
        assertEquals(4, done)
        // From one source only (a key that the other does not read), each press is done.
        assertEquals(true, twins.admit(BrowserShortcut.Back, ShortcutSource.PAGE))
        assertEquals(true, twins.admit(BrowserShortcut.Back, ShortcutSource.PAGE))
        // Another shortcut right after is not a twin.
        assertEquals(true, twins.admit(BrowserShortcut.Forward, ShortcutSource.WINDOW))
    }
}
