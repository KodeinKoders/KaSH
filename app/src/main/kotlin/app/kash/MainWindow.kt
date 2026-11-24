package app.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kash.core.DoNotDisturb
import app.kash.core.Inbox
import app.kash.core.RailSide
import app.kash.core.Selection
import app.kash.core.ThemeMode
import app.kash.ui.Chip
import app.kash.ui.Kash
import app.kash.ui.KashIcon
import app.kash.ui.KashIcons
import app.kash.ui.MenuAnchor
import app.kash.ui.MenuState
import app.kash.ui.WindowMenu
import app.kash.ui.menuAnchor
import app.kash.ui.hoverBackground
import app.kash.ui.strings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar

/** The main window's content: the title bar, the rail of the services, and the Unread tab or the selected service. */
@Composable
fun DecoratedWindowScope.MainWindowContent(model: KashModel, icons: ServiceIcons, systemDark: Boolean) {
    val state = model.state
    val settings = state.settings
    val enabled = state.services.filter { it.enabled }
    val unread = model.unread
    val badges = Inbox.badges(state.services, unread, settings)
    val rows = Inbox.rows(state.services, unread, settings)
    val selected = (state.selection as? Selection.OfService)?.let { selection -> enabled.firstOrNull { it.id == selection.id } }

    val selectedTab = selected?.let { model.loaded[it.id]?.tab }
    TitleBar {
        // A service's header is here, so that its page has the whole height of the window: its tile, title, address and count on the left, its
        // buttons on the right. The Unread tab has its title there too. The search's field is in the middle.
        if (selected == null) {
            Text(strings.inbox, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Start).padding(start = 8.dp))
        } else {
            key(selected.id) { ServiceTitle(selected, selectedTab, badges[selected.id] ?: 0, icons, Modifier.align(Alignment.Start).padding(start = 8.dp)) }
        }
        Row(
            Modifier.align(Alignment.End).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected != null) {
                key(selected.id) {
                    ServiceButtons(
                        selected, selectedTab, onToggleMute = { model.toggleMute(selected.id) }, actions = serviceActions(model, selected.id),
                        update = selected.plugin?.let(model.pluginUpdates::get),
                        updating = selected.plugin in model.updatingPlugins,
                    )
                }
                Box(Modifier.width(1.dp).height(16.dp).background(Kash.colors.divider))
            }
            // The switch, or a range of the settings (until when it ends).
            if (model.doNotDisturb) Chip(model.doNotDisturbUntil?.let { strings.doNotDisturbUntil(untilLabel(it)) } ?: strings.doNotDisturb, height = 22.dp)
            ThemeMenu(settings.theme, systemDark) { mode -> model.updateSettings { it.copy(theme = mode) } }
        }
        // Last: Jewel's title bar measures its children in their order and leaves out those that do not fit, and in a narrow window the field is
        // what goes (⌘⇧K still opens the search), not the service's buttons.
        SearchField(onClick = model::toggleSearch, modifier = Modifier.align(Alignment.CenterHorizontally))
    }

    val rail = RailModel(
        services = enabled,
        selection = if (selected == null) Selection.Inbox else state.selection,
        badges = badges,
        // The topics are not counted (Inbox.sections).
        inboxCount = Inbox.sections(rows, model.priorities).count,
        showBadges = settings.showBadges,
        showLabels = settings.showLabels,
        doNotDisturb = model.doNotDisturb,
        doNotDisturbLabel = model.doNotDisturbUntil?.let { strings.doNotDisturbUntil(untilLabel(it)) } ?: strings.doNotDisturb,
        onSelect = model::select,
        onAdd = { model.editing = null; model.addServiceOpen = true },
        onToggleDoNotDisturb = model::toggleDoNotDisturb,
        onSettings = { model.settingsOpen = true },
        settingsDot = model.pluginUpdates.isNotEmpty(),
        updates = enabled.filter { it.plugin in model.pluginUpdates }.map { it.id }.toSet(),
        // Its own mute, its own ranges, or its notifications turned off in its Edit window: not KaSH's Do Not Disturb, which is all of them.
        quiet = enabled.filter { !it.desktopNotifications || DoNotDisturb.isQuiet(it, model.now) }.map { it.id }.toSet(),
        onMove = model::moveNextTo,
        contextMenu = { service, close ->
            val update = service.plugin?.takeIf { it !in model.updatingPlugins }?.let(model.pluginUpdates::get)
            serviceContextMenu(service, model.loaded[service.id]?.tab, serviceActions(model, service.id), update, close)
        },
    )
    val metrics = RailMetrics.of(settings.iconSize)
    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(Kash.colors.background)) {
            // Always there, without a size while the Unread tab is shown: a new panel for each page made it resized each time it was shown.
            ServicePage(selectedTab, if (selected == null) Modifier.size(0.dp) else Modifier.fillMaxSize())
            if (selected == null) {
                InboxScreen(rows, settings.inboxSort, { sort -> model.updateSettings { it.copy(inboxSort = sort) } }, icons, onRefresh = { model.refresh() }, onOpen = { row -> model.openConversation(row.service, row.conversation) },
                    actions = RowActions(
                        markReadOf = { row -> model.markReadOf(row.service.id) },
                        onMarkRead = { row -> row.conversation?.let { model.markRead(row.service.id, it) } },
                        isPriority = { row -> model.isPriority(row.service.id, row.conversation) },
                        onTogglePriority = { row -> row.conversation?.let { model.togglePriority(row.service.id, it, row.name) } },
                    ),
                    priorities = model.priorities,
                )
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Kash.colors.background)) {
        when (settings.railSide) {
            RailSide.LEFT -> Row(Modifier.fillMaxSize()) { ServiceRail(rail, icons, RailSide.LEFT, metrics); Box(Modifier.weight(1f)) { content() } }
            RailSide.RIGHT -> Row(Modifier.fillMaxSize()) { Box(Modifier.weight(1f)) { content() }; ServiceRail(rail, icons, RailSide.RIGHT, metrics) }
            RailSide.TOP -> Column(Modifier.fillMaxSize()) { ServiceRail(rail, icons, RailSide.TOP, metrics); Box(Modifier.weight(1f)) { content() } }
            RailSide.BOTTOM -> Column(Modifier.fillMaxSize()) { Box(Modifier.weight(1f)) { content() }; ServiceRail(rail, icons, RailSide.BOTTOM, metrics) }
        }
    }
}

