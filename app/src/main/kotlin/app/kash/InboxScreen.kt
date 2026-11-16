package app.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kash.core.ConversationKind
import app.kash.core.Inbox
import app.kash.core.InboxRow
import app.kash.core.InboxSort
import app.kash.core.UnreadCount
import app.kash.ui.CountPill
import app.kash.ui.Kash
import app.kash.ui.KashIcon
import app.kash.ui.KashIcons
import app.kash.ui.dashedBorder
import app.kash.ui.hoverBackground
import app.kash.ui.strings
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

/**
 * The Unread tab: every conversation with unread messages of the services that tell them, by date, by count or by service, filtered by name, in
 * two sections: the messages, which the services count, and the topics, which they show without counting them (a channel in bold, without a
 * badge).
 *
 * @param rows Every row, before filtering (see [Inbox.rows]).
 * @param sort How the rows are sorted, which [onSort] changes: kept in the settings, so that it is the same at the next launch.
 */
@Composable
fun InboxScreen(rows: List<InboxRow>, sort: InboxSort, onSort: (InboxSort) -> Unit, icons: ServiceIcons, onRefresh: () -> Unit, onOpen: (InboxRow) -> Unit) {
    val query = rememberTextFieldState()
    val all = Inbox.sections(rows)
    val filtered = Inbox.sections(Inbox.filter(rows, query.text.toString()))
    VerticallyScrollableContainer(Modifier.fillMaxSize().background(Kash.colors.background), scrollState = rememberScrollState()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(strings.inbox, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        val summary = strings.inboxSummary(all.count, all.messages.size)
                        Text(if (all.topics.isEmpty()) summary else "$summary · ${strings.topicsSummary(all.topics.size)}", color = Kash.colors.muted)
                    }
                    OutlinedButton(onClick = onRefresh) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            KashIcon(KashIcons.Refresh, Kash.colors.text)
                            Text(strings.refresh)
                        }
                    }
                }
                if (all.isEmpty) {
                    EmptyInbox()
                    return@Column
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        state = query,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(strings.filterPlaceholder, color = Kash.colors.faint) },
                        leadingIcon = { KashIcon(KashIcons.Search, Kash.colors.muted, Modifier.padding(end = 8.dp)) },
                    )
                    SegmentedControl(
                        listOf(
                            SegmentedControlButtonData(sort == InboxSort.BY_DATE, { Text(strings.byDate) }, { onSort(InboxSort.BY_DATE) }),
                            SegmentedControlButtonData(sort == InboxSort.BY_COUNT, { Text(strings.byCount) }, { onSort(InboxSort.BY_COUNT) }),
                            SegmentedControlButtonData(sort == InboxSort.BY_SERVICE, { Text(strings.byService) }, { onSort(InboxSort.BY_SERVICE) }),
                        ),
                    )
                }
                if (filtered.isEmpty) {
                    NoResults(query.text.toString())
                } else {
                    InboxSection(strings.messagesSection, filtered.messages, strings.noUnreadMessages, sort, icons, onOpen)
                    InboxSection(strings.topicsSection, filtered.topics, strings.noUnreadTopics, sort, icons, onOpen)
                }
            }
        }
    }
}

