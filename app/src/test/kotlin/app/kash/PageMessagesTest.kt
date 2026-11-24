package app.kash

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import app.kash.core.Contact
import app.kash.core.ConversationKind
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

    private fun message(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun theUnreadConversationsAreReadWithTheirKindsCountsAndTimes() {
        val read = conversationsOf(message("""{"type":"unread","conversations":[
            {"id":"a","name":"Laila","kind":"direct","count":3,"preview":"À tout","time":1732000000000,"timePrecision":"day"},
            {"id":"b","name":"Escalade","kind":"group"},
            {"id":"c","name":"général","kind":"channel","topic":true,"count":5,"mention":true},
            {"id":7,"name":"Seven","kind":"unknown","count":-2}
        ]}"""))!!
        assertEquals(listOf(ConversationKind.DIRECT, ConversationKind.GROUP, ConversationKind.CHANNEL), read.map { it.kind })
        // A count by default is 1; a topic's is 0, without a mention; a negative one is left out.
        assertEquals(listOf(3, 1, 0), read.map { it.count })
        assertEquals(false, read[2].mention)
        assertEquals(1732000000000, read[0].time)
        assertEquals(true, read[0].dayOnly)
        assertEquals(listOf("a", "b", "c"), read.map { it.id })
        assertEquals("7", conversationsOf(message("""{"conversations":[{"id":7,"name":"Seven"}]}"""))!!.single().id)
    }

    @Test
    fun aListThatCannotBeReadIsNone() {
        assertNull(conversationsOf(message("""{"type":"unread","conversations":{}}""")))
        assertNull(conversationsOf(message("""{"type":"unread","conversations":[{"name":"no id"}]}""")))
        assertNull(conversationsOf(message("""{"type":"unread"}""")))
        assertNull(contactsOf(message("""{"type":"contacts","contacts":[{"id":"a"}]}""")))
    }

    @Test
    fun theContactsAreReadWithTheirKinds() {
        val contacts = contactsOf(message("""{"type":"contacts","contacts":[{"id":"a","name":"Laila","kind":"direct"},{"id":"g","name":"général","kind":"channel"}]}"""))!!
        assertEquals(listOf(Contact("a", "Laila", ConversationKind.DIRECT), Contact("g", "général", ConversationKind.CHANNEL)), contacts)
    }
}
