package app.kash.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class WindowFileTest {
    private val directory: Path = Files.createTempDirectory("kash-window")
    private val path = directory.resolve("window.json")
    private val file = WindowFile(path)

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun aGeometryIsReadBackAsItWasWritten() {
        val geometry = WindowGeometry(x = 120f, y = 80f, width = 1000f, height = 700f, mode = WindowMode.MAXIMIZED)

        file.save(geometry)

        assertEquals(geometry, file.load())
        assertFalse(path.resolveSibling("window.json.tmp").exists())
    }

    @Test
    fun withNoFileThereIsTheDefaultWindow() {
        assertEquals(WindowGeometry(), file.load())
        assertNull(file.load().x)
    }

    @Test
    fun aFileThatCannotBeReadOrIsOfALaterVersionIsTheDefaultWindowAndIsWrittenOverNext() {
        path.writeText("not json")
        assertEquals(WindowGeometry(), file.load())

        path.writeText("""{"version": 99, "x": 5, "width": 800}""")
        assertEquals(WindowGeometry(), file.load())

        file.save(WindowGeometry(x = 1f, y = 2f))
        assertEquals(1f, file.load().x)
    }

    @Test
    fun whatALaterVersionAddedToAFileOfOurVersionIsIgnored() {
        path.writeText("""{"version": 1, "width": 900, "somethingNew": true}""")

        assertEquals(900f, file.load().width)
    }

    @Test
    fun theWindowIsNotKeptInTheStateFile() {
        val directories = KashDirectories(directory)

        assertNotEquals(directories.stateFile, directories.windowFile)
        assertEquals("window.json", directories.windowFile.fileName.toString())
    }

    @Test
    fun twoWritersAtOnceLeaveAWholeFile() {
        val geometry = WindowGeometry(width = 900f, height = 600f)
        val failures = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        val writers = List(2) {
            Thread {
                try {
                    repeat(300) { file.save(geometry) }
                } catch (e: Throwable) {
                    failures += e
                }
            }.apply { start() }
        }
        writers.forEach { it.join() }

        assertEquals(emptyList(), failures.toList())
        assertEquals(geometry, file.load())
    }
}
