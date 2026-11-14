package app.kash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.jewel.ui.component.DefaultMenuController
import org.jetbrains.jewel.ui.component.LocalMenuController
import org.jetbrains.jewel.ui.component.MenuContent
import org.jetbrains.jewel.ui.component.MenuScope
import org.jetbrains.jewel.ui.component.styling.LocalMenuStyle
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

/** Where a menu's button is on the screen, in points (AWT's coordinates): set by [menuAnchor], read by [WindowMenu]. */
class MenuAnchor {
    var left by mutableStateOf(0f)
    var right by mutableStateOf(0f)
    var bottom by mutableStateOf(0f)
}

/** The AWT window that the composition is in: what [menuAnchor] measures from. Each window of KaSH provides its own. */
val LocalAwtWindow = staticCompositionLocalOf<java.awt.Window?> { null }

/**
 * Whether a [WindowMenu] is open, which its button toggles. A click on the button while the menu is open first takes the focus from the menu,
 * which closes it ([dismiss]), then toggles it: a toggle right after a dismissal is that click, and is ignored, so the menu stays closed.
 */
class MenuState {
    var open by mutableStateOf(false)
        private set
    private var dismissedAt = 0L

    fun toggle() {
        if (System.currentTimeMillis() - dismissedAt > REOPEN_DELAY_MS) open = !open
    }

    fun dismiss() {
        if (open) dismissedAt = System.currentTimeMillis()
        open = false
    }

    private companion object {
        const val REOPEN_DELAY_MS = 300
    }
}

/** Keeps [anchor] at this element's place on the screen. */
@Composable
fun Modifier.menuAnchor(anchor: MenuAnchor): Modifier {
    val window = LocalAwtWindow.current
    val density = LocalDensity.current.density
    return onGloballyPositioned { coordinates ->
        // The window's content, not the window: its frame has a title bar, or not, depending on the window.
        val pane = (window as? javax.swing.RootPaneContainer)?.contentPane ?: return@onGloballyPositioned
        val origin: java.awt.Point = runCatching { pane.locationOnScreen }.getOrNull() ?: return@onGloballyPositioned
        val position = coordinates.positionInWindow()
        anchor.left = origin.x + position.x / density
        anchor.right = origin.x + (position.x + coordinates.size.width) / density
        anchor.bottom = origin.y + (position.y + coordinates.size.height) / density
    }
}

/**
 * Jewel's menu ([MenuContent], its items and style), in a window of its own under [anchor], aligned on its right edge when [alignEnd]: it closes
 * when an item is chosen, when it loses the focus (a click elsewhere), or on Escape.
 *
 * Jewel's PopupMenu is a popup of Compose, drawn in the window, under a service's page, which is a native view of Chromium. Compose can put its
 * popups in windows of their own (`compose.layers.type=WINDOW`), but the window that it made for a menu stayed 1×1 pixel at the top left of the
 * screen (Compose 1.12.2, Jewel 0.42, on the JetBrains Runtime 25): the menus did not open. This window is sized by its content.
 */
@Composable
fun WindowMenu(anchor: MenuAnchor, width: Dp, alignEnd: Boolean, onDismiss: () -> Unit, content: MenuScope.() -> Unit) {
    // The callback is made once and calls the latest one: Jewel's menu forgets that a click was on an item when its controller's callback is a
    // new object at each drawing (KaTTY's `KattyMenu` says the same).
    val latest by rememberUpdatedState(onDismiss)
    val controller = remember { DefaultMenuController(onDismissRequest = { latest(); true }) }
    val state = rememberWindowState(
        size = DpSize.Unspecified,
        position = WindowPosition((if (alignEnd) anchor.right - width.value else anchor.left).dp - MARGIN, (anchor.bottom + GAP).dp - MARGIN),
    )
    Window(
        onCloseRequest = { latest() },
        state = state,
        title = "",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                latest()
                true
            } else {
                false
            }
        },
    ) {
        DisposableEffect(window) {
            val listener = object : WindowFocusListener {
                override fun windowGainedFocus(e: WindowEvent) = Unit
                override fun windowLostFocus(e: WindowEvent) = latest()
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }
        // Under its button, starting where the button starts, or ending where it ends; again when the button moves. The window is larger than the
        // menu by its margin, where the shadow is drawn.
        LaunchedEffect(anchor.left, anchor.right, anchor.bottom, alignEnd) {
            val x = if (alignEnd) anchor.right - width.value else anchor.left
            window.setLocation((x - MARGIN.value).toInt(), (anchor.bottom + GAP - MARGIN.value).toInt())
        }
        CompositionLocalProvider(LocalMenuController provides controller) {
            MenuFrame(width, content)
        }
    }
}

/**
 * The frame of the design's menus around Jewel's [MenuContent] (Jewel's menu background, a border of the controls' color, rounded corners, a shadow, in a
 * transparent margin where the shadow is drawn): MenuContent draws only the items, and Jewel's PopupMenu, which draws the rest, is not used here.
 */
@Composable
fun MenuFrame(width: Dp, content: MenuScope.() -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(Modifier.padding(MARGIN)) {
        Box(
            Modifier.width(width)
                .shadow(SHADOW, shape, ambientColor = Color.Black.copy(alpha = 0.28f), spotColor = Color.Black.copy(alpha = 0.28f))
                // Jewel's own menu background: its items are drawn on it (white in the light theme), and would be white blocks on the design's panel.
                .background(LocalMenuStyle.current.colors.background, shape)
                .border(1.dp, Kash.colors.control, shape)
                .clip(shape)
                .padding(4.dp),
        ) {
            // A width of its own: sized by its content, the window gave the menu none, and its items were squeezed in a column of a few points.
            MenuContent(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

// The transparent margin of the window around the menu, where its shadow is drawn.
private val MARGIN = 16.dp

// The design's shadow: `0 6px 20px` black at 28 %.
private val SHADOW = 10.dp

// Between the button and its menu, in points.
private const val GAP = 2f
