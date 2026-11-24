package app.kash.core

import kotlinx.serialization.Serializable
import java.nio.file.Path

/**
 * When the notifications of a group ([NotificationGroups.key]: a service and a title) are over because what they say was read, in KaSH or
 * elsewhere (the phone, another browser): the service's page shows it read, and its plugin's next report says so.
 *
 * A group is followed from its notification on:
 * - its conversation (the plugin's unread one of its title, a topic too: [NotificationGroups.listedOf]) was listed, and is not any more: read;
 * - or, for a service whose conversations do not match the titles of its notifications (or a web application, which only tells its total), the
 *   service had something unread after the notification came, and has nothing any more: everything was read.
 *
 * Not the service's total at once: a notification often comes before the plugin's report of its message. A group of the last launch, whose
 * notification the Notification Center still shows ([restored]), is over once its service said what it has unread, when its conversation is not
 * listed and was before (the groups whose conversation was listed are kept, by [seen]), or when its service has nothing unread.
 */
public class NotificationReads(seenBefore: Set<String> = emptySet()) {
    private class Followed(var listed: Boolean, var serviceHadUnread: Boolean, val restored: Boolean)

    // The hashes of the groups whose conversation was listed, at the last launch (NotificationReadsFile).
    private val seenBefore = seenBefore.toMutableSet()

    private val followed = LinkedHashMap<String, Followed>()

    /** The groups followed now. */
    public val keys: Set<String> get() = followed.keys

    /** The hashes ([hash]) of the groups followed whose conversation was listed: what [NotificationReadsFile] keeps for the next launch. */
    public val seen: Set<String> get() = followed.filterValues { it.listed }.keys.mapTo(HashSet(), ::hash)

    /** A notification of the group [key] was shown, while its service has [unread]: whether the group is over already (never, for now). */
    public fun shown(key: String, unread: ServiceUnread?) {
        // A notification that comes again for a group keeps what was known of it.
        val group = followed.getOrPut(key) { Followed(listed = false, serviceHadUnread = false, restored = false) }
        // Listed already: the plugin reported its message before the notification came.
        if (unread != null && listedIn(key, unread)) group.listed = true
    }

    /**
     * The group [key] of a notification of the last launch, which the Notification Center still shows: true when it is over at once. Its service
     * is [serviceThere] or deleted (over); [unread] is what it has unread, or null while it has not said it since the launch (a page still loading,
     * a service disabled, a plugin whose read fails): the group is then followed as it is, and judged by the first [update] where its service
     * said it, rather than taken for read.
     */
    public fun restored(key: String, unread: ServiceUnread?, serviceThere: Boolean = unread != null): Boolean {
        if (!serviceThere) return true
        val group = Followed(listed = hash(key) in seenBefore, serviceHadUnread = false, restored = true)
        followed[key] = group
        if (unread == null) return false
        return over(key, group, unread).also { if (it) followed.remove(key) }
    }

    /**
     * What the services that said it have unread now ([unread]), among the services that are there ([services]): the groups that are over, which
     * are not followed any more. A group of a service that is not there any more is over; one of a service that is there but is not in [unread]
     * (its page is closed, not loaded yet, or has not said what it has unread since it was loaded) is not judged: it stays as it is.
     */
    public fun update(unread: Map<ServiceId, ServiceUnread>, services: Set<ServiceId> = unread.keys): List<String> {
        val over = followed.filter { (key, group) ->
            val service = serviceOf(key)
            when {
                service !in services -> true
                service !in unread -> false
                else -> over(key, group, unread[service])
            }
        }.keys.toList()
        over.forEach(followed::remove)
        return over
    }

    /** The group [key] is not followed any more: its notification was clicked, closed, or went away. */
    public fun forget(key: String) {
        followed.remove(key)
    }

    // Whether the group [key] is over, with what its service has unread now; what was seen of it is kept in [group].
    private fun over(key: String, group: Followed, unread: ServiceUnread?): Boolean {
        // A service that is not there any more: nothing will say it was read.
        if (unread == null) return true
        if (listedIn(key, unread)) {
            group.listed = true
            return false
        }
        if (group.listed) return true
        val hasUnread = unread.count > 0 || unread.conversations.isNotEmpty()
        if (hasUnread) {
            group.serviceHadUnread = true
            return false
        }
        return group.serviceHadUnread || group.restored
    }

    private fun listedIn(key: String, unread: ServiceUnread): Boolean = NotificationGroups.listedOf(unread.conversations, titleOf(key)) != null

    public companion object {
        /** The service of the group [key]. */
        public fun serviceOf(key: String): ServiceId = ServiceId(key.substringBefore('|'))

        /** The title of the group [key], as [NotificationGroups.key] keeps it (trimmed, in lower case: the conversations are matched without case). */
        public fun titleOf(key: String): String = key.substringAfter('|')

        /** The hash of the group [key], which [NotificationReadsFile] keeps: a group's title is a contact's name. */
        public fun hash(key: String): String = shortHash(key)
    }
}

@Serializable
internal data class NotificationReadsContent(
    val version: Int = 1,
    val seen: Set<String> = emptySet(),
)

/** `notification-reads.json`: the groups whose conversation was listed ([NotificationReads.seen], hashed), for the next launch. */
public class NotificationReadsFile(path: Path) {
    private val file = JsonFile(
        path, NotificationReadsContent.serializer(), ::NotificationReadsContent,
        "the notifications of the last launch are kept until their services have nothing unread",
    )

    /** The hashes of the groups, or none when there is no file or it cannot be read ([JsonFile]). */
    public fun load(): Set<String> = file.load().seen

    /** Writes [seen] durably: a crash while writing leaves the file before. */
    public fun save(seen: Set<String>) {
        file.save(NotificationReadsContent(seen = seen))
    }
}
