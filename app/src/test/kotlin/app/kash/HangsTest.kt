package app.kash

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HangsTest {
    private val directory = Files.createTempDirectory("kash-hangs").resolve("hangs")

    @AfterTest
    fun cleanUp() {
        directory.parent.toFile().deleteRecursively()
    }

    @Test
    fun theThreadsAreWrittenWithTheirWholeStacks() {
        val file = assertNotNull(dumpThreads(directory, "test"))
        val text = Files.readString(file)

        assertTrue(file.fileName.toString().endsWith("-test.txt"))
        assertTrue(text.startsWith("KaSH: test"))
        assertTrue("\"${Thread.currentThread().name}\"" in text)
        // This test's own frame is there, deeper than the 8 frames that ThreadInfo.toString() keeps.
        assertTrue("HangsTest.theThreadsAreWrittenWithTheirWholeStacks" in text)
    }
}
