package app.kash.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrioritiesTest {
    private val whatsApp = Service(ServiceId("w"), "whatsapp", name = "WhatsApp", url = "https://web.whatsapp.com/", profile = ProfileId.random())
    private val slack = Service(ServiceId("s"), "slack", name = "Slack", url = "https://acme.slack.com/", profile = ProfileId.random())

    @Test
    fun aContactIsMarkedAndUnmarkedInItsService() {
        val marked = Priorities.toggle(emptyList(), whatsApp.id, "laila", "Laila")
        assertEquals(listOf(PriorityContact(whatsApp.id, "laila", "Laila")), marked)
        assertTrue(Priorities.contains(marked, whatsApp.id, "laila"))
        assertFalse(Priorities.contains(marked, slack.id, "laila"))
        assertFalse(Priorities.contains(marked, whatsApp.id, null))
        assertEquals(emptyList(), Priorities.toggle(marked, whatsApp.id, "laila", "Laila"))
    }

    @Test
    fun aNotificationIsAPriorityContactsByItsConversationOrItsName() {
        val priorities = listOf(PriorityContact(whatsApp.id, "laila", "Laila"))
        assertTrue(Priorities.isNotification(priorities, whatsApp.id, "Someone else", "laila"))
        // The plugin does not list the conversation yet: its name.
        assertTrue(Priorities.isNotification(priorities, whatsApp.id, " laila ", null))
        assertFalse(Priorities.isNotification(priorities, slack.id, "Laila", null))
        assertFalse(Priorities.isNotification(priorities, whatsApp.id, "Lucas", "lucas"))
    }

    @Test
    fun aDeletedServicesPrioritiesAreForgotten() {
        val priorities = listOf(PriorityContact(whatsApp.id, "laila", "Laila"), PriorityContact(ServiceId("gone"), "x", "X"))
        assertEquals(listOf(PriorityContact(whatsApp.id, "laila", "Laila")), Priorities.of(priorities, listOf(whatsApp, slack)))
    }

    @Test
    fun aPriorityContactsNotificationsGoThroughDoNotDisturbAndAMutedService() {
        val settings = Settings()
        val muted = whatsApp.copy(muted = true)
        assertFalse(NotificationPolicy.decide(settings.copy(doNotDisturb = true), whatsApp).show)
        assertTrue(NotificationPolicy.decide(settings.copy(doNotDisturb = true), whatsApp, priority = true).show)
        assertTrue(NotificationPolicy.decide(settings, muted, priority = true).show)
        // Not when the notifications are off, the service's or all of them, nor for a disabled service.
        assertFalse(NotificationPolicy.decide(settings.copy(notifications = false), whatsApp, priority = true).show)
        assertFalse(NotificationPolicy.decide(settings, whatsApp.copy(desktopNotifications = false), priority = true).show)
        assertFalse(NotificationPolicy.decide(settings, whatsApp.copy(enabled = false), priority = true).show)
        assertEquals(false, NotificationPolicy.decide(settings.copy(sound = false), muted, priority = true).sound)
    }

    @Test
    fun theUnreadTabHasThePriorityConversationsFirstInASectionOfTheirOwn() {
        val rows = listOf(
            InboxRow(whatsApp, "lucas", "Lucas", ConversationKind.DIRECT, 2),
            InboxRow(whatsApp, "laila", "Laila", ConversationKind.DIRECT, 3),
            InboxRow(slack, "C1", "general", ConversationKind.CHANNEL, 0, topic = true),
        )
        val priorities = listOf(PriorityContact(whatsApp.id, "laila", "Laila"), PriorityContact(slack.id, "C1", "general"))
        val sections = Inbox.sections(rows, priorities)
        assertEquals(listOf("Laila", "general"), sections.priority.map { it.name })
        assertEquals(listOf("Lucas"), sections.messages.map { it.name })
        assertEquals(emptyList(), sections.topics)
        // A priority topic is still not counted.
        assertEquals(5, sections.count)
        assertEquals(Inbox.sections(rows).count, sections.count)
    }

    @Test
    fun thePrioritiesAreKept() {
        val directory = java.nio.file.Files.createTempDirectory("kash-priorities")
        val file = StateFile(directory.resolve("state.json"))
        val state = KashState(services = listOf(whatsApp), priorities = listOf(PriorityContact(whatsApp.id, "laila", "Laila")))
        file.save(state)
        assertEquals(state.priorities, file.load().priorities)
        directory.toFile().deleteRecursively()
    }
}
