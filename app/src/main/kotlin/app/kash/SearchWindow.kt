package app.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import app.kash.core.Search
import app.kash.core.SearchEntry
import app.kash.core.SearchMatch
import app.kash.core.SearchSection
import app.kash.core.UnreadCount
import app.kash.ui.CountPill
import app.kash.ui.UiScaled
import app.kash.ui.uiScaleFactor
import app.kash.ui.Kash
import app.kash.ui.KashIcon
import app.kash.ui.KashIcons
import app.kash.ui.KeyCap
import app.kash.ui.Shortcut
import app.kash.ui.hoverBackground
import app.kash.ui.strings
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.swing.RootPaneContainer

/** The shortcut of the search, as it is shown: `BrowserShortcut.Search` in a page, [isSearchShortcut] elsewhere. */
const val SEARCH_SHORTCUT = "⌘⇧K"

/** The search's shortcut that marks the selected line's contact as priority, or not any more ([isPriorityShortcut]). */
const val PRIORITY_SHORTCUT = "⌘D"

/** Whether [event] is ⌘D, which marks the selected line's contact as priority in the search, or not any more. */
fun isPriorityShortcut(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && event.key == Key.D && event.isMetaPressed && !event.isShiftPressed && !event.isAltPressed && !event.isCtrlPressed

/** Whether [event] is the search's shortcut, ⌘⇧K, in a window of KaSH (a page has its own: `BrowserShortcut.Search`). */
fun isSearchShortcut(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && event.key == Key.K && event.isMetaPressed && event.isShiftPressed && !event.isAltPressed && !event.isCtrlPressed

/**
 * The field of the title bar that opens the search, as KaTTY's opens its palette: it looks like a text field, and the pointer says so, although
 * what is typed goes in the search.
 */
@Composable
fun SearchField(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Kash.colors
    val label = strings.searchLabel
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier.width(260.dp * uiScaleFactor()).heightIn(min = 26.dp).clip(shape).background(colors.field).border(1.dp, colors.divider, shape)
            .semantics { contentDescription = label }
            .pointerHoverIcon(PointerIcon.Text)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KashIcon(KashIcons.Search, colors.muted, size = 14.dp)
        Text(strings.searchField, Modifier.weight(1f), color = colors.muted, maxLines = 1)
        Shortcut(SEARCH_SHORTCUT)
    }
}

/**
 * The search of the contacts and topics of every service, over the main window ([owner]): a window of its own, since a service's page is a native
 * view that is drawn over anything that Compose draws in the main window. It covers the main window with a veil, under which the card is, as
 * KaTTY's palette. It closes on a click on the veil, Escape, ⌘⇧K, when it loses the focus, and when a line is opened ([onOpen]).
 */
@Composable
fun SearchWindow(
    owner: java.awt.Window?, entries: List<SearchEntry>, icons: ServiceIcons, onOpen: (SearchEntry) -> Unit, onDismiss: () -> Unit,
    priority: PriorityActions = PriorityActions(),
) {
    val latest by rememberUpdatedState(onDismiss)
    // Where the main window's content is on the screen, in AWT's points, which are dp.
    val bounds = remember(owner) {
        val pane = (owner as? RootPaneContainer)?.contentPane
        val origin = pane?.let { runCatching { it.locationOnScreen }.getOrNull() }
        if (pane == null || origin == null) null else java.awt.Rectangle(origin.x, origin.y, pane.width, pane.height)
    }
    val state = rememberWindowState(
        size = if (bounds == null) DpSize(720.dp, 560.dp) else DpSize(bounds.width.dp, bounds.height.dp),
        position = if (bounds == null) WindowPosition(Alignment.Center) else WindowPosition(bounds.x.dp, bounds.y.dp),
    )
    Window(
        onCloseRequest = { latest() },
        state = state,
        title = strings.searchLabel,
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        onPreviewKeyEvent = { event ->
            if (isSearchShortcut(event)) {
                latest()
                true
            } else {
                false
            }
        },
    ) {
        // The size of the text that Settings › Appearance gives, inside the window (UiScaled).
        UiScaled {
            DisposableEffect(window) {
                val listener = object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent) = Unit
                    override fun windowLostFocus(e: WindowEvent) = latest()
                }
                window.addWindowFocusListener(listener)
                onDispose { window.removeWindowFocusListener(listener) }
            }
            SearchContent(entries, icons, onOpen, onDismiss, priority)
        }
    }
}

