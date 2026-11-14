package app.kash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

/** A background of [color] while the pointer is over the element: the design's `:hover` rules. */
@Composable
fun Modifier.hoverBackground(color: Color, shape: Shape, interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }): Modifier {
    val hovered by interactionSource.collectIsHoveredAsState()
    return this.hoverable(interactionSource).background(if (hovered) color else Color.Transparent, shape)
}

/** A dashed border, which Compose's `border` does not draw: the design's `+` tile. */
fun Modifier.dashedBorder(color: Color, width: Dp, radius: Dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/** A square button that shows only [icon], with [label] for accessibility and as a tooltip: the design's `.ib` buttons. */
@Composable
fun IconOnlyButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    iconSize: Dp = 16.dp,
    tint: Color = Kash.colors.muted,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(if (size >= 44.dp) 6.dp else 4.dp)
    Tooltip({ Text(label) }) {
        Box(
            modifier.size(size)
                .background(if (active) Kash.colors.hover else Color.Transparent, shape)
                .hoverBackground(Kash.colors.hover, shape)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            KashIcon(icon, if (enabled) tint else tint.copy(alpha = 0.4f), size = iconSize)
        }
    }
}

/** The red badge of an unread count, with a ring of [ring] (the rail's color) that separates it from the tile it overlaps. */
@Composable
fun UnreadBadge(text: String, ring: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.defaultMinSize(minWidth = 20.dp).height(20.dp)
            .background(Kash.colors.badge, RoundedCornerShape(10.dp))
            .border(2.dp, ring, RoundedCornerShape(10.dp))
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp, maxLines = 1)
    }
}

/** The blue count of a row of the Unread tab. */
@Composable
fun CountPill(text: String) {
    Box(
        Modifier.defaultMinSize(minWidth = 24.dp).height(22.dp).background(Kash.colors.count, RoundedCornerShape(11.dp)).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A grey chip: the title bar's "Do not disturb", a service's unread count in its header. */
@Composable
fun Chip(text: String, height: Dp = 20.dp) {
    Box(Modifier.height(height).background(Kash.colors.chip, RoundedCornerShape(height / 2)).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** A section of a settings page or of a dialog: its title, followed by a line (`<h3>` + rule in the design). */
@Composable
fun SectionTitle(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Box(Modifier.weight(1f).height(1.dp).background(Kash.colors.line))
    }
}

/** A field's label above it, in the design's small muted text. */
@Composable
fun FieldLabel(text: String) {
    Text(text, fontSize = 12.sp, color = Kash.colors.muted)
}

/** A hint under a field or a checkbox. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 12.sp, color = Kash.colors.muted, modifier = modifier)
}

/** An empty space of [width] × [height]. */
@Composable
fun Gap(width: Dp = 0.dp, height: Dp = 0.dp) {
    Box(Modifier.width(width).height(height))
}
