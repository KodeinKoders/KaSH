package app.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberWindowState
import app.kash.core.DockBadge
import app.kash.core.IconSize
import app.kash.core.InboxSort
import app.kash.core.Language
import app.kash.core.RailSide
import app.kash.core.Service
import app.kash.core.ServiceId
import app.kash.core.Settings
import app.kash.core.ThemeMode
import app.kash.core.UnreadCount
import app.kash.macos.LoginItemStatus
import app.kash.macos.MacosLoginItem
import app.kash.macos.inAppBundle
import app.kash.ui.Hint
import app.kash.ui.Kash
import app.kash.ui.LocalAwtWindow
import app.kash.ui.KashIcon
import app.kash.ui.KashIcons
import app.kash.ui.SectionTitle
import app.kash.ui.Strings
import app.kash.ui.UnreadBadge
import app.kash.ui.hoverBackground
import app.kash.ui.strings
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import kotlin.math.roundToInt

private enum class Page { GENERAL, APPEARANCE, NOTIFICATIONS, INBOX, SERVICES }

/** The title of [page], and the texts of its settings, which the search of the navigation looks into. */
private fun Strings.page(page: Page): Pair<String, List<String>> = when (page) {
    Page.GENERAL -> general to listOf(startup, launchAtLogin, language, interfaceLanguage)
    Page.APPEARANCE -> appearance to listOf(theme, light, dark, system, sidebar, iconSize, position, showLabels, showBadges)
    Page.NOTIFICATIONS -> notifications to listOf(alerts, desktopNotifications, playSound, dockBadge, dockTotal, dockDot, dockNone, doNotDisturb)
    Page.INBOX -> inboxTab to listOf(display, defaultSort, openInboxOnLaunch, content, skipMuted, directOnly)
    Page.SERVICES -> services to listOf(enabled, edit, resetCache, eraseData, delete, addService)
}

/**
 * The settings window. What it changes is a draft until Apply or OK; the actions of a service's menu (reset the cache, erase, delete) are done
 * at once, after their confirmation.
 */
@Composable
fun SettingsWindow(model: KashModel, icons: ServiceIcons, onClose: () -> Unit) {
    val title = strings.settings
    DecoratedWindow(onCloseRequest = onClose, state = rememberWindowState(size = DpSize(960.dp, 720.dp)), title = title, icon = remember { AppIcon.painter() }) {
        TitleBar { Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.CenterHorizontally)) }
        CompositionLocalProvider(LocalAwtWindow provides window) {
            SettingsContent(model, icons, onClose)
        }
    }
}

