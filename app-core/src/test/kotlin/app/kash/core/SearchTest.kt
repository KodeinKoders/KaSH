package app.kash.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTest {
    @Test
    fun theLettersOfTheQueryAreFoundInOrder() {
        assertEquals(listOf(0, 3), fuzzyMatch("lr", "Laura")!!.second)
        assertNull(fuzzyMatch("rl", "lr"))
        assertNull(fuzzyMatch("xyz", "Laura"))
    }

    @Test
    fun theCaseAndTheAccentsDoNotMatter() {
        assertEquals(listOf(0, 1, 2), fuzzyMatch("HEL", "hélène")!!.second)
        assertEquals(listOf(0, 1, 2), fuzzyMatch("hél", "HELENE")!!.second)
        assertEquals(listOf(0, 1), fuzzyMatch("ca", "Ça va")!!.second)
        // The letters that no decomposition takes their accent from.
        assertEquals(listOf(0, 1, 2, 3), fuzzyMatch("lodz", "Łódź")!!.second)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), fuzzyMatch("strase", "Straße")!!.second)
        // A query typed decomposed: e, then its accent.
        assertEquals(listOf(0, 1), fuzzyMatch("he\u0301", "hélène")!!.second)
    }

    @Test
    fun aKoreanSyllableIsNotTheFirstOfItsLetters() {
        assertNull(fuzzyMatch("김", "강민"))
        assertEquals(listOf(0), fuzzyMatch("김", "김민")!!.second)
    }

    @Test
    fun anEmptyQueryMatchesEverythingWithNothingMarked() {
        assertEquals(0 to emptyList(), fuzzyMatch("", "Laura"))
        assertEquals(0 to emptyList(), fuzzyMatch("  ", "Laura"))
    }

    @Test
    fun spacesInTheQueryAreNotLettersToFind() {
        assertEquals(listOf(0, 1, 5, 6), fuzzyMatch("je so", "jeux société")!!.second)
    }

    @Test
    fun lettersTogetherAtTheStartOfAWordBeatScatteredOnes() {
        assertTrue(fuzzyMatch("lucas", "Lucas Martin")!!.first > fuzzyMatch("lucas", "Le Cerf Ludique au cas où")!!.first)
        assertTrue(fuzzyMatch("ma", "Lucas Martin")!!.first > fuzzyMatch("ma", "Emma")!!.first)
        assertTrue(fuzzyMatch("rel", "release")!!.first > fuzzyMatch("rel", "release-notes-for-the-whole-team")!!.first)
    }

    private val whatsApp = Service(ServiceId("w"), "whatsapp", name = "WhatsApp", url = "https://web.whatsapp.com/", profile = ProfileId.random())
    private val slack = Service(ServiceId("s"), "slack", name = "Slack", url = "https://acme.slack.com/", profile = ProfileId.random())
    private val custom = Service(ServiceId("c"), null, name = "Mail", url = "https://mail.example.com/", profile = ProfileId.random())
    private val disabled = slack.copy(id = ServiceId("d"), enabled = false)

    private val directory = mapOf(
        whatsApp.id to listOf(
            Contact("laura", "Laura", ConversationKind.DIRECT),
            Contact("jeux", "Jeux de société", ConversationKind.GROUP),
            Contact("lucas", "Lucas", ConversationKind.DIRECT),
        ),
        slack.id to listOf(Contact("C1", "general", ConversationKind.CHANNEL), Contact("D1", "Lucas Martin", ConversationKind.DIRECT)),
        disabled.id to listOf(Contact("C2", "hidden", ConversationKind.CHANNEL)),
    )

    private val unread = mapOf(
        whatsApp.id to ServiceUnread(
            conversations = listOf(
                Conversation("lucas", "Lucas", ConversationKind.DIRECT, 2),
                // Unread, and not among the contacts that the plugin gave.
                Conversation("new", "Nouveau", ConversationKind.DIRECT, 1),
            ),
        ),
        slack.id to ServiceUnread(
            conversations = listOf(
                Conversation("C1", "general", ConversationKind.CHANNEL, 0, topic = true),
                Conversation("threads", "Fils de discussion", ConversationKind.CHANNEL, 3),
            ),
        ),
        custom.id to ServiceUnread(total = 4),
    )

    private val entries = Search.entries(listOf(whatsApp, slack, custom, disabled), directory, unread)

    @Test
    fun theEntriesAreTheContactsOfTheEnabledPluginsAndTheirUnreadConversations() {
        assertEquals(listOf("laura", "jeux", "lucas", "new", "C1", "D1"), entries.map { it.conversation })
        assertEquals(2, entries.single { it.conversation == "lucas" }.count)
        assertTrue(entries.single { it.conversation == "C1" }.topic)
        assertEquals(0, entries.single { it.conversation == "laura" }.count)
    }

    @Test
    fun withNoQueryTheContactsComeFirstAndWhatIsUnreadFirstInEachSection() {
        val matches = Search.search(entries, "")
        assertEquals(listOf("lucas", "new", "laura", "D1", "C1", "jeux"), matches.map { it.entry.conversation })
        assertEquals(listOf(SearchSection.CONTACTS, SearchSection.CONTACTS, SearchSection.CONTACTS, SearchSection.CONTACTS, SearchSection.TOPICS, SearchSection.TOPICS), matches.map { it.entry.section })
        assertTrue(matches.all { it.indices.isEmpty() })
    }

    @Test
    fun theQueryKeepsWhatMatchesTheBestFirstInEachSection() {
        val matches = Search.search(entries, "luc")
        assertEquals(listOf("lucas", "D1"), matches.map { it.entry.conversation })
        assertEquals(listOf(0, 1, 2), matches.first().indices)
        assertEquals(listOf("jeux"), Search.search(entries, "societe").map { it.entry.conversation })
        assertEquals(emptyList(), Search.search(entries, "zzz"))
    }

    @Test
    fun aContactGivenTwiceIsListedOnce() {
        val twice = mapOf(whatsApp.id to listOf(Contact("a", "Ana", ConversationKind.DIRECT), Contact("a", "Ana", ConversationKind.DIRECT)))
        assertEquals(1, Search.entries(listOf(whatsApp), twice, emptyMap()).size)
    }
}
