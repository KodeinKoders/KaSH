package app.kash.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationReadsTest {
    private val whatsApp = ServiceId("w")
    private val slack = ServiceId("s")
    private val laila = NotificationGroups.key(whatsApp, "Laila")
    private val lucas = NotificationGroups.key(whatsApp, "Lucas")

    private fun unread(vararg conversations: Conversation) = ServiceUnread(conversations = conversations.toList())
    private fun direct(name: String, count: Int = 1) = Conversation(name.lowercase(), name, ConversationKind.DIRECT, count)

    @Test
    fun aGroupIsOverOnceItsConversationIsReadAfterItWasListed() {
        val reads = NotificationReads()
        reads.shown(laila, unread())
        // The plugin reports the message after the notification.
        assertEquals(emptyList(), reads.update(mapOf(whatsApp to unread(direct("Laila")))))
        // Read: in KaSH, on the phone.
        assertEquals(listOf(laila), reads.update(mapOf(whatsApp to unread())))
        assertTrue(laila !in reads.keys)
    }

    @Test
    fun aConversationListedBeforeItsNotificationCameIsFollowedAllTheSame() {
        val reads = NotificationReads()
        // The plugin reported the message first: nothing changes after the notification, until the conversation is read.
        reads.shown(laila, unread(direct("Laila")))
        assertEquals(listOf(laila), reads.update(mapOf(whatsApp to unread(direct("Lucas")))))
    }

    @Test
    fun anotherConversationReadEndsNothing() {
        val reads = NotificationReads()
        reads.shown(laila, unread(direct("Laila"), direct("Lucas")))
        assertEquals(emptyList(), reads.update(mapOf(whatsApp to unread(direct("Laila")))))
    }

    @Test
    fun aTopicIsFollowedToo() {
        val general = NotificationGroups.key(slack, "général")
        val reads = NotificationReads()
        reads.shown(general, unread(Conversation("c1", "général", ConversationKind.CHANNEL, 0, topic = true)))
        assertEquals(listOf(general), reads.update(mapOf(slack to unread())))
    }

    @Test
    fun aGroupOfNoConversationIsOverOnceItsServiceHasNothingUnread() {
        // Slack's title says "New message from Laila", which no conversation is named.
        val message = NotificationGroups.key(slack, "New message from Laila")
        val reads = NotificationReads()
        // Not at once: the plugin had not reported the message yet.
        reads.shown(message, unread())
        assertEquals(emptyList(), reads.update(mapOf(slack to unread())))
        assertEquals(emptyList(), reads.update(mapOf(slack to unread(direct("Laila")))))
        assertEquals(listOf(message), reads.update(mapOf(slack to unread())))
    }

    @Test
    fun aWebApplicationsNotificationIsOverOnceItsTitleCountsNothing() {
        val app = ServiceId("a")
        val message = NotificationGroups.key(app, "Nouveau message")
        val reads = NotificationReads()
        reads.shown(message, ServiceUnread())
        assertEquals(emptyList(), reads.update(mapOf(app to ServiceUnread(total = 2))))
        assertEquals(emptyList(), reads.update(mapOf(app to ServiceUnread(total = 1))))
        assertEquals(listOf(message), reads.update(mapOf(app to ServiceUnread(total = null))))
    }

    @Test
    fun aDeletedServicesGroupsAreOver() {
        val reads = NotificationReads()
        reads.shown(laila, unread(direct("Laila")))
        assertEquals(listOf(laila), reads.update(emptyMap()))
    }

    @Test
    fun aForgottenGroupIsNotFollowed() {
        val reads = NotificationReads()
        reads.shown(laila, unread(direct("Laila")))
        reads.forget(laila)
        assertEquals(emptyList(), reads.update(mapOf(whatsApp to unread())))
    }

    @Test
    fun aGroupOfTheLastLaunchIsOverWhenItsConversationWasListedAndIsNotAnyMore() {
        val before = NotificationReads()
        before.shown(laila, unread(direct("Laila")))
        before.shown(lucas, unread())
        val seen = before.seen
        assertEquals(setOf(NotificationReads.hash(laila)), seen)

        val after = NotificationReads(seen)
        // Laila's was listed, and was read meanwhile; Lucas' never was: kept while the service has something unread.
        assertTrue(after.restored(laila, unread(direct("Rose"))))
        assertFalse(after.restored(lucas, unread(direct("Rose"))))
        assertEquals(listOf(lucas), after.update(mapOf(whatsApp to unread())))
    }

    @Test
    fun aGroupOfTheLastLaunchStillUnreadIsKeptAndFollowed() {
        val reads = NotificationReads(setOf(NotificationReads.hash(laila)))
        assertFalse(reads.restored(laila, unread(direct("Laila"))))
        assertEquals(listOf(laila), reads.update(mapOf(whatsApp to unread())))
    }

    @Test
    fun aGroupOfTheLastLaunchIsOverWhenItsServiceHasNothingUnreadOrIsGone() {
        val reads = NotificationReads()
        assertTrue(reads.restored(lucas, unread()))
        assertTrue(reads.restored(NotificationGroups.key(ServiceId("gone"), "Rose"), null))
    }

    @Test
    fun theFileKeepsTheHashesOnly() {
        val path = Files.createTempDirectory("kash-reads").resolve("notification-reads.json")
        val file = NotificationReadsFile(path)
        assertEquals(emptySet(), file.load())
        val seen = setOf(NotificationReads.hash(laila))
        file.save(seen)
        assertEquals(seen, file.load())
        assertFalse("laila" in Files.readString(path))
        Files.writeString(path, "{")
        assertEquals(emptySet(), file.load())
    }
}