/** What a service's menu does, wherever it is (its header, Settings › Services). */
fun serviceActions(model: KashModel, id: app.kash.core.ServiceId) = ServiceActions(
    onUpdatePlugin = { model.service(id)?.plugin?.let(model::startPluginUpdate) },
    onBack = { model.loaded[id]?.tab?.back() },
    onForward = { model.loaded[id]?.tab?.forward() },
    onReload = { model.loaded[id]?.tab?.reload() },
    onToggleMute = { model.toggleMute(id) },
    onDisable = { model.service(id)?.let { model.replace(it.copy(enabled = false)) } },
    onDevTools = { model.loaded[id]?.tab?.openDevTools() },
    onEdit = { model.editing = id; model.addServiceOpen = true },
    onResetCache = { model.resetCache(id) },
    onErase = { model.service(id)?.let { model.confirmation = Confirmation.Erase(it) } },
    onDelete = { model.service(id)?.let { model.confirmation = Confirmation.Delete(it) } },
    zoom = { model.zoomOf(id) },
    onZoomIn = { model.zoomIn(id) },
    onZoomOut = { model.zoomOut(id) },
    onZoomReset = { model.resetZoom(id) },
)

/** The theme button of the title bar: the current mode's icon, and a menu of the three modes, the system's current one as a hint. */
@Composable
private fun ThemeMenu(mode: ThemeMode, systemDark: Boolean, onPick: (ThemeMode) -> Unit) {
    val menu = remember { MenuState() }
    val names = mapOf(ThemeMode.LIGHT to strings.light, ThemeMode.DARK to strings.dark, ThemeMode.SYSTEM to strings.system)
    val label = strings.themeButton(names.getValue(mode))
    val anchor = remember { MenuAnchor() }
    Box(Modifier.menuAnchor(anchor)) {
        Row(
            Modifier.heightIn(min = 30.dp)
                .background(if (menu.open) Kash.colors.hover else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(4.dp))
                .hoverBackground(Kash.colors.hover, RoundedCornerShape(4.dp))
                .clickable { menu.toggle() }
                .semantics { contentDescription = label }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            KashIcon(modeIcon(mode), Kash.colors.muted)
            KashIcon(KashIcons.ChevronDown, Kash.colors.muted, size = 10.dp)
        }
    }
    if (menu.open) {
        WindowMenu(anchor, width = 200.dp, alignEnd = true, onDismiss = menu::dismiss) {
            passiveItem { Text(strings.theme, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Kash.colors.muted) }
            for (option in ThemeMode.entries) {
                selectableItem(selected = option == mode, onClick = { menu.dismiss(); onPick(option) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KashIcon(modeIcon(option), Kash.colors.muted)
                        Text(names.getValue(option), modifier = Modifier.weight(1f))
                        if (option == ThemeMode.SYSTEM) {
                            Text(if (systemDark) strings.dark else strings.light, fontSize = 11.sp, color = Kash.colors.muted)
                        }
                        Box(Modifier.width(16.dp)) { if (option == mode) KashIcon(KashIcons.Check, Kash.colors.text, size = 14.dp) }
                    }
                }
            }
        }
    }
}

private fun modeIcon(mode: ThemeMode) = when (mode) {
    ThemeMode.LIGHT -> KashIcons.Sun
    ThemeMode.DARK -> KashIcons.Moon
    ThemeMode.SYSTEM -> KashIcons.Monitor
}

/** When a range of Do Not Disturb ends, as the Unread tab writes a time: the time today, the day and the time another day. */
@Composable
private fun untilLabel(end: java.time.LocalDateTime): String =
    strings.timeLabel.of(end.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