@Composable
private fun SettingsContent(model: KashModel, icons: ServiceIcons, onClose: () -> Unit) {
    var page by remember { mutableStateOf(Page.APPEARANCE) }
    var draft by remember { mutableStateOf(model.settings) }
    // The services' draft: their order and whether they are enabled, applied to the services as they are then (one may be edited meanwhile).
    var order by remember { mutableStateOf(model.services.map { it.id }) }
    var enabled by remember { mutableStateOf(model.services.associate { it.id to it.enabled }) }
    val services = (order.mapNotNull { id -> model.service(id) } + model.services.filter { it.id !in order })
        .map { it.copy(enabled = enabled[it.id] ?: it.enabled) }
    val dirty = draft != model.settings || services != model.services
    val apply = {
        if (draft.launchAtLogin != model.settings.launchAtLogin && inAppBundle) MacosLoginItem.set(draft.launchAtLogin)
        model.updateSettings { draft }
        model.applyServices(services)
    }
    val query = rememberTextFieldState()
    val q = query.text.toString().trim().lowercase()
    val s = strings

    Column(Modifier.fillMaxSize().background(Kash.colors.panel)) {
        Row(Modifier.weight(1f)) {
            Column(
                Modifier.width(220.dp).fillMaxHeight().background(Kash.colors.panel).padding(horizontal = 8.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextField(
                    state = query,
                    modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 8.dp).semantics { contentDescription = s.searchSetting },
                    placeholder = { Text(s.search, color = Kash.colors.faint) },
                    leadingIcon = { KashIcon(KashIcons.Search, Kash.colors.muted, Modifier.padding(end = 6.dp), size = 14.dp) },
                )
                for (p in Page.entries) {
                    val (name, texts) = s.page(p)
                    if (q.isNotEmpty() && (listOf(name) + texts).none { it.lowercase().contains(q) }) continue
                    val shape = RoundedCornerShape(4.dp)
                    Box(
                        Modifier.fillMaxWidth().height(30.dp)
                            .background(if (p == page) Kash.colors.selection else androidx.compose.ui.graphics.Color.Transparent, shape)
                            .hoverBackground(Kash.colors.hover, shape)
                            .clickable { page = p }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { Text(name, fontWeight = if (p == page) FontWeight.Medium else FontWeight.Normal) }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Kash.colors.separator))
            Column(Modifier.weight(1f).fillMaxHeight().background(Kash.colors.background)) {
                Text(s.page(page).first, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp))
                VerticallyScrollableContainer(Modifier.weight(1f), scrollState = rememberScrollState()) {
                    Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        when (page) {
                            Page.GENERAL -> GeneralPage(draft) { draft = it }
                            Page.APPEARANCE -> AppearancePage(draft, services, model, icons) { draft = it }
                            Page.NOTIFICATIONS -> NotificationsPage(draft) { draft = it }
                            Page.INBOX -> InboxPage(draft) { draft = it }
                            Page.SERVICES -> ServicesPage(
                                services, model, icons,
                                onToggle = { id, on -> enabled = enabled + (id to on) },
                                onMove = { from, to -> order = services.map { it.id }.toMutableList().apply { add(to, removeAt(from)) } },
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Kash.colors.separator))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            OutlinedButton(onClick = onClose) { Text(s.cancel) }
            OutlinedButton(onClick = apply, enabled = dirty) { Text(s.apply) }
            DefaultButton(onClick = { apply(); onClose() }) { Text(s.ok) }
        }
    }
}

@Composable
private fun <T> Segments(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    SegmentedControl(options.map { (value, label) -> SegmentedControlButtonData(value == selected, { Text(label) }, { onPick(value) }) })
}

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.width(160.dp))
        content()
    }
}

