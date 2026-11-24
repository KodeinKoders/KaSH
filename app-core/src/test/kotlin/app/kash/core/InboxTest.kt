package app.kash.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InboxTest {
    // A plugin's service ([plugin] is its id), or a web application (null).
    private fun service(plugin: String?, name: String, label: String = "") =
        Service(ServiceId.random(), plugin, name = name, url = "https://example.com/", label = label, profile = ProfileId.random())

    private val whatsApp = service("whatsapp", "WhatsApp", "Perso")
    private val discord = service(null, "Discord", "Jeux")
    private val custom = service(null, "Mon app")
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
    fun byDateIsTheMostRecentFirstThenWhatHasNoDateByCount() {
        val dated = mapOf(
            whatsApp.id to ServiceUnread(
                conversations = listOf(
                    Conversation("1", "Laila", ConversationKind.DIRECT, 3, time = 1_000L),
                    Conversation("2", "Escalade", ConversationKind.GROUP, 7, time = 3_000L),
                    Conversation("5", "Sans date", ConversationKind.DIRECT, 2),
                ),
            ),
            discord.id to ServiceUnread(conversations = listOf(Conversation("3", "Le Cerf Ludique", ConversationKind.GROUP, 12, mention = true, time = 2_000L))),
            custom.id to ServiceUnread(total = 5),
        )

        val rows = Inbox.byDate(Inbox.rows(listOf(whatsApp, discord, custom), dated, settings))

        assertEquals(listOf("Escalade", "Le Cerf Ludique", "Laila", "Mon app", "Sans date"), rows.map { it.name })
    }

    @Test
    fun theUnreadTabIsByDateByDefault() {
        assertEquals(InboxSort.BY_DATE, Settings().inboxSort)
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
        // Without the accents, as the search.
        val accented = rows.map { if (it.name == "Laila") it.copy(name = "Hélène") else it }
        assertEquals(listOf("Hélène"), Inbox.filter(accented, "helene").map { it.name })
    }

    @Test
    fun theRowsHaveThePreviewOfTheirConversation() {
        val rows = Inbox.rows(listOf(whatsApp, discord, custom), unread, settings)
        assertEquals(listOf("On se voit jeudi ?", "", "", ""), rows.map { it.preview })
    }

    @Test
    fun approximateTimesAreShownOrNotUsedAtAll() {
        val timed = mapOf(
            whatsApp.id to ServiceUnread(
                conversations = listOf(
                    Conversation("seen", "Vue", ConversationKind.DIRECT, 1, time = 3_000L),
                    Conversation("closed", "Pendant la fermeture", ConversationKind.DIRECT, 1, time = 5_000L, whileClosed = WhileClosed(1_000L)),
                    Conversation("old", "Ancienne", ConversationKind.DIRECT, 1, time = 2_000L),
                ),
            ),
        )
        val shown = Inbox.byDate(Inbox.rows(listOf(whatsApp), timed, settings))
        assertEquals(listOf("Pendant la fermeture", "Vue", "Ancienne"), shown.map { it.name })
        assertEquals(WhileClosed(1_000L), shown.first().whileClosed)

        val hidden = Inbox.byDate(Inbox.rows(listOf(whatsApp), timed, settings.copy(showApproximateTimes = false)))
        assertEquals(listOf("Vue", "Ancienne", "Pendant la fermeture"), hidden.map { it.name })
        assertEquals(listOf(3_000L, 2_000L, null), hidden.map { it.time })
        assertEquals(null, hidden.last().whileClosed)
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

    private val slack = service("slack", "Slack", "kotlinlang")

    private val withTopics = unread + mapOf(
        slack.id to ServiceUnread(
            conversations = listOf(
                Conversation("D1", "Bruno", ConversationKind.DIRECT, 2),
                Conversation("C1", "general", ConversationKind.GROUP, 0, topic = true),
                Conversation("C2", "random", ConversationKind.GROUP, 0, topic = true, time = 5_000L),
            ),
        ),
    )

    @Test
    fun theTopicsAreASectionOfTheirOwn() {
        val sections = Inbox.sections(Inbox.rows(listOf(whatsApp, slack), withTopics, settings))
        assertEquals(listOf("Laila", "Escalade", "Bruno"), sections.messages.map { it.name })
        assertEquals(listOf("general", "random"), sections.topics.map { it.name })
        assertEquals(12, sections.count)
        assertEquals(listOf(0, 0), sections.topics.map { it.count })
    }

    @Test
    fun aTopicIsCountedByNoBadge() {
        assertEquals(2, withTopics.getValue(slack.id).count)
        assertEquals(mapOf(slack.id to 2), Inbox.badges(listOf(slack), withTopics, settings))
        val onlyTopics = mapOf(slack.id to ServiceUnread(conversations = listOf(Conversation("C1", "general", ConversationKind.GROUP, 0, topic = true))))
        assertEquals(emptyMap(), Inbox.badges(listOf(slack), onlyTopics, settings))
        assertNull(Inbox.dockBadge(Inbox.badges(listOf(slack), onlyTopics, settings), settings))
    }

    @Test
    fun directOnlyLeavesTheTopicsOut() {
        val rows = Inbox.rows(listOf(slack), withTopics, settings.copy(directOnly = true))
        assertEquals(listOf("Bruno"), rows.map { it.name })
    }

    @Test
    fun aGroupOfTheTopicsCountsNoMessage() {
        val groups = Inbox.byService(Inbox.sections(Inbox.rows(listOf(slack), withTopics, settings)).topics)
        assertEquals(listOf(0), groups.map { it.count })
    }
}
