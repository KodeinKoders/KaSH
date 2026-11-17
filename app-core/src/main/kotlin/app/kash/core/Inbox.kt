package app.kash.core

/**
 * A line of the Unread tab: a conversation of a service, or the total of a service that only tells its total (a custom application).
 *
 * @property conversation The conversation's id in its service, null for a total.
 * @property kind The conversation's kind, null for a total.
 * @property preview The start of the conversation's last message; empty for a total, or when the service shows none.
 * @property time When the conversation's last message came, in milliseconds since the epoch; null for a total, or when the service does not tell.
 * @property topic Whether it is a topic (see [Conversation.topic]): in the Topics section, without a count, which no badge counts.
 * @property whileClosed Whether [time] is when KaSH saw a conversation that came while it was not watching (see [Conversation.whileClosed]).
 */
public data class InboxRow(
    val service: Service,
    val conversation: String?,
    val name: String,
    val kind: ConversationKind?,
    val count: Int,
    val preview: String = "",
    val time: Long? = null,
    val dayOnly: Boolean = false,
    val topic: Boolean = false,
    val whileClosed: WhileClosed? = null,
) {
    val isTotal: Boolean get() = kind == null
}

/** The rows of one service, in the Unread tab grouped by service. */
public data class InboxGroup(
    val service: Service,
    val rows: List<InboxRow>,
) {
    val count: Int get() = rows.sumOf { it.count }
}

/**
 * The two sections of the Unread tab: the conversations whose unread messages their service counts, and the topics, which it shows without
 * counting them (a channel in bold, without a badge).
 */
public data class InboxSections(
    val messages: List<InboxRow>,
    val topics: List<InboxRow>,
) {
    val isEmpty: Boolean get() = messages.isEmpty() && topics.isEmpty()

    /** The number of unread messages, the topics' left out. */
    val count: Int get() = messages.sumOf { it.count }
}

/** What the Unread tab shows, made from the services and what is unread in each. */
public object Inbox {
    /** The services whose unread messages the Unread tab lists: enabled, included, and not muted when [Settings.skipMuted] says so. */
    public fun listed(services: List<Service>, settings: Settings): List<Service> =
        services.filter { it.enabled && it.includeInInbox && !(settings.skipMuted && it.muted) }

    /**
     * Every row, before filtering: the conversations of each listed service, or its total when it only has one. With [Settings.directOnly], the
     * conversations of groups and channels are left out, unless they mention the user, and so are the topics.
     */
    public fun rows(services: List<Service>, unread: Map<ServiceId, ServiceUnread>, settings: Settings): List<InboxRow> =
        listed(services, settings).flatMap { service ->
            val serviceUnread = unread[service.id] ?: ServiceUnread.NONE
            when {
                serviceUnread.conversations.isNotEmpty() ->
                    serviceUnread.conversations
                        .filter { (it.topic || it.count > 0) && (!settings.directOnly || (!it.topic && (it.kind == ConversationKind.DIRECT || it.mention))) }
                        // An approximate time that the settings do not show is not used either: the row has none.
                        .map { if (it.whileClosed != null && !settings.showApproximateTimes) it.copy(time = null, whileClosed = null) else it }
                        .map { InboxRow(service, it.id, it.name, it.kind, if (it.topic) 0 else it.count, it.preview, it.time, it.dayOnly, it.topic, it.whileClosed) }
                (serviceUnread.total ?: 0) > 0 -> listOf(InboxRow(service, null, service.title, null, serviceUnread.total!!))
                else -> emptyList()
            }
        }

    /** The rows in their sections: the messages, then the topics, each in the order of [rows]. */
    public fun sections(rows: List<InboxRow>): InboxSections {
        val (topics, messages) = rows.partition { it.topic }
        return InboxSections(messages, topics)
    }

    /** The rows that [query] matches, by the conversation's name, or the service's name or label, ignoring case. */
    public fun filter(rows: List<InboxRow>, query: String): List<InboxRow> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return rows
        return rows.filter {
            it.name.lowercase().contains(q) || it.service.name.lowercase().contains(q) || it.service.label.lowercase().contains(q)
        }
    }

    /**
     * The rows by the time of their last message, the most recent first. The rows without one (the total of a custom application, a service that
     * does not tell) come after, by number of unread messages.
     */
    public fun byDate(rows: List<InboxRow>): List<InboxRow> =
        rows.sortedWith(compareBy<InboxRow> { it.time == null }.thenByDescending { it.time ?: 0L }.thenByDescending { it.count })

    /** The rows by number of unread messages, the most first; rows of the same number keep their order. */
    public fun byCount(rows: List<InboxRow>): List<InboxRow> = rows.sortedByDescending { it.count }

    /** The rows grouped by service, the services with the most unread messages first, each group by number of unread messages. */
    public fun byService(rows: List<InboxRow>): List<InboxGroup> =
        rows.groupBy { it.service.id }.values
            .map { InboxGroup(it.first().service, byCount(it)) }
            .sortedByDescending { it.count }

    /**
     * The badge of each service's tab: the number of its unread messages, when the service and the settings show one. A service that tells
     * nothing has none.
     */
    public fun badges(services: List<Service>, unread: Map<ServiceId, ServiceUnread>, settings: Settings): Map<ServiceId, Int> =
        if (!settings.showBadges) {
            emptyMap()
        } else {
            services.filter { it.enabled && it.showBadge }
                .associate { it.id to (unread[it.id] ?: ServiceUnread.NONE).count }
                .filterValues { it > 0 }
        }

    /** What the icon of the Dock shows, from the badges of the tabs: their total, a dot, or nothing. */
    public fun dockBadge(badges: Map<ServiceId, Int>, settings: Settings): String? {
        val total = badges.values.sum()
        return when {
            total == 0 -> null
            settings.dockBadge == DockBadge.TOTAL -> UnreadCount.badge(total)
            settings.dockBadge == DockBadge.DOT -> "•"
            else -> null
        }
    }
}
