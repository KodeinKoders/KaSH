package app.kash.browser

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class RealPathTest {
    private val directory = Files.createTempDirectory("kash-real-path").toRealPath()

    @Test
    fun aPathThroughALinkIsTheRealOne() {
        val real = Files.createDirectories(directory.resolve("support"))
        val link = Files.createSymbolicLink(directory.resolve("link"), real)
        assertEquals(real.resolve("profiles"), realPath(link.resolve("profiles")))
    }

    @Test
    fun whatDoesNotExistYetIsKeptAfterTheRealPathOfWhatDoes() {
        val real = Files.createDirectories(directory.resolve("root"))
        val link = Files.createSymbolicLink(directory.resolve("to-root"), real)
        assertEquals(real.resolve("profiles").resolve("env-1"), realPath(link.resolve("profiles").resolve("env-1")))
    }

    @Test
    fun aRelativePathIsMadeAbsolute() {
        assertEquals(java.nio.file.Paths.get("").toRealPath().resolve("profiles"), realPath(java.nio.file.Paths.get("profiles")))
    }
}
