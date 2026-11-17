package app.kash.core

/** What a conversation is in its service. */
public enum class ConversationKind {
    /** With one person. */
    DIRECT,

    /** With several people. */
    GROUP,

    /** A channel that the user follows. */
    CHANNEL,
}

/**
 * A conversation with unread messages, as a service's page shows it.
 *
 * @property id What the service's page calls it, which opens it there.
 * @property name Empty when the service gives it none (a group without a subject).
 * @property mention Whether one of its unread messages mentions the user.
 * @property preview The start of its last message, as the service shows it in its list; empty when it shows none.
 * @property time When its last message came, in milliseconds since the epoch; null when the service does not tell.
 * @property dayOnly Whether [time] only tells the day (the service shows no time for it): it is then the start of that day.
 * @property topic Whether the service shows its new messages without counting them (a channel in bold, without a badge, and no mention): it is
 * a topic of the Unread tab, which no badge counts, and its [count] is 0.
 * @property whileClosed Whether [time] is not the service's but when KaSH saw the conversation, which came while KaSH was not watching the
 * service (see [UnreadTimes]); null when the service tells the time, or KaSH saw the conversation come.
 */
public data class Conversation(
    val id: String,
    val name: String,
    val kind: ConversationKind,
    val count: Int,
    val mention: Boolean = false,
    val preview: String = "",
    val time: Long? = null,
    val dayOnly: Boolean = false,
    val topic: Boolean = false,
    val whileClosed: WhileClosed? = null,
)

/**
 * What is unread in a service.
 *
 * @property total The number of unread messages, when the service tells it without listing the conversations (the title of a custom application).
 * @property conversations The conversations with unread messages, when the service's page is read (instrumented).
 */
public data class ServiceUnread(
    val total: Int? = null,
    val conversations: List<Conversation> = emptyList(),
) {
    /** The number of unread messages: the conversations' when they are known (not the topics', which the service does not count), else [total], else none. */
    val count: Int get() = if (conversations.isNotEmpty()) conversations.filterNot { it.topic }.sumOf { it.count } else total ?: 0

    public companion object {
        public val NONE: ServiceUnread = ServiceUnread()
    }
}

/** Reading the number of unread messages where a page shows it. */
public object UnreadCount {
    private val TITLE = Regex("^\\s*\\((\\d+)\\+?\\)")

    /** The number at the start of a page's title, as most web applications put it: `(3) Mon application` → 3. Null when there is none. */
    public fun fromTitle(title: String): Int? = TITLE.find(title)?.groupValues?.get(1)?.toIntOrNull()

    /** A count as a badge shows it: `99+` above 99. */
    public fun badge(count: Int): String = if (count > 99) "99+" else count.toString()
}
