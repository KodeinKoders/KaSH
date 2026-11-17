package app.kash.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val logger = LoggerFactory.getLogger(DirectoryFile::class.java)

/**
 * A contact or a topic that a service gives access to (a person, a group, a channel, a space, a server), as its plugin showed it: what the search
 * lists, by its [name], and opens by its [id] (the same as its conversation's, see [Conversation.id]).
 */
@Serializable
public data class Contact(
    val id: String,
    val name: String,
    val kind: ConversationKind,
)

/**
 * The contacts and topics of the services, kept across launches ([DirectoryFile]): a page often shows only part of them (a virtual list, the
 * rows it loaded, a list on another screen), so what KaSH saw once stays.
 */
public object Directory {
    /**
     * What KaSH knows of a service's contacts once its plugin gives [received]: all of them when it says so ([complete]: one that is not there any
     * more is gone), else what its page shows, added to what KaSH saw before ([previous]), whose names it updates; in the page's order, then the
     * others in their order before.
     */
    public fun merge(previous: List<Contact>, received: List<Contact>, complete: Boolean): List<Contact> {
        if (complete) return received
        val ids = received.mapTo(HashSet()) { it.id }
        return received + previous.filter { it.id !in ids }
    }
}

@Serializable
internal data class DirectoryContent(
    val version: Int = 1,
    val services: Map<String, List<Contact>> = emptyMap(),
)

/**
 * `contacts.json`: the contacts and topics of each service ([Directory]), by service id. Their ids and names are in it as they are, since the
 * search shows the names and opens the conversations by their ids.
 */
public class DirectoryFile(private val path: Path) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** The contacts of each service, or none when there is no file or it cannot be read (which is logged). */
    public fun load(): Map<ServiceId, List<Contact>> {
        if (!Files.exists(path)) return emptyMap()
        return try {
            json.decodeFromString(DirectoryContent.serializer(), Files.readString(path)).services.mapKeys { ServiceId(it.key) }
        } catch (e: Exception) {
            logger.error("Cannot read {}: the search starts again from what the services show", path, e)
            emptyMap()
        }
    }

    /** Writes [services] to a temporary file next to the file, then moves it in place: a crash while writing leaves the file before. */
    @Synchronized
    public fun save(services: Map<ServiceId, List<Contact>>) {
        Files.createDirectories(path.toAbsolutePath().parent)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        val content = DirectoryContent(services = services.mapKeys { it.key.value })
        Files.writeString(temporary, json.encodeToString(DirectoryContent.serializer(), content))
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
