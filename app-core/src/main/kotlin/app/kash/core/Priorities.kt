package app.kash.core

import kotlinx.serialization.Serializable

/**
 * A contact (or a topic) that the user marked as priority: its unread conversation is in the Unread tab's section of its own, before the
 * messages, and its notifications are shown even in Do Not Disturb and when its service is muted ([NotificationPolicy]).
 *
 * @property conversation Its id in its service ([Conversation.id], [Contact.id]).
 * @property name Its name when it was marked: what Settings shows, and what a notification is told by while its plugin does not list the
 * conversation yet.
 */
@Serializable
public data class PriorityContact(
    val service: ServiceId,
    val conversation: String,
    val name: String,
)

/** The contacts that the user marked as priority. */
public object Priorities {
    /** Whether the conversation [conversation] of [service] is a priority one. */
    public fun contains(priorities: List<PriorityContact>, service: ServiceId, conversation: String?): Boolean =
        conversation != null && priorities.any { it.service == service && it.conversation == conversation }

    /** [priorities] with the conversation [conversation] of [service], named [name], added, or taken out when it was there. */
    public fun toggle(priorities: List<PriorityContact>, service: ServiceId, conversation: String, name: String): List<PriorityContact> =
        if (contains(priorities, service, conversation)) priorities.filterNot { it.service == service && it.conversation == conversation }
        else priorities + PriorityContact(service, conversation, name)

    /**
     * Whether a notification of [service] titled [title] is a priority contact's: its unread conversation ([conversation], by its title,
     * see [NotificationGroups.conversationOf]) is one, or, while the plugin does not list it yet, a priority contact of the service has that name.
     */
    public fun isNotification(priorities: List<PriorityContact>, service: ServiceId, title: String, conversation: String?): Boolean =
        contains(priorities, service, conversation) ||
            priorities.any { it.service == service && it.name.isNotBlank() && it.name.trim().equals(title.trim(), ignoreCase = true) }

    /** [priorities] without those of the services that no longer exist. */
    public fun of(priorities: List<PriorityContact>, services: List<Service>): List<PriorityContact> =
        priorities.filter { contact -> services.any { it.id == contact.service } }
}
