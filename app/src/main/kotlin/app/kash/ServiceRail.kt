package app.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kash.core.IconSize
import app.kash.core.RailSide
import app.kash.core.Selection
import app.kash.core.Service
import app.kash.core.ServiceId
import app.kash.core.UnreadCount
import app.kash.ui.Kash
import app.kash.ui.KashIcon
import app.kash.ui.KashIcons
import app.kash.ui.IconOnlyButton
import app.kash.ui.UnreadBadge
import app.kash.ui.dashedBorder
import app.kash.ui.strings
import org.jetbrains.jewel.ui.component.Text

/** The size of the tiles, and of the rail across them (its width when it is vertical). */
class RailMetrics(val tile: Dp, val rail: Dp) {
    companion object {
        /** Medium is the main window of the design: 40 dp tiles in a 72 dp rail. */
        fun of(size: IconSize) = when (size) {
            IconSize.SMALL -> RailMetrics(32.dp, 60.dp)
            IconSize.MEDIUM -> RailMetrics(40.dp, 72.dp)
            IconSize.LARGE -> RailMetrics(48.dp, 84.dp)
        }
    }
}

/** What the rail shows of each service and of the Unread tab, and what its buttons do. */
class RailModel(
    val services: List<Service>,
    val selection: Selection,
    val badges: Map<ServiceId, Int>,
    val inboxCount: Int,
    val showBadges: Boolean,
    val showLabels: Boolean,
    val doNotDisturb: Boolean,
    val onSelect: (Selection) -> Unit,
    val onAdd: () -> Unit,
    val onToggleDoNotDisturb: () -> Unit,
    val onSettings: () -> Unit,
)

/**
 * The rail of the services, on [side] of the window: the Unread tab, a tile for each enabled service, the `+` tile, then Do Not Disturb and the
 * settings at its end.
 */
@Composable
fun ServiceRail(model: RailModel, icons: ServiceIcons, side: RailSide, metrics: RailMetrics) {
    val vertical = side == RailSide.LEFT || side == RailSide.RIGHT
    val tiles: @Composable () -> Unit = {
        RailItem(
            selected = model.selection == Selection.Inbox,
            label = strings.inbox,
            badge = if (model.showBadges && model.inboxCount > 0) UnreadCount.badge(model.inboxCount) else null,
            description = strings.inboxTileLabel(model.inboxCount),
            side = side,
            metrics = metrics,
            showLabel = true,
            onClick = { model.onSelect(Selection.Inbox) },
        ) {
            Box(Modifier.size(metrics.tile).background(Kash.colors.neutral, RoundedCornerShape(metrics.tile / 4)), contentAlignment = Alignment.Center) {
                KashIcon(KashIcons.Inbox, Kash.colors.text, size = metrics.tile * 0.55f)
            }
        }
        Box(
            if (vertical) Modifier.padding(vertical = 4.dp).width(32.dp).height(1.dp) else Modifier.padding(horizontal = 4.dp).height(32.dp).width(1.dp),
        ) { Box(Modifier.matchParentSize().background(Kash.colors.divider)) }
        for (service in model.services) {
            val count = model.badges[service.id] ?: 0
            RailItem(
                selected = model.selection == Selection.OfService(service.id),
                label = service.label,
                badge = if (count > 0) UnreadCount.badge(count) else null,
                description = strings.serviceTileLabel(service.title, count),
                side = side,
                metrics = metrics,
                showLabel = model.showLabels,
                onClick = { model.onSelect(Selection.OfService(service.id)) },
            ) {
                ServiceTile(service, icons, metrics.tile)
            }
        }
        val addLabel = strings.addService
        Box(
            Modifier.padding(6.dp).size(metrics.tile)
                .dashedBorder(Kash.colors.dashed, 1.dp, metrics.tile / 4)
                .clickable(onClick = model.onAdd)
                .semantics {
                    contentDescription = addLabel
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) { KashIcon(KashIcons.Plus, Kash.colors.muted, size = 18.dp) }
    }
    val buttons: @Composable () -> Unit = {
        IconOnlyButton(
            if (model.doNotDisturb) KashIcons.BellOff else KashIcons.Bell, strings.doNotDisturb, model.onToggleDoNotDisturb,
            size = 44.dp, iconSize = 20.dp, tint = if (model.doNotDisturb) Kash.colors.text else Kash.colors.muted,
        )
        IconOnlyButton(KashIcons.Settings, strings.settings, model.onSettings, size = 44.dp, iconSize = 20.dp)
    }
    if (vertical) {
        val edge = if (side == RailSide.LEFT) Modifier.padding(end = 1.dp) else Modifier.padding(start = 1.dp)
        Box(Modifier.width(metrics.rail).fillMaxHeight().background(Kash.colors.separator)) {
            Column(
                Modifier.matchParentSize().then(edge).background(Kash.colors.panel).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) { tiles() }
                buttons()
            }
        }
    } else {
        val edge = if (side == RailSide.TOP) Modifier.padding(bottom = 1.dp) else Modifier.padding(top = 1.dp)
        Box(Modifier.fillMaxWidth().background(Kash.colors.separator)) {
            Row(
                Modifier.fillMaxWidth().then(edge).background(Kash.colors.panel).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) { tiles() }
                buttons()
            }
        }
    }
}

/**
 * A tile of the rail: the selection's indicator on the edge next to the content, and its ring around the tile; the label under it; the unread
 * badge over its top right corner.
 */
@Composable
private fun RailItem(
    selected: Boolean,
    label: String,
    badge: String?,
    description: String,
    side: RailSide,
    metrics: RailMetrics,
    showLabel: Boolean,
    onClick: () -> Unit,
    tile: @Composable () -> Unit,
) {
    val vertical = side == RailSide.LEFT || side == RailSide.RIGHT
    val ringed = metrics.tile + 8.dp
    Box(
        (if (vertical) Modifier.width(metrics.rail) else Modifier.padding(horizontal = 6.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Tab
                this.selected = selected
            }
            .padding(vertical = 2.dp),
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Box(Modifier.size(ringed)) {
                Box(
                    Modifier.matchParentSize().then(
                        if (selected) Modifier.border(2.dp, Kash.colors.accent, RoundedCornerShape(metrics.tile / 4 + 4.dp)) else Modifier,
                    ),
                    contentAlignment = Alignment.Center,
                ) { tile() }
                if (badge != null) {
                    // Over the tile's top right corner, as in the design: 14 dp inside its right edge, 4 dp above its top.
                    UnreadBadge(badge, Kash.colors.panel, Modifier.offset(x = 4.dp + metrics.tile - 14.dp, y = 0.dp))
                }
            }
            if (showLabel && label.isNotBlank()) {
                Text(
                    label,
                    modifier = Modifier.widthIn(max = 64.dp),
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) Kash.colors.text else Kash.colors.muted,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        if (selected) {
            val bar = when (side) {
                RailSide.LEFT -> Modifier.align(Alignment.TopStart).padding(top = 10.dp).width(3.dp).height(28.dp)
                    .background(Kash.colors.accent, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                RailSide.RIGHT -> Modifier.align(Alignment.TopEnd).padding(top = 10.dp).width(3.dp).height(28.dp)
                    .background(Kash.colors.accent, RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp))
                RailSide.TOP -> Modifier.align(Alignment.BottomCenter).width(28.dp).height(3.dp)
                    .background(Kash.colors.accent, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                RailSide.BOTTOM -> Modifier.align(Alignment.TopCenter).width(28.dp).height(3.dp)
                    .background(Kash.colors.accent, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
            }
            Box(bar)
        }
    }
}
