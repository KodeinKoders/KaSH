package app.kash

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import app.kash.core.UnreadCount
import app.kash.macos.MacosWindows
import app.kash.ui.CountPill
import app.kash.ui.UiScaled
import app.kash.ui.LocalUiScale
import app.kash.ui.uiScaleFactor
import app.kash.ui.Kash
import app.kash.ui.KashIcon
import app.kash.ui.KashIcons
import app.kash.ui.strings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.jewel.ui.component.Text
import java.awt.GraphicsEnvironment

private val TOAST_WIDTH = 360.dp
private val TOAST_HEIGHT = 76.dp
private val TOAST_GAP = 8.dp
private val SCREEN_MARGIN = 12.dp

// The title of the notifications' window, which no one sees (it has no title bar), and which tells it to AppKit (MacosWindows.makeNonactivating).
private const val TOAST_TITLE = "KaSH notifications"

/**
 * KaSH's own notifications, when the settings ask for them or macOS's Notification Center is unavailable (KaSH is not run as KaSH.app): a window without decoration at the top right of
 * the screen, which never takes the focus. Clicking a notification does what clicking it in the Notification Center does; closing it with its
 * cross leaves the application that is in front where it is.
 *
 * macOS brings an application to the front at a click on any of its windows, the click on a cross too, but a panel that does not activate it
 * (`NSWindowStyleMaskNonactivatingPanel`), which AWT does not make (its `POPUP` windows are normal ones, measured): the window is a utility one
 * (`Window.Type.UTILITY`, which AWT makes an `NSPanel`; set before it is shown, in [SwingWindow]'s `init`: AWT refuses it afterwards), which
 * [MacosWindows.makeNonactivating] makes one once it is open, by its title.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ToastWindow(notifier: Notifier, icons: ServiceIcons) {
    val toasts = notifier.toasts.takeLast(4)
    if (toasts.isEmpty()) return
    val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    // As tall as their text is big (Settings › Appearance › Text size): the window is made outside them, from the scale itself.
    val toastHeight = TOAST_HEIGHT * LocalUiScale.current
    val height = toastHeight * toasts.size + TOAST_GAP * (toasts.size - 1)
    val state = rememberWindowState(
        position = WindowPosition((screen.x + screen.width).dp - TOAST_WIDTH - SCREEN_MARGIN, screen.y.dp + SCREEN_MARGIN),
        size = DpSize(TOAST_WIDTH, height),
    )
    LaunchedEffect(height) { state.size = DpSize(TOAST_WIDTH, height) }
    SwingWindow(
        onCloseRequest = {},
        state = state,
        title = TOAST_TITLE,
        decoration = WindowDecoration.Undecorated(),
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
        init = { window ->
            window.type = java.awt.Window.Type.UTILITY
            window.addWindowListener(object : java.awt.event.WindowAdapter() {
                override fun windowOpened(e: java.awt.event.WindowEvent) = MacosWindows.makeNonactivating(TOAST_TITLE)
            })
        },
    ) {
        // The size of the text that Settings › Appearance gives, inside the window (UiScaled).
        UiScaled {
            ToastContent(
                toasts, icons, notifier::countOf, onClick = { notifier.clicked(it.key) }, onClose = { notifier.dismiss(it.key) },
                markReadOf = notifier::markReadOf, onMarkRead = { notifier.markRead(it.key) },
            )
        }
    }
}

/** The notifications of [ToastWindow], apart from its window: a click on one is [onClick], on its cross [onClose]. */
@Composable
internal fun ToastContent(
    toasts: List<Toast>, icons: ServiceIcons, countOf: (Toast) -> Int, onClick: (Toast) -> Unit, onClose: (Toast) -> Unit,
    markReadOf: (Toast) -> MarkRead? = { null }, onMarkRead: (Toast) -> Unit = {},
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(TOAST_GAP)) {
        for (toast in toasts) {
            Row(
                Modifier.fillMaxWidth().height(TOAST_HEIGHT * uiScaleFactor())
                    .background(Kash.colors.panel, RoundedCornerShape(10.dp))
                    .border(1.dp, Kash.colors.control, RoundedCornerShape(10.dp))
                    .clickable { onClick(toast) }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A test notification is KaSH's own.
                val appIcon = remember { AppIcon.painter() }
                if (toast.service != null) ServiceTile(toast.service, icons, size = 32.dp)
                else if (appIcon != null) Image(appIcon, contentDescription = null, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(toast.service?.title ?: "KaSH", color = Kash.colors.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(toast.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (toast.body.isNotBlank()) Text(toast.body, color = Kash.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // How many messages of the contact are unread (its notifications, or its conversation's count when the plugin has more): only
                // when there are several, a single message is the one shown.
                val count = countOf(toast)
                if (count > 1) CountPill(UnreadCount.badge(count))
                // Its conversation marked as read without showing its service, when its plugin can.
                val how = markReadOf(toast)
                if (how != null) {
                    val markRead = if (how == MarkRead.BY_OPENING) strings.markReadByOpening else strings.markRead
                    Row(Modifier.semantics { contentDescription = markRead }.clickable { onMarkRead(toast) }) { KashIcon(KashIcons.Check, Kash.colors.faint) }
                }
                val close = strings.searchClose
                Row(Modifier.semantics { contentDescription = close }.clickable { onClose(toast) }) { KashIcon(KashIcons.Close, Kash.colors.faint) }
            }
        }
    }
}
