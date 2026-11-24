package app.kash

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * This value of a page's message as a primitive (a string, a number, a boolean, null), or null when it is not one: a page may send anything, and
 * `jsonPrimitive` throws on an object or an array, which ended the reading of that service's messages for good.
 */
internal fun JsonElement?.primitive(): JsonPrimitive? = this as? JsonPrimitive
