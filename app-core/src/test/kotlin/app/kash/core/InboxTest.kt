package app.kash.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InboxTest {
    private fun service(kind: ServiceKind, name: String, label: String = "") =
        Service(ServiceId.random(), kind, name, "https://example.com/", label = label, profile = ProfileId.random())

    private val whatsApp = service(ServiceKind.WHATSAPP, "WhatsApp", "Perso")
    private val discord = service(ServiceKind.CUSTOM, "Discord", "Jeux")
    private val custom = service(ServiceKind.CUSTOM, "Mon app")
    private val settings = Settings()

    private val unread = mapOf(
        whatsApp.id to ServiceUnread(
            conversations = listOf(Conversation("1", "Laila", ConversationKind.DIRECT, 3, preview = "On se voit jeudi ?"), Conversation("2", "Escalade", ConversationKind.GROUP, 7)),
        ),
        discord.id to ServiceUnread(
            conversations = listOf(
                Conversation("3", "Le Cerf Ludique", ConversationKind.GROUP, 12, mention = true),
                Conversation("4", "Muet", ConversationKind.GROUP, 0),
            ),
        ),
        custom.id to ServiceUnread(total = 5),
    )

    @Test
    fun conversationsAndTotalsAreRows() {
        val rows = Inbox.rows(listOf(whatsApp, discord, custom), unread, settings)
        assertEquals(listOf("Laila", "Escalade", "Le Cerf Ludique", "Mon app"), rows.map { it.name })
        assertEquals(true, rows.last().isTotal)
    }

    @Test
    fun byCountIsTheMostFirst() {
        val rows = Inbox.byCount(Inbox.rows(listOf(whatsApp, discord, custom), unread, settings))
        assertEquals(listOf(12, 7, 5, 3), rows.map { it.count })
    }

    @Test
    fun byServiceGroupsTheMostFirst() {
        val groups = Inbox.byService(Inbox.rows(listOf(whatsApp, discord, custom), unread, settings))
        assertEquals(listOf(discord.id, whatsApp.id, custom.id), groups.map { it.service.id })
        assertEquals(listOf("Escalade", "Laila"), groups[1].rows.map { it.name })
    }

    @Test
    fun theFilterMatchesTheNameTheServiceAndTheLabel() {
        val rows = Inbox.rows(listOf(whatsApp, discord, custom), unread, settings)
        assertEquals(listOf("Laila"), Inbox.filter(rows, " lai ").map { it.name })
        assertEquals(listOf("Laila", "Escalade"), Inbox.filter(rows, "whatsapp").map { it.name })
        assertEquals(listOf("Le Cerf Ludique"), Inbox.filter(rows, "JEUX").map { it.name })
    }

    @Test
    fun theRowsHaveThePreviewOfTheirConversation() {
        val rows = Inbox.rows(listOf(whatsApp, discord, custom), unread, settings)
        assertEquals(listOf("On se voit jeudi ?", "", "", ""), rows.map { it.preview })
    }

    @Test
    fun directOnlyKeepsTheDirectConversationsAndTheMentions() {
        val rows = Inbox.rows(listOf(whatsApp, discord, custom), unread, settings.copy(directOnly = true))
        assertEquals(listOf("Laila", "Le Cerf Ludique", "Mon app"), rows.map { it.name })
    }

    @Test
    fun disabledExcludedAndMutedServicesAreNotListed() {
        val services = listOf(whatsApp.copy(enabled = false), discord.copy(includeInInbox = false), custom.copy(muted = true))
        assertEquals(emptyList(), Inbox.rows(services, unread, settings))
        assertEquals(listOf("Mon app"), Inbox.rows(services, unread, settings.copy(skipMuted = false)).map { it.name })
    }

    @Test
    fun badgesFollowTheServicesAndTheSettings() {
        val services = listOf(whatsApp, discord.copy(showBadge = false), custom)
        assertEquals(mapOf(whatsApp.id to 10, custom.id to 5), Inbox.badges(services, unread, settings))
        assertEquals(emptyMap(), Inbox.badges(services, unread, settings.copy(showBadges = false)))
        // A service that tells nothing (WhatsApp before it is instrumented) has no badge.
        assertEquals(emptyMap(), Inbox.badges(listOf(whatsApp), emptyMap(), settings))
    }

    @Test
    fun theDockShowsTheTotalADotOrNothing() {
        val badges = mapOf(whatsApp.id to 95, custom.id to 5)
        assertEquals("99+", Inbox.dockBadge(badges, settings))
        assertEquals("•", Inbox.dockBadge(badges, settings.copy(dockBadge = DockBadge.DOT)))
        assertNull(Inbox.dockBadge(badges, settings.copy(dockBadge = DockBadge.NONE)))
        assertNull(Inbox.dockBadge(emptyMap(), settings))
    }
}
