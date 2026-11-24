package app.kash.core

import kotlinx.serialization.Serializable
import java.nio.file.Path

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
        val shown = bounded(received)
        if (complete) return shown.take(MAX_CONTACTS)
        val ids = shown.mapTo(HashSet()) { it.id }
        return (shown + previous.filter { it.id !in ids }).take(MAX_CONTACTS)
    }

    /** How many contacts a service keeps at most: the ones that its page showed last first. */
    public const val MAX_CONTACTS: Int = 10_000

    /** The longest id and name of a contact. */
    public const val MAX_ID: Int = 512
    public const val MAX_NAME: Int = 256

    // What a plugin sent, within bounds: each id once (its first), without an id or a name too long. Everything is kept in contacts.json and read
    // at each launch, and a plugin, a buggy one or a hostile one, could grow it without end.
    private fun bounded(received: List<Contact>): List<Contact> =
        received.asSequence().filter { it.id.length <= MAX_ID && it.name.length <= MAX_NAME }.distinctBy { it.id }.toList()
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
public class DirectoryFile(path: Path) {
    private val file = JsonFile(path, DirectoryContent.serializer(), ::DirectoryContent, "the search starts again from what the services show")

    /** The contacts of each service, or none when there is no file or it cannot be read ([JsonFile]). */
    public fun load(): Map<ServiceId, List<Contact>> = file.load().services.mapKeys { ServiceId(it.key) }

    /** Writes [services] durably: a crash while writing leaves the file before. */
    public fun save(services: Map<ServiceId, List<Contact>>) {
        file.save(DirectoryContent(services = services.mapKeys { it.key.value }))
    }
}
