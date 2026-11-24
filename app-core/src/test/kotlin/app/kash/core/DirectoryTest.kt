package app.kash.core

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DirectoryTest {
    private fun contact(id: String, name: String = id) = Contact(id, name, ConversationKind.DIRECT)

    @Test
    fun aCompleteListReplacesWhatWasSeen() {
        assertEquals(listOf(contact("b")), Directory.merge(listOf(contact("a"), contact("b", "old")), listOf(contact("b")), complete = true))
    }

    @Test
    fun whatAPageShowsIsAddedToWhatWasSeenWithItsNewNames() {
        val merged = Directory.merge(listOf(contact("a"), contact("b", "old"), contact("c")), listOf(contact("b", "new"), contact("d")), complete = false)
        // The page's first, then the others in their order before.
        assertEquals(listOf(contact("b", "new"), contact("d"), contact("a"), contact("c")), merged)
    }

    @Test
    fun whatAPluginSendsIsBounded() {
        // Each id once, its first.
        assertEquals(listOf(contact("a", "first")), Directory.merge(emptyList(), listOf(contact("a", "first"), contact("a", "second")), complete = true))
        // An id or a name too long is left out.
        val long = listOf(contact("x".repeat(Directory.MAX_ID + 1)), contact("n", "y".repeat(Directory.MAX_NAME + 1)), contact("ok"))
        assertEquals(listOf(contact("ok")), Directory.merge(emptyList(), long, complete = false))
        // At most MAX_CONTACTS, the page's first.
        val many = (1..Directory.MAX_CONTACTS + 50).map { contact("c$it") }
        val merged = Directory.merge(listOf(contact("old")), many, complete = false)
        assertEquals(Directory.MAX_CONTACTS, merged.size)
        assertEquals(contact("c1"), merged.first())
    }

    @Test
    fun theFileKeepsTheContactsOfEachService() {
        val path = Files.createTempDirectory("kash-directory").resolve("contacts.json")
        val file = DirectoryFile(path)
        assertEquals(emptyMap(), file.load())
        val services = mapOf(ServiceId("s1") to listOf(contact("a"), Contact("g", "Group", ConversationKind.GROUP)), ServiceId("s2") to emptyList())
        file.save(services)
        assertEquals(services, file.load())
        path.toFile().writeText("not json")
        assertEquals(emptyMap(), file.load())
        path.parent.toFile().deleteRecursively()
    }
}
