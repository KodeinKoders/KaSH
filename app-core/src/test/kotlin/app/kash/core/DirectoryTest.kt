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
