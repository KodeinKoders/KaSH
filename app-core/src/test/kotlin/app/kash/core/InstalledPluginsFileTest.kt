package app.kash.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstalledPluginsFileTest {
    private val directory: Path = Files.createTempDirectory("kash-plugins")
    private val path = directory.resolve("plugins.json")
    private val file = InstalledPluginsFile(path)

    @AfterTest
    fun cleanUp() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun thePluginsAreReadBackAsTheyWereWritten() {
        val plugins = listOf(
            InstalledPlugin("my-chat", PluginSource.Folder("/Users/me/my-chat"), watch = true, version = "1.2.0", versionCode = 4),
            InstalledPlugin("other", PluginSource.Url("https://example.com/other.zip"), version = "2", versionCode = 2),
            InstalledPlugin("zipped", PluginSource.Zip("/Users/me/zipped.zip"), version = "1", versionCode = 1),
        )
        file.save(plugins)
        assertEquals(plugins, file.load())
    }

    @Test
    fun noFileOrABrokenOneIsNoPlugin() {
        assertEquals(emptyList(), file.load())
        path.writeText("{not json")
        assertEquals(emptyList(), file.load())
        // Set aside, so that the next installation does not save over the plugins that it lists.
        val aside = Files.list(path.parent).use { files -> files.filter { it.fileName.toString().startsWith("${path.fileName}.unreadable-") }.toList() }
        assertEquals("{not json", aside.single().readText())
    }

    @Test
    fun aGreaterVersionCodeOfTheSamePluginIsNewer() {
        val installed = InstalledPlugin("my-chat", PluginSource.Folder("/x"), version = "1.2.0", versionCode = 4)
        fun manifest(id: String, code: Int) = PluginManifest(
            manifestVersion = 1, id = id, version = "x", versionCode = code, name = LocalizedText(mapOf("en" to "My chat")),
            square = PluginSquare("MC", "#336699"), url = "https://chat.example.com/", matches = listOf("https://chat.example.com/*"), script = "plugin.js",
        )
        assertTrue(PluginUpdates.isNewer(installed, manifest("my-chat", 5)))
        assertFalse(PluginUpdates.isNewer(installed, manifest("my-chat", 4)))
        assertFalse(PluginUpdates.isNewer(installed, manifest("my-chat", 3)))
        assertFalse(PluginUpdates.isNewer(installed, manifest("another", 9)))
    }

    @Test
    fun aPluginsFolderIsNamedByItsId() {
        val directories = KashDirectories(directory)
        assertEquals(directory.resolve("plugins").resolve("my-chat"), directories.plugin("my-chat"))
        assertEquals(null, directories.plugin("../profiles"))
        assertEquals(null, directories.plugin(".staging-x"))
    }
}
