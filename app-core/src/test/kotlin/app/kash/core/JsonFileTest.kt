package app.kash.core

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonFileTest {
    private val directory = Files.createTempDirectory("kash-json")

    @Test
    fun aFileOfALaterVersionIsReadAsThisOneCan() {
        val path = directory.resolve("unread-times.json")
        val file = UnreadTimesFile(path)
        val seen = mapOf(ServiceId("w") to SeenService(until = 1, conversations = mapOf(UnreadTimes.key("a") to SeenUnread(2, 3))))
        file.save(seen)
        // A later version wrote it, with a key that this one does not know.
        path.writeText(path.readText().replace("\"version\": 1", "\"version\": 2, \"somethingNew\": true"))
        assertEquals(seen, file.load())
    }

    @Test
    fun aCacheThatCannotBeReadIsSetAsideToo() {
        val path = directory.resolve("contacts.json").also { it.writeText("[") }
        assertEquals(emptyMap(), DirectoryFile(path).load())
        val aside = Files.list(directory).use { files -> files.filter { it.fileName.toString().startsWith("contacts.json.unreadable-") }.toList() }
        assertEquals("[", aside.single().readText())
    }

    @Test
    fun theHashesAreTheSame() {
        assertEquals(UnreadTimes.key("laila"), NotificationReads.hash("laila"))
        assertEquals(32, shortHash("laila").length)
    }
}
