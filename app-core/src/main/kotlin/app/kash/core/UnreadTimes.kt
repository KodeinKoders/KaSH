package app.kash.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

private val logger = LoggerFactory.getLogger(UnreadTimesFile::class.java)

/**
 * A conversation that came while KaSH was not watching its service (KaSH closed, the service disabled): its time is when KaSH saw it once its
 * page was loaded again, and it came after [after], when KaSH last watched the service (null: KaSH never did, a service just added).
 */
public data class WhileClosed(val after: Long?)

/**
 * When KaSH saw a conversation with unread messages, for a service that does not tell when they came.
 *
 * @property count Its count when KaSH last saw it (0 for a topic): a greater one is a new message.
 * @property time When KaSH saw it unread, or saw its count grow, in milliseconds since the epoch.
 * @property whileClosed Whether it came while KaSH was not watching the service, after [after] (see [WhileClosed]).
 */
@Serializable
public data class SeenUnread(
    val count: Int,
    val time: Long,
    val whileClosed: Boolean = false,
    val after: Long? = null,
)

/**
 * What KaSH saw of a service's unread conversations.
 *
 * @property until When KaSH last watched the service (its last report, or when its page was closed), in milliseconds since the epoch: what came
 * after, until its page was loaded again, came while KaSH was not watching it.
 * @property conversations What it saw of each conversation, by [UnreadTimes.key].
 */
@Serializable
public data class SeenService(
    val until: Long? = null,
    val conversations: Map<String, SeenUnread> = emptyMap(),
)

/**
 * The time of the unread conversations whose service does not tell it (Slack, Discord): when KaSH saw them, kept across launches
 * ([UnreadTimesFile]).
 *
 * A conversation takes the time when KaSH first sees it unread, and again when its count grows (a new message). One that is not unread any more
 * is forgotten. While a page settles, just loaded, its reports may be partial (a list that is still loading): what they do not show is kept, and
 * what is new came while KaSH was not watching the service, when that lasted long enough ([WhileClosed]).
 */
public object UnreadTimes {
    /** How long after its page was loaded a service's reports may be partial, and its new conversations came before. */
    public const val SETTLE_MS: Long = 60_000

    /** How long KaSH must not have watched a service for its new conversations to have come meanwhile: a page loaded again at once missed none. */
    public const val GAP_MS: Long = 60_000

    /**
     * The key of a conversation [id] in [SeenService.conversations]: a hash, since some ids say who the conversation is with (WhatsApp's are
     * phone numbers).
     */
    public fun key(id: String): String =
        MessageDigest.getInstance("SHA-256").digest(id.encodeToByteArray()).take(16).joinToString("") { "%02x".format(it) }

    /**
     * What KaSH saw of a service ([previous]) once its page shows [conversations], at [now]. Its page was loaded at [loadedAt]; KaSH had last
     * watched the service at [watchedUntil] before (the `until` that it had then; null: never). Only the conversations without a time are kept.
     */
    public fun update(previous: SeenService, conversations: List<Conversation>, now: Long, loadedAt: Long, watchedUntil: Long?): SeenService {
        val settling = now - loadedAt < SETTLE_MS
        // What is new while the page settles came while KaSH was not watching the service, when it did not for long enough or never did.
        val whileClosed = settling && (watchedUntil == null || loadedAt - watchedUntil > GAP_MS)
        val seen = conversations.filter { it.time == null }.associate { conversation ->
            val key = key(conversation.id)
            val before = previous.conversations[key]
            key to when {
                // Seen before, without a new message (fewer: some were read).
                before != null && conversation.count <= before.count -> before.copy(count = conversation.count)
                whileClosed -> SeenUnread(conversation.count, now, whileClosed = true, after = watchedUntil)
                else -> SeenUnread(conversation.count, now)
            }
        }
        return SeenService(until = now, conversations = if (settling) previous.conversations + seen else seen)
    }

    /** [conversations] with the time when KaSH saw them, for those whose service does not tell it, from [seen]. */
    public fun withTimes(conversations: List<Conversation>, seen: SeenService): List<Conversation> = conversations.map { conversation ->
        if (conversation.time != null) return@map conversation
        val unread = seen.conversations[key(conversation.id)] ?: return@map conversation
        conversation.copy(time = unread.time, whileClosed = if (unread.whileClosed) WhileClosed(unread.after) else null)
    }
}

@Serializable
internal data class UnreadTimesContent(
    val version: Int = 1,
    val services: Map<String, SeenService> = emptyMap(),
)

/** `unread-times.json`: what KaSH saw of each service's unread conversations ([UnreadTimes]), by service id. */
public class UnreadTimesFile(private val path: Path) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** What KaSH saw of each service, or nothing when there is no file or it cannot be read (which is logged). */
    public fun load(): Map<ServiceId, SeenService> {
        if (!Files.exists(path)) return emptyMap()
        return try {
            json.decodeFromString(UnreadTimesContent.serializer(), Files.readString(path)).services.mapKeys { ServiceId(it.key) }
        } catch (e: Exception) {
            logger.error("Cannot read {}: the unread conversations' times start again", path, e)
            emptyMap()
        }
    }

    /** Writes [services] to a temporary file next to the file, then moves it in place: a crash while writing leaves the file before. */
    @Synchronized
    public fun save(services: Map<ServiceId, SeenService>) {
        Files.createDirectories(path.toAbsolutePath().parent)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        val content = UnreadTimesContent(services = services.mapKeys { it.key.value })
        Files.writeString(temporary, json.encodeToString(UnreadTimesContent.serializer(), content))
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