/** What the search shows, apart from its window: the field, the lines that match, and how to use them. */
@Composable
fun SearchContent(
    entries: List<SearchEntry>, icons: ServiceIcons, onOpen: (SearchEntry) -> Unit, onDismiss: () -> Unit, priority: PriorityActions = PriorityActions(),
) {
    val colors = Kash.colors
    val s = strings
    val query = rememberTextFieldState()
    var text by remember { mutableStateOf("") }
    LaunchedEffect(query) { snapshotFlow { query.text.toString() }.collect { text = it } }
    val matches = remember(entries, text) { Search.search(entries, text) }
    // The selection is kept by its line, since the lines may change while the search is open (a page reports), and is the first one otherwise.
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selected = matches.indexOfFirst { it.entry.key == selectedKey }.takeIf { it >= 0 } ?: 0
    val select = { index: Int -> selectedKey = matches.getOrNull(index)?.entry?.key }
    // A new query selects the first line again.
    LaunchedEffect(text) { selectedKey = null }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val listState = rememberLazyListState()

    SearchFrame(s.searchLabel, onDismiss) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KashIcon(KashIcons.Search, colors.muted, size = 18.dp)
            TextField(
                query,
                Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> { select(stepped(selected, 1, matches.size)); true }
                        Key.DirectionUp -> { select(stepped(selected, -1, matches.size)); true }
                        Key.Enter, Key.NumPadEnter -> { matches.getOrNull(selected)?.let { onOpen(it.entry) }; true }
                        Key.Escape -> { onDismiss(); true }
                        // The selected line's contact marked as priority, or not any more, without opening it (as a browser's ⌘D bookmarks).
                        Key.D -> if (isPriorityShortcut(event)) { matches.getOrNull(selected)?.let { priority.onToggle(it.entry) }; true } else false
                        else -> false
                    }
                },
                placeholder = { Text(s.searchPlaceholder, color = colors.faint) },
            )
            KeyCap("esc")
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 420.dp).padding(horizontal = 6.dp, vertical = 4.dp), state = listState) {
            if (matches.isEmpty()) {
                item {
                    Text(if (text.isBlank()) s.searchNothingYet else s.searchNoMatch(text.trim()), Modifier.padding(12.dp), color = colors.muted)
                }
            }
            itemsIndexed(matches, key = { _, match -> match.entry.key }) { index, match ->
                // The title of a section is in its first line, so that the list's indices are the lines'.
                if (index == 0 || matches[index - 1].entry.section != match.entry.section) {
                    Text(
                        if (match.entry.section == SearchSection.CONTACTS) s.searchContacts else s.searchTopics,
                        Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
                        color = colors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                SearchRow(match, selected == index, icons, priority) { onOpen(match.entry) }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 36.dp).background(colors.panel).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FooterHint(listOf("↑", "↓"), s.searchNavigate)
            FooterHint(listOf("⏎"), s.searchOpen)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Shortcut(PRIORITY_SHORTCUT)
                Text(s.searchPriority, color = colors.muted, fontSize = 12.sp, maxLines = 1)
            }
            Box(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Shortcut(SEARCH_SHORTCUT)
                Text(s.searchClose, color = colors.muted, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
    // The selected line stays in view, the first one too when the arrows go round the list.
    LaunchedEffect(selected, matches) {
        if (matches.isNotEmpty()) listState.animateScrollToItem(selected.coerceIn(0, matches.size - 1))
    }
}

// The index [delta] lines after [index], round a list of [size] lines.
private fun stepped(index: Int, delta: Int, size: Int): Int = if (size == 0) 0 else Math.floorMod(index + delta, size)

/** The veil over the main window and the card on it, at the top, as KaTTY's palette's. */
@Composable
private fun SearchFrame(description: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = Kash.colors
    val card = RoundedCornerShape(12.dp)
    Box(
        // The window's corners are rounded: the veil too.
        Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color(0xFF0E0F12).copy(alpha = .3f))
            .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.padding(top = 90.dp, bottom = 24.dp, start = 24.dp, end = 24.dp).widthIn(max = 640.dp).fillMaxWidth()
                .shadow(24.dp, card).clip(card).background(colors.card).border(1.dp, colors.divider, card)
                // A click on the card is not one on the veil.
                .clickable(interactionSource = null, indication = null) {}
                .semantics { contentDescription = description },
            content = content,
        )
    }
}

@Composable
private fun FooterHint(keys: List<String>, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { KeyCap(it) }
        Text(label, color = Kash.colors.muted, fontSize = 12.sp, maxLines = 1)
    }
}

/** A line of the search: its service's tile, its name with the letters that matched, its service, and what is unread in it. */
@Composable
private fun SearchRow(match: SearchMatch, selected: Boolean, icons: ServiceIcons, priority: PriorityActions, onClick: () -> Unit) {
    val colors = Kash.colors
    val entry = match.entry
    val shape = RoundedCornerShape(6.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 36.dp).clip(shape)
            .then(if (selected) Modifier.background(colors.selection).hoverable(interaction) else Modifier.hoverBackground(colors.hover, shape, interaction))
            .semantics { contentDescription = "${entry.name} ${entry.service.title}"; this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ServiceTile(entry.service, icons, 18.dp)
        Text(highlighted(entry.name, match.indices, colors.accent), Modifier.weight(1f, fill = false), color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(entry.service.title, Modifier.weight(1f), color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        when {
            entry.count > 0 -> CountPill(UnreadCount.badge(entry.count))
            entry.topic -> Box(Modifier.padding(horizontal = 8.dp).size(8.dp).background(colors.muted, CircleShape))
        }
        // Its star: filled for a priority contact, outlined while the line is pointed at or selected, which marks it; it does not open it.
        val isPriority = priority.isPriority(entry)
        if (isPriority || hovered || selected) {
            val label = if (isPriority) strings.unmarkPriority else strings.markPriority
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).hoverBackground(colors.hover, RoundedCornerShape(4.dp))
                    .semantics { contentDescription = label }
                    .clickable(role = Role.Button) { priority.onToggle(entry) },
                contentAlignment = Alignment.Center,
            ) { KashIcon(if (isPriority) KashIcons.StarFilled else KashIcons.Star, if (isPriority) colors.accent else colors.muted, size = 14.dp) }
        }
    }
}

/** Whether a line of the search is a priority contact's ([isPriority]), and marking it as one or not any more ([onToggle]). */
class PriorityActions(val isPriority: (SearchEntry) -> Boolean = { false }, val onToggle: (SearchEntry) -> Unit = {})

/** [name] with the letters at [indices] in bold, in the accent's color: what the query matched. */
internal fun highlighted(name: String, indices: List<Int>, accent: Color): AnnotatedString = buildAnnotatedString {
    val marked = indices.toSet()
    for ((i, c) in name.withIndex()) {
        if (i in marked) withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(c) } else append(c)
    }
}
