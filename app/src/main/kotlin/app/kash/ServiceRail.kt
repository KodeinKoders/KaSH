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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import app.kash.ui.MenuAnchor
import app.kash.ui.ReorderState
import app.kash.ui.reorderable
import app.kash.ui.WindowMenu
import app.kash.ui.dashedBorder
import org.jetbrains.jewel.ui.component.MenuScope
import java.awt.MouseInfo
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
    /** Whether a dot on the settings' button says that something waits there: a plugin's new version. */
    val settingsDot: Boolean = false,
    /** The services whose plugin has a new version: an arrow at the bottom left of their tiles. */
    val updates: Set<ServiceId> = emptySet(),
    /** Moves a service past its neighbor in the rail (a tile dragged over it): see [KashModel.moveNextTo]. */
    val onMove: (ServiceId, ServiceId) -> Unit,
    /** The items of a service's context menu (a right click on its tile), which the function given closes. */
    val contextMenu: MenuScope.(Service, () -> Unit) -> Unit,
)

/**
 * The rail of the services, on [side] of the window: the Unread tab, a tile for each enabled service, the `+` tile, then Do Not Disturb and the
 * settings at its end.
 */
@Composable
fun ServiceRail(model: RailModel, icons: ServiceIcons, side: RailSide, metrics: RailMetrics) {
    val vertical = side == RailSide.LEFT || side == RailSide.RIGHT
    // The service whose context menu is open, and where it opens (where the click was).
    var menu by remember { mutableStateOf<Pair<ServiceId, MenuAnchor>?>(null) }
    // The tiles are dragged to change the services' order.
    val reorder = remember { ReorderState<ServiceId>() }
    val ids = model.services.map { it.id }
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
            // Its own place in the composition, which moves with it: its drag goes on when it takes another tile's place.
            key(service.id) {
                val count = model.badges[service.id] ?: 0
                RailItem(
                    modifier = Modifier.reorderable(reorder, service.id, vertical, RAIL_SPACING, ids, model.onMove),
                    selected = model.selection == Selection.OfService(service.id),
                    label = service.label,
                    badge = if (count > 0) UnreadCount.badge(count) else null,
                    update = service.id in model.updates,
                    description = strings.serviceTileLabel(service.title, count),
                    side = side,
                    metrics = metrics,
                    showLabel = model.showLabels,
                    onClick = { model.onSelect(Selection.OfService(service.id)) },
                    onContextMenu = { menu = service.id to pointerAnchor() },
                ) {
                    ServiceTile(service, icons, metrics.tile)
                }
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
        Box {
            IconOnlyButton(KashIcons.Settings, strings.settings, model.onSettings, size = 44.dp, iconSize = 20.dp)
            if (model.settingsDot) Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(7.dp).background(Kash.colors.accent, CircleShape))
        }
    }
    menu?.let { (id, anchor) ->
        val service = model.services.firstOrNull { it.id == id }
        if (service == null) {
            menu = null
        } else {
            val close = { menu = null }
            // Wide enough for the longest item in French ("Réactiver les notifications", "Effacer toutes les données…").
            WindowMenu(anchor, width = 260.dp, alignEnd = false, onDismiss = close) { model.contextMenu(this, service, close) }
        }
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
                    verticalArrangement = Arrangement.spacedBy(RAIL_SPACING),
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
                    horizontalArrangement = Arrangement.spacedBy(RAIL_SPACING),
                ) { tiles() }
                buttons()
            }
        }
    }
}

// Between the tiles of the rail.
private val RAIL_SPACING = 2.dp

/** Where the pointer is on the screen, for a context menu that opens there. */
internal fun pointerAnchor(): MenuAnchor {
    val location = MouseInfo.getPointerInfo()?.location
    return MenuAnchor().apply {
        left = location?.x?.toFloat() ?: 0f
        right = left
        bottom = location?.y?.toFloat() ?: 0f
    }
}

/**
 * A right click, or a click with Control (macOS's right click on a trackpad without it), which runs [onContextClick] instead of the click of
 * the element.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.onContextClick(onContextClick: () -> Unit): Modifier = onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { event ->
    val buttons = event.buttons
    if (buttons.isSecondaryPressed || (buttons.isPrimaryPressed && event.keyboardModifiers.isCtrlPressed)) {
        event.changes.forEach { it.consume() }
        onContextClick()
    }
}

/**
 * A tile of the rail: the selection's indicator on the edge next to the content, and its ring around the tile; the label under it; the unread
 * badge over its top right corner, and an arrow over its bottom left one when its plugin has a new version ([update]).
 */
@Composable
private fun RailItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    label: String,
    badge: String?,
    update: Boolean = false,
    description: String,
    side: RailSide,
    metrics: RailMetrics,
    showLabel: Boolean,
    onClick: () -> Unit,
    onContextMenu: (() -> Unit)? = null,
    tile: @Composable () -> Unit,
) {
    val vertical = side == RailSide.LEFT || side == RailSide.RIGHT
    val ringed = metrics.tile + 8.dp
    Box(
        modifier.then(if (vertical) Modifier.width(metrics.rail) else Modifier.padding(horizontal = 6.dp))
            .then(if (onContextMenu != null) Modifier.onContextClick(onContextMenu) else Modifier)
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
                if (update) {
                    // Over the tile's bottom left corner, as the badge over its top right one: 4 dp out of the tile.
                    Box(
                        Modifier.align(Alignment.BottomStart).size(16.dp)
                            .background(Kash.colors.accent, CircleShape)
                            .border(2.dp, Kash.colors.panel, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { KashIcon(KashIcons.ArrowUp, Color.White, size = 9.dp) }
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
