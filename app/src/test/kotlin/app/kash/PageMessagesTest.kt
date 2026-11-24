package app.kash

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageMessagesTest {
    @Test
    fun aValueOfTheWrongTypeIsReadAsMissing() {
        val message = Json.parseToJsonElement("""{"type":"unreadCount","count":{},"list":[1],"title":"Hi","n":3,"none":null}""").jsonObject
        assertNull(message["count"].primitive())
        assertNull(message["list"].primitive())
        assertNull(message["absent"].primitive())
        assertEquals("Hi", message["title"].primitive()?.contentOrNull)
        assertEquals(3, message["n"].primitive()?.intOrNull)
        assertNull(message["none"].primitive()?.contentOrNull)
    }
}
