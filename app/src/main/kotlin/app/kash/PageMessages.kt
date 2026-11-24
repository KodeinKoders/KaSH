package app.kash

import app.kash.core.Contact
import app.kash.core.Conversation
import app.kash.core.ConversationKind
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * This value of a page's message as a primitive (a string, a number, a boolean, null), or null when it is not one: a page may send anything, and
 * `jsonPrimitive` throws on an object or an array, which ended the reading of that service's messages for good.
 */
internal fun JsonElement?.primitive(): JsonPrimitive? = this as? JsonPrimitive

/**
 * The unread conversations of an `unread` message (`kash.unread.set`), or null when its list cannot be read (not a list, an entry without an id):
 * a topic has a count of 0, a count that is not one is 1. Not the times that KaSH keeps itself (`withSeenTimes`).
 */
internal fun conversationsOf(message: JsonObject): List<Conversation>? = runCatching {
    message.getValue("conversations").jsonArray.map { element ->
        val conversation = element.jsonObject
        val topic = conversation["topic"].primitive()?.booleanOrNull ?: false
        Conversation(
            id = conversation["id"].primitive()?.contentOrNull ?: error("no id"),
            name = conversation["name"].primitive()?.contentOrNull.orEmpty(),
            kind = kindOf(conversation),
            // A topic's messages are not counted.
            count = if (topic) 0 else conversation["count"].primitive()?.intOrNull ?: 1,
            mention = !topic && conversation["mention"].primitive()?.booleanOrNull ?: false,
            preview = conversation["preview"].primitive()?.contentOrNull.orEmpty(),
            time = conversation["time"].primitive()?.longOrNull,
            dayOnly = conversation["timePrecision"].primitive()?.contentOrNull == "day",
            topic = topic,
        )
    }
}.getOrNull()

/** The contacts and topics of a `contacts` message (`kash.contacts`), or null when its list cannot be read (an entry without an id or a name). */
internal fun contactsOf(message: JsonObject): List<Contact>? = runCatching {
    message.getValue("contacts").jsonArray.map { element ->
        val contact = element.jsonObject
        Contact(
            id = contact["id"].primitive()?.contentOrNull ?: error("no id"),
            name = contact["name"].primitive()?.contentOrNull ?: error("no name"),
            kind = kindOf(contact),
        )
    }
}.getOrNull()

private fun kindOf(entry: JsonObject): ConversationKind = when (entry["kind"].primitive()?.contentOrNull) {
    "group" -> ConversationKind.GROUP
    "channel" -> ConversationKind.CHANNEL
    else -> ConversationKind.DIRECT
}
