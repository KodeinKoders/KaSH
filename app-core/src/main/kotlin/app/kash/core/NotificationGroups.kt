package app.kash.core

/**
 * The notifications of a contact (a person, a group, a channel) are one: its last message, and how many are unread. A page's notifications say
 * whose they are by their title (WhatsApp's is the contact's name, a group's name for a group), which is what they are grouped by, in their service.
 */
public object NotificationGroups {
    /** The group of a notification of [service] titled [title]: what tells it from the others, and the Notification Center's identifier. */
    public fun key(service: ServiceId, title: String): String = "${service.value}|${title.trim().lowercase()}"

    /** The unread conversation of the group titled [title] among [conversations] (the plugin's), by its name; null when none is. */
    public fun conversationOf(conversations: List<Conversation>, title: String): Conversation? {
        val name = title.trim()
        if (name.isEmpty()) return null
        return conversations.firstOrNull { !it.topic && it.name.trim().equals(name, ignoreCase = true) }
    }

    /**
     * The count that the group shows: the notifications that it [received] since it was last shown, or its conversation's unread messages
     * when the plugin counts more (a message that came without a notification, or before KaSH saw it).
     */
    public fun count(received: Int, conversation: Conversation?): Int = maxOf(received, conversation?.count ?: 0)
}
