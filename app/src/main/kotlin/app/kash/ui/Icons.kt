package app.kash.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/** A circle as SVG path data: the design's icons use `<circle>`, which a vector path does not have. */
private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

/** An icon of the design: strokes of [width] on a 20×20 grid, round caps and joins, in black (tinted where it is drawn). */
private fun strokeIcon(name: String, vararg paths: String, width: Float = 1.5f): ImageVector =
    ImageVector.Builder(name, 20.dp, 20.dp, 20f, 20f).apply {
        for (d in paths) {
            addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** An icon of the design that is filled rather than stroked. */
private fun fillIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 20.dp, 20.dp, 20f, 20f).apply {
        for (d in paths) addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
    }.build()

/** The icons of the design, drawn from its inline SVGs. */
object KashIcons {
    val Sun = strokeIcon(
        "sun", circle(10f, 10f, 3.2f),
        "M10 2.5v1.8M10 15.7v1.8M2.5 10h1.8M15.7 10h1.8M4.7 4.7l1.3 1.3M14 14l1.3 1.3M4.7 15.3L6 14M14 6l1.3-1.3",
    )
    val Moon = strokeIcon("moon", "M16 12.2A6.5 6.5 0 0 1 7.8 4a6.5 6.5 0 1 0 8.2 8.2z")
    val Monitor = strokeIcon("monitor", "M4.5 4h11a1.5 1.5 0 0 1 1.5 1.5v7a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 3 12.5v-7A1.5 1.5 0 0 1 4.5 4z", "M7.5 17h5M10 14v3")
    val ChevronDown = strokeIcon("chevron-down", "M5 8l5 5 5-5", width = 2f)
    val ChevronLeft = strokeIcon("chevron-left", "M12 5l-5 5 5 5")
    val ChevronRight = strokeIcon("chevron-right", "M8 5l5 5-5 5")
    val Inbox = strokeIcon("inbox", "M3 12h4l1.5 2.5h3L13 12h4", "M5.2 4h9.6l2.2 8v3.2a.8.8 0 0 1-.8.8H3.8a.8.8 0 0 1-.8-.8V12z")
    val Plus = strokeIcon("plus", "M10 4v12M4 10h12")
    val Bell = strokeIcon("bell", "M6 8a4 4 0 0 1 8 0v3l1.5 3h-11L6 11z", "M8.5 16.5a1.6 1.6 0 0 0 3 0")
    val BellOff = strokeIcon("bell-off", "M6 8a4 4 0 0 1 8 0v3l1.5 3h-11L6 11z", "M8.5 16.5a1.6 1.6 0 0 0 3 0", "M3.5 3.5l13 13")
    val Settings = strokeIcon(
        "settings", "M3.5 6h8M15.5 6h1M3.5 14h2M9.5 14h7", circle(13.5f, 6f, 2f), circle(7.5f, 14f, 2f),
    )
    val Refresh = strokeIcon("refresh", "M16 10a6 6 0 1 1-1.8-4.3", "M15 3v3.5h-3.5")
    val Search = strokeIcon("search", circle(9f, 9f, 5f), "M13 13l3.5 3.5")
    val More = fillIcon("more", circle(5f, 10f, 1.4f), circle(10f, 10f, 1.4f), circle(15f, 10f, 1.4f))
    val Globe = strokeIcon(
        "globe", circle(10f, 10f, 7f), "M3 10h14", "M10 3c2 2.2 2.8 4.6 2.8 7s-.8 4.8-2.8 7c-2-2.2-2.8-4.6-2.8-7s.8-4.8 2.8-7z", width = 1.2f,
    )
    val Picture = strokeIcon(
        "picture", "M5 4h10a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", circle(7.5f, 8.5f, 1.5f), "M17 13l-4-4-7 7", width = 1.4f,
    )
    val Check = strokeIcon("check", "M4.5 10.5l3.5 3.5 7.5-8", width = 2f)
    val Grip = fillIcon(
        "grip", circle(7.5f, 5f, 1.3f), circle(12.5f, 5f, 1.3f), circle(7.5f, 10f, 1.3f), circle(12.5f, 10f, 1.3f),
        circle(7.5f, 15f, 1.3f), circle(12.5f, 15f, 1.3f),
    )
    val Pencil = strokeIcon("pencil", "M13.5 3.5l3 3L7 16H4v-3z")
    val Trash = strokeIcon("trash", "M4 6h12M8 6V4h4v2M6 6l.8 10h6.4L14 6")
    val Eraser = strokeIcon("eraser", "M8 16h8", "M11.5 4.5l4 4L9 15H6l-2-2 7.5-8.5z")
    val Code = strokeIcon("code", "M7 6l-4 4 4 4M13 6l4 4-4 4")
    val Close = strokeIcon("close", "M5.5 5.5l9 9M14.5 5.5l-9 9")
    // A priority contact (PriorityContact): outlined to mark one, filled once it is.
    val Star = strokeIcon("star", "M10 2.8l2.2 4.6 5 .7-3.6 3.5.9 5-4.5-2.4-4.5 2.4.9-5L2.8 8.1l5-.7z")
    val StarFilled = fillIcon("star-filled", "M10 2.8l2.2 4.6 5 .7-3.6 3.5.9 5-4.5-2.4-4.5 2.4.9-5L2.8 8.1l5-.7z")
    val Clock = strokeIcon("clock", circle(10f, 10f, 7f), "M10 6v4l2.6 1.8")
    val Info = strokeIcon("info", circle(10f, 10f, 7f), "M10 9.2v4.3", "M10 6.6h.01")
    val ArrowUp = strokeIcon("arrow-up", "M10 16V4.5", "M5 9.5l5-5 5 5", width = 2f)
}

/** [icon] at [size], in [tint]. */
@Composable
fun KashIcon(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Image(rememberVectorPainter(icon), contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}