@Composable
private fun GeneralPage(draft: Settings, onChange: (Settings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(strings.startup)
        CheckboxRow(strings.launchAtLogin, draft.launchAtLogin, { onChange(draft.copy(launchAtLogin = it)) }, enabled = inAppBundle)
        when {
            !inAppBundle -> Hint(strings.launchAtLoginUnavailable, Modifier.padding(start = 24.dp))
            MacosLoginItem.status() == LoginItemStatus.REQUIRES_APPROVAL -> Hint(strings.launchAtLoginApproval, Modifier.padding(start = 24.dp))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(strings.language)
        LabeledRow(strings.interfaceLanguage) {
            val languages = listOf(Language.SYSTEM to strings.systemLanguage, Language.FRENCH to "Français", Language.ENGLISH to "English")
            ListComboBox(
                items = languages.map { it.second },
                selectedIndex = languages.indexOfFirst { it.first == draft.language },
                onSelectedItemChange = { onChange(draft.copy(language = languages[it].first)) },
                modifier = Modifier.width(200.dp),
            )
        }
    }
}

@Composable
private fun AppearancePage(draft: Settings, services: List<Service>, model: KashModel, icons: ServiceIcons, onChange: (Settings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(strings.theme)
        Segments(
            listOf(ThemeMode.LIGHT to strings.light, ThemeMode.DARK to strings.dark, ThemeMode.SYSTEM to strings.system),
            draft.theme,
        ) { onChange(draft.copy(theme = it)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(strings.sidebar)
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledRow(strings.iconSize) {
                    Segments(listOf(IconSize.SMALL to strings.small, IconSize.MEDIUM to strings.medium, IconSize.LARGE to strings.large), draft.iconSize) {
                        onChange(draft.copy(iconSize = it))
                    }
                }
                LabeledRow(strings.position) {
                    val sides = listOf(RailSide.LEFT to strings.left, RailSide.RIGHT to strings.right, RailSide.TOP to strings.top, RailSide.BOTTOM to strings.bottom)
                    ListComboBox(
                        items = sides.map { it.second },
                        selectedIndex = sides.indexOfFirst { it.first == draft.railSide },
                        onSelectedItemChange = { onChange(draft.copy(railSide = sides[it].first)) },
                        modifier = Modifier.width(160.dp),
                    )
                }
                CheckboxRow(strings.showLabels, draft.showLabels, { onChange(draft.copy(showLabels = it)) })
                CheckboxRow(strings.showBadges, draft.showBadges, { onChange(draft.copy(showBadges = it)) })
            }
            RailPreview(draft, services.filter { it.enabled }.take(3), model, icons)
        }
    }
}

/** The rail as the draft would draw it, with the first services and their badges. */
@Composable
private fun RailPreview(draft: Settings, services: List<Service>, model: KashModel, icons: ServiceIcons) {
    val metrics = RailMetrics.of(draft.iconSize)
    val badges = app.kash.core.Inbox.badges(services, model.unread, draft)
    val horizontal = draft.railSide == RailSide.TOP || draft.railSide == RailSide.BOTTOM
    val tiles: @Composable () -> Unit = {
        for (service in services) {
            Box(Modifier.padding(if (horizontal) 6.dp else 4.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    ServiceTile(service, icons, metrics.tile)
                    if (draft.showLabels && service.label.isNotBlank()) Text(service.label, fontSize = 10.sp, color = Kash.colors.muted, maxLines = 1)
                }
                val count = badges[service.id] ?: 0
                if (count > 0) UnreadBadge(UnreadCount.badge(count), Kash.colors.panel, Modifier.offset(x = metrics.tile - 14.dp, y = (-4).dp))
            }
        }
    }
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(80.dp).height(10.dp).background(Kash.colors.line, RoundedCornerShape(3.dp)))
            repeat(3) { Box(Modifier.width(120.dp).height(28.dp).background(Kash.colors.line, RoundedCornerShape(4.dp))) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(strings.preview, fontSize = 12.sp, color = Kash.colors.muted)
        Box(
            Modifier.size(260.dp, 220.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Kash.colors.line, RoundedCornerShape(8.dp))
                .background(Kash.colors.background),
        ) {
            val rail: @Composable () -> Unit = {
                if (horizontal) {
                    Row(Modifier.fillMaxWidth().background(Kash.colors.panel).padding(horizontal = 6.dp, vertical = 2.dp)) { tiles() }
                } else {
                    Column(Modifier.width(metrics.rail).fillMaxHeight().background(Kash.colors.panel).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) { tiles() }
                }
            }
            when (draft.railSide) {
                RailSide.LEFT -> Row { rail(); content() }
                RailSide.RIGHT -> Row { Box(Modifier.weight(1f)) { content() }; rail() }
                RailSide.TOP -> Column { rail(); content() }
                RailSide.BOTTOM -> Column { Box(Modifier.weight(1f)) { content() }; rail() }
            }
            if (services.isEmpty()) Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) { Hint(strings.noService, Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun NotificationsPage(draft: Settings, onChange: (Settings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(strings.alerts)
        CheckboxRow(strings.desktopNotifications, draft.notifications, { onChange(draft.copy(notifications = it)) })
        CheckboxRow(strings.playSound, draft.sound, { onChange(draft.copy(sound = it)) })
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(strings.dockBadge)
        for ((value, label) in listOf(DockBadge.TOTAL to strings.dockTotal, DockBadge.DOT to strings.dockDot, DockBadge.NONE to strings.dockNone)) {
            RadioButtonRow(label, draft.dockBadge == value, { onChange(draft.copy(dockBadge = value)) })
        }
    }
}

@Composable
private fun InboxPage(draft: Settings, onChange: (Settings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(strings.display)
        LabeledRow(strings.defaultSort) {
            Segments(listOf(InboxSort.BY_DATE to strings.byDate, InboxSort.BY_COUNT to strings.byCount, InboxSort.BY_SERVICE to strings.byService), draft.inboxSort) {
                onChange(draft.copy(inboxSort = it))
            }
        }
        CheckboxRow(strings.openInboxOnLaunch, draft.openInboxOnLaunch, { onChange(draft.copy(openInboxOnLaunch = it)) })
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(strings.content)
        CheckboxRow(strings.skipMuted, draft.skipMuted, { onChange(draft.copy(skipMuted = it)) })
        CheckboxRow(strings.directOnly, draft.directOnly, { onChange(draft.copy(directOnly = it)) })
        Hint(strings.directOnlyHint, Modifier.padding(start = 24.dp))
    }
}

@Composable
private fun ServicesPage(
    services: List<Service>,
    model: KashModel,
    icons: ServiceIcons,
    onToggle: (ServiceId, Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.servicesCount(services.size, services.count { it.enabled }), color = Kash.colors.muted, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { model.editing = null; model.addServiceOpen = true }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KashIcon(KashIcons.Plus, Kash.colors.text, size = 14.dp)
                    Text(strings.addService)
                }
            }
        }
        if (services.isEmpty()) {
            Hint(strings.noService)
            return@Column
        }
        val rowHeight = with(LocalDensity.current) { 49.dp.toPx() }
        var dragged by remember { mutableStateOf<ServiceId?>(null) }
        var offset by remember { mutableFloatStateOf(0f) }
        val shape = RoundedCornerShape(8.dp)
        Column(Modifier.fillMaxWidth().background(Kash.colors.line, shape).border(1.dp, Kash.colors.line, shape), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            for ((index, service) in services.withIndex()) {
                val dragging = dragged == service.id
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .then(if (dragging) Modifier.offset { IntOffset(0, offset.roundToInt()) } else Modifier)
                        .background(Kash.colors.card)
                        .padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val currentIndex = index
                    Box(
                        Modifier.pointerInput(service.id) {
                            detectDragGestures(
                                onDragStart = { dragged = service.id; offset = 0f },
                                onDragEnd = { dragged = null; offset = 0f },
                                onDragCancel = { dragged = null; offset = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    offset += amount.y
                                    val position = services.indexOfFirst { it.id == service.id }.takeIf { it >= 0 } ?: currentIndex
                                    if (offset > rowHeight / 2 && position < services.lastIndex) {
                                        onMove(position, position + 1); offset -= rowHeight
                                    } else if (offset < -rowHeight / 2 && position > 0) {
                                        onMove(position, position - 1); offset += rowHeight
                                    }
                                },
                            )
                        }.semantics { contentDescription = "" },
                    ) {
                        org.jetbrains.jewel.ui.component.Tooltip({ Text(strings.dragToReorder) }) { KashIcon(KashIcons.Grip, Kash.colors.faint) }
                    }
                    ServiceTile(service, icons, 28.dp, if (service.enabled) Modifier else Modifier.alpha(0.45f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            service.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (service.enabled) Kash.colors.text else Kash.colors.muted,
                        )
                        if (model.cacheReset[service.id] == true) {
                            Text(strings.cacheReset, fontSize = 11.sp, color = Kash.colors.muted)
                        } else {
                            Text(displayAddress(service.url), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Kash.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    CheckboxRow(strings.enabled, service.enabled, { onToggle(service.id, it) })
                    ServiceMenuButton(service, serviceActions(model, service.id), devToolsEnabled = model.loaded[service.id] != null, buttonSize = 30.dp)
                }
            }
        }
    }
}
