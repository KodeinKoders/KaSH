package app.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import app.kash.ui.Kash
import app.kash.ui.KashIcon
import app.kash.ui.KashIcons
import org.jetbrains.jewel.ui.component.Text
import java.awt.GraphicsEnvironment

private val TOAST_WIDTH = 360.dp
private val TOAST_HEIGHT = 76.dp
private val TOAST_GAP = 8.dp
private val SCREEN_MARGIN = 12.dp

/**
 * The notifications that macOS's Notification Center cannot show (KaSH is not run as KaSH.app): a window without decoration at the top right of
 * the screen, which never takes the focus. Clicking a notification does what clicking it in the Notification Center does.
 */
@Composable
fun ToastWindow(notifier: Notifier, icons: ServiceIcons) {
    val toasts = notifier.toasts.takeLast(4)
    if (toasts.isEmpty()) return
    val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    val height = TOAST_HEIGHT * toasts.size + TOAST_GAP * (toasts.size - 1)
    val state = rememberWindowState(
        position = WindowPosition((screen.x + screen.width).dp - TOAST_WIDTH - SCREEN_MARGIN, screen.y.dp + SCREEN_MARGIN),
        size = DpSize(TOAST_WIDTH, height),
    )
    LaunchedEffect(height) { state.size = DpSize(TOAST_WIDTH, height) }
    Window(
        onCloseRequest = {},
        state = state,
        title = "KaSH",
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(TOAST_GAP)) {
            for (toast in toasts) {
                Row(
                    Modifier.fillMaxWidth().height(TOAST_HEIGHT)
                        .background(Kash.colors.panel, RoundedCornerShape(10.dp))
                        .border(1.dp, Kash.colors.control, RoundedCornerShape(10.dp))
                        .clickable { notifier.clicked(toast.key) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ServiceTile(toast.service, icons, size = 32.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(toast.service.title, color = Kash.colors.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(toast.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (toast.body.isNotBlank()) Text(toast.body, color = Kash.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.clickable { notifier.dismiss(toast.key) }) { KashIcon(KashIcons.Close, Kash.colors.faint) }
                }
            }
        }
    }
}
