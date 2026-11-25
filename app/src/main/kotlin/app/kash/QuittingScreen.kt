package app.kash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kash.ui.Kash
import app.kash.ui.strings
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Text

/**
 * What the main window shows while KaSH quits (⌘Q, the Dock, its close button), in place of the Unread tab or a service's page, until KaSH is
 * gone ([KashModel.quitting]): "Quitting KaSH…" and a spinner in its middle. Stopping Chromium, which writes what the profiles still hold, takes a
 * second or two, and a window that stayed as it was looked like a ⌘Q that did nothing.
 *
 * In the window, not a window of its own over it, which did not follow it when it was moved or resized.
 */
@Composable
fun QuittingScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(strings.quittingKash, fontWeight = FontWeight.Medium, color = Kash.colors.muted)
        }
    }
}
