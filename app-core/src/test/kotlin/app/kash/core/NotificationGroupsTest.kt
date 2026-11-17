package app.kash.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class NotificationGroupsTest {
    private val whatsApp = ServiceId("w")
    private val slack = ServiceId("s")

    @Test
    fun theNotificationsOfAContactInAServiceAreOneGroup() {
        assertEquals(NotificationGroups.key(whatsApp, "Laila"), NotificationGroups.key(whatsApp, " laila "))
        assertNotEquals(NotificationGroups.key(whatsApp, "Laila"), NotificationGroups.key(whatsApp, "Lucas"))
        assertNotEquals(NotificationGroups.key(whatsApp, "Laila"), NotificationGroups.key(slack, "Laila"))
    }

    private val conversations = listOf(
        Conversation("1", "Laila", ConversationKind.DIRECT, 3),
        Conversation("2", "général", ConversationKind.CHANNEL, 0, topic = true),
    )

    @Test
    fun theConversationOfAGroupIsTheUnreadOneOfItsName() {
        assertEquals("1", NotificationGroups.conversationOf(conversations, "laila")?.id)
        assertNull(NotificationGroups.conversationOf(conversations, "Lucas"))
        // A topic has no count to show.
        assertNull(NotificationGroups.conversationOf(conversations, "général"))
        assertNull(NotificationGroups.conversationOf(conversations, " "))
    }

    @Test
    fun theCountIsTheNotificationsOrTheUnreadMessagesWhicheverIsMore() {
        val laila = conversations.first()
        assertEquals(3, NotificationGroups.count(1, laila))
        assertEquals(4, NotificationGroups.count(4, laila))
        assertEquals(2, NotificationGroups.count(2, null))
    }
}
