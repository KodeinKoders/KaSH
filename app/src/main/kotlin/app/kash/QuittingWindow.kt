package app.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import app.kash.ui.Kash
import app.kash.ui.UiScaled
import app.kash.ui.strings
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text
import javax.swing.RootPaneContainer

/**
 * What the main window shows while KaSH quits (⌘Q, the Dock, its close button): a veil and "Quitting KaSH…" over its content, until it is gone.
 * Stopping Chromium, which writes what the profiles still hold, takes a second or two, and a window that stayed as it was looked like a ⌘Q that
 * did nothing.
 *
 * A window of its own, exactly over [owner]'s content pane, as the search is: a service's page is a native view, drawn over anything that Compose
 * draws in the main window.
 */
@Composable
fun QuittingWindow(owner: java.awt.Window?) {
    val bounds = remember(owner) {
        val pane = (owner as? RootPaneContainer)?.contentPane
        val origin = pane?.let { runCatching { it.locationOnScreen }.getOrNull() }
        if (pane == null || origin == null) null else java.awt.Rectangle(origin.x, origin.y, pane.width, pane.height)
    }
    val state = rememberWindowState(
        size = if (bounds == null) DpSize(480.dp, 320.dp) else DpSize(bounds.width.dp, bounds.height.dp),
        position = if (bounds == null) WindowPosition(Alignment.Center) else WindowPosition(bounds.x.dp, bounds.y.dp),
    )
    Window(
        onCloseRequest = {},
        state = state,
        title = strings.quittingKash,
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = false,
    ) {
        // The size of the text that Settings › Appearance gives, inside the window (UiScaled).
        UiScaled { QuittingContent() }
    }
}

/** A veil over the window, and in its middle a card that says that KaSH quits, with a spinner. */
@Composable
fun QuittingContent() {
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.fillMaxSize().background(Color(0xFF0E0F12).copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
        Row(
            Modifier.shadow(10.dp, shape).background(Kash.colors.panel, shape).padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(strings.quittingKash, fontWeight = FontWeight.Medium)
        }
    }
}
