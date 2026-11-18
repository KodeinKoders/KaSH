package app.kash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import app.kash.core.RailSide
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.LocalTooltipStyle

/**
 * A tooltip in a window of its own, next to [anchor] on its [side] (out of a rail on that side of the window): [text], and [shortcut] after
 * it, fainter, as macOS writes a shortcut. It never takes the focus.
 *
 * Jewel's tooltip is a popup of Compose, drawn in the window, under a service's page (a native view of Chromium): a tile's tooltip, next to the
 * rail, is over the page. The window is placed once its content is measured, invisible until then.
 */
@Composable
fun WindowTooltip(anchor: MenuAnchor, side: RailSide, text: String, shortcut: String?) {
    val state = rememberWindowState(size = DpSize.Unspecified, position = WindowPosition(anchor.right.dp, anchor.top.dp))
    Window(
        onCloseRequest = {},
        state = state,
        title = "",
        undecorated = true,
        transparent = true,
        resizable = false,
        focusable = false,
        alwaysOnTop = true,
    ) {
        UiScaled {
            var placed by remember { mutableStateOf(false) }
            val density = LocalDensity.current.density
            Box(
                Modifier.alpha(if (placed) 1f else 0f).padding(MARGIN).onSizeChanged { size ->
                    // In points, the tooltip without its margin, centered on the anchor's side.
                    val width = size.width / density
                    val height = size.height / density
                    val centerX = (anchor.left + anchor.right) / 2
                    val centerY = (anchor.top + anchor.bottom) / 2
                    val (x, y) = when (side) {
                        RailSide.LEFT -> anchor.right + GAP to centerY - height / 2
                        RailSide.RIGHT -> anchor.left - GAP - width to centerY - height / 2
                        RailSide.TOP -> centerX - width / 2 to anchor.bottom + GAP
                        RailSide.BOTTOM -> centerX - width / 2 to anchor.top - GAP - height
                    }
                    window.setLocation((x - MARGIN.value).toInt(), (y - MARGIN.value).toInt())
                    placed = true
                },
            ) { TooltipContent(text, shortcut) }
        }
    }
}

/** What a [WindowTooltip] shows, in Jewel's tooltip's colors: [text], and [shortcut] after it. */
@Composable
fun TooltipContent(text: String, shortcut: String?) {
    val style = LocalTooltipStyle.current
    val shape = RoundedCornerShape(style.metrics.cornerSize)
    Row(
        Modifier.shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
            .background(style.colors.background, shape)
            .border(style.metrics.borderWidth, style.colors.border, shape)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, color = style.colors.content, maxLines = 1)
        if (shortcut != null) Text(shortcut, color = style.colors.content.copy(alpha = 0.6f), maxLines = 1)
    }
}

// The transparent margin of the window around the tooltip, where its shadow is drawn.
private val MARGIN = 8.dp

// Between the anchor and the tooltip, in points.
private const val GAP = 4f