/** A section of the Unread tab: its title and its number of conversations, then its rows as [sort] says, or [empty] when it has none. */
@Composable
private fun InboxSection(title: String, rows: List<InboxRow>, empty: String, sort: InboxSort, icons: ServiceIcons, onOpen: (InboxRow) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (rows.isNotEmpty()) Text(rows.size.toString(), color = Kash.colors.muted, maxLines = 1)
            Box(Modifier.weight(1f).height(1.dp).background(Kash.colors.line))
        }
        when {
            rows.isEmpty() -> Text(empty, color = Kash.colors.muted, modifier = Modifier.padding(horizontal = 2.dp))
            sort == InboxSort.BY_DATE -> RowsCard(Inbox.byDate(rows)) { row -> InboxRowView(row, icons, withService = true) { onOpen(row) } }
            sort == InboxSort.BY_COUNT -> RowsCard(Inbox.byCount(rows)) { row -> InboxRowView(row, icons, withService = true) { onOpen(row) } }
            else -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                for (group in Inbox.byService(rows)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ServiceTile(group.service, icons, 20.dp)
                            Text(group.service.title, fontWeight = FontWeight.SemiBold)
                            // The topics have no count: their number.
                            val count = if (group.rows.first().topic) strings.topicsSummary(group.rows.size) else strings.unread(group.count)
                            Text(count, color = Kash.colors.muted)
                            Box(Modifier.weight(1f))
                            Link(strings.open(group.service.name), onClick = { onOpen(group.rows.first().copy(conversation = null)) })
                        }
                        RowsCard(group.rows) { row -> InboxRowView(row, icons, withService = false) { onOpen(row) } }
                    }
                }
            }
        }
    }
}

/** Rows in a rounded card, separated by a line of 1 dp. */
@Composable
private fun RowsCard(rows: List<InboxRow>, row: @Composable (InboxRow) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Kash.colors.line).border(1.dp, Kash.colors.line, shape),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        for (r in rows) row(r)
    }
}

/**
 * A conversation: its service's tile (in the flat list), its name, its context and its service, its count. A row of a custom application is its
 * total, which says so; a topic has a dot instead of a count.
 */
@Composable
private fun InboxRowView(row: InboxRow, icons: ServiceIcons, withService: Boolean, onClick: () -> Unit) {
    val context = when (row.kind) {
        null -> strings.unreadMessages
        ConversationKind.DIRECT -> strings.directConversation
        ConversationKind.GROUP -> strings.groupConversation
        ConversationKind.CHANNEL -> strings.channelConversation
    }
    val name = row.name.ifBlank { if (row.kind == ConversationKind.GROUP) strings.unnamedGroup else strings.unnamedConversation }
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (withService) 56.dp else 48.dp)
            .background(Kash.colors.card)
            .hoverBackground(Kash.colors.rowHover, RoundedCornerShape(0.dp))
            .clickable(onClick = onClick)
            // The hand of a link: a click opens the conversation.
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (withService) ServiceTile(row.service, icons, 32.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The start of the last message, as the service shows it; what the conversation is when there is none.
            Text(
                row.preview.ifBlank { if (withService) "$context · ${row.service.title}" else context },
                fontSize = 12.sp, color = Kash.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // When the last message came, over the count, as WhatsApp's list shows it.
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            row.time?.let { Text(strings.timeLabel.of(it, dayOnly = row.dayOnly), fontSize = 11.sp, color = Kash.colors.muted, maxLines = 1) }
            if (row.topic) Box(Modifier.padding(horizontal = 8.dp, vertical = 7.dp).size(8.dp).background(Kash.colors.muted, CircleShape))
            else CountPill(UnreadCount.badge(row.count))
        }
        KashIcon(KashIcons.ChevronRight, Kash.colors.faint)
    }
}

@Composable
private fun NoResults(query: String) {
    Box(
        Modifier.fillMaxWidth().dashedBorder(Kash.colors.control, 1.dp, 8.dp).padding(vertical = 40.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(strings.noResults(query.trim()), color = Kash.colors.muted, textAlign = TextAlign.Center)
    }
}

/** No conversation to show: not in the design, drawn as its "no results" box. */
@Composable
private fun EmptyInbox() {
    Column(
        Modifier.fillMaxWidth().dashedBorder(Kash.colors.control, 1.dp, 8.dp).padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KashIcon(KashIcons.Inbox, Kash.colors.faint, size = 36.dp)
        Text(strings.emptyTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(strings.emptyHint, color = Kash.colors.muted, textAlign = TextAlign.Center)
    }
}
