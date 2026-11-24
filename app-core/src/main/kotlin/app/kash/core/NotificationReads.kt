package app.kash.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

private val logger = LoggerFactory.getLogger(NotificationReads::class.java)

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
 * notification the Notification Center still shows ([restored]), is over at once when its conversation is not listed and was before (the
 * groups whose conversation was listed are kept, by [seen]), or when its service has nothing unread.
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
     * The group [key] of a notification of the last launch, which the Notification Center still shows, once its service had the time to say
     * what it has unread ([unread]: null for a service that is not there any more). True when it is over at once.
     */
    public fun restored(key: String, unread: ServiceUnread?): Boolean {
        val group = Followed(listed = hash(key) in seenBefore, serviceHadUnread = false, restored = true)
        followed[key] = group
        return over(key, group, unread).also { if (it) followed.remove(key) }
    }

    /** What every service has unread now ([unread]): the groups that are over, which are not followed any more. */
    public fun update(unread: Map<ServiceId, ServiceUnread>): List<String> {
        val over = followed.filter { (key, group) -> over(key, group, unread[serviceOf(key)]) }.keys.toList()
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
        public fun hash(key: String): String =
            MessageDigest.getInstance("SHA-256").digest(key.encodeToByteArray()).take(16).joinToString("") { "%02x".format(it) }
    }
}

@Serializable
internal data class NotificationReadsContent(
    val version: Int = 1,
    val seen: Set<String> = emptySet(),
)

/** `notification-reads.json`: the groups whose conversation was listed ([NotificationReads.seen], hashed), for the next launch. */
public class NotificationReadsFile(private val path: Path) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** The hashes of the groups, or none when there is no file or it cannot be read (which is logged). */
    public fun load(): Set<String> {
        if (!Files.exists(path)) return emptySet()
        return try {
            json.decodeFromString(NotificationReadsContent.serializer(), Files.readString(path)).seen
        } catch (e: Exception) {
            logger.error("Cannot read {}: the notifications of the last launch are kept until their services have nothing unread", path, e)
            emptySet()
        }
    }

    /** Writes [seen] to a temporary file next to the file, then moves it in place: a crash while writing leaves the file before. */
    @Synchronized
    public fun save(seen: Set<String>) {
        Files.createDirectories(path.toAbsolutePath().parent)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(temporary, json.encodeToString(NotificationReadsContent.serializer(), NotificationReadsContent(seen = seen)))
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
