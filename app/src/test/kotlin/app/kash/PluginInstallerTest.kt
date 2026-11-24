package app.kash

import app.kash.core.KashDirectories
import app.kash.core.PluginPackageException
import app.kash.core.PluginSource
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginInstallerTest {
    private val root: Path = Files.createTempDirectory("kash-installer")
    private val directories = KashDirectories(root.resolve("support"))
    private val installer = PluginInstaller(directories, setOf("slack"))

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun manifest(id: String = "my-chat", code: Int = 1) = """
        {
          "manifestVersion": 1, "id": "$id", "version": "1.$code", "versionCode": $code, "name": "My chat",
          "square": {"text": "MC", "color": "#336699"},
          "url": "https://chat.example.com/", "matches": ["https://chat.example.com/*"], "script": "plugin.js"
        }
    """.trimIndent()

    private fun files(id: String = "my-chat", code: Int = 1) = mapOf("manifest.json" to manifest(id, code), "plugin.js" to "kash.watch(() => []); // $code")

    private fun folder(code: Int = 1): Path {
        val folder = root.resolve("source")
        Files.createDirectories(folder)
        files(code = code).forEach { (name, text) -> folder.resolve(name).writeText(text) }
        return folder
    }

    private fun zip(entries: Map<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    @Test
    fun aFolderIsCopiedLoadedWatchedAndUpdated() {
        val source = PluginSource.Folder(folder().toString())
        installer.install(installer.read(source), source, watch = true)
        assertTrue(directories.plugin("my-chat")!!.resolve("plugin.js").exists())
        assertEquals(listOf("my-chat"), installer.plugins().map { it.id })
        // What a new KaSH reads back.
        assertEquals(installer.installed, PluginInstaller(directories, emptySet()).installed)

        folder(code = 2)
        assertEquals(2, installer.readManifest(source).versionCode)
        installer.install(installer.read(source), source, watch = true)
        assertEquals(2, installer.installed.single().versionCode)
        assertTrue("// 2" in directories.plugin("my-chat")!!.resolve("plugin.js").readText())
        // Nothing of the installation is left next to the plugin.
        assertEquals(listOf("my-chat"), Files.list(directories.plugins).use { stream -> stream.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun anInstallationCutShortIsPutRightAtTheNextLaunch() {
        val source = PluginSource.Folder(folder().toString())
        installer.install(installer.read(source), source, watch = true)
        val plugins = directories.plugins
        // Cut between its two moves: the plugin's folder moved aside, the new one not in its place yet.
        Files.move(plugins.resolve("my-chat"), plugins.resolve(".previous-my-chat"))
        Files.createDirectories(plugins.resolve(".staging-other"))
        assertEquals(listOf("my-chat"), PluginInstaller(directories, emptySet()).plugins().map { it.id })
        assertEquals(listOf("my-chat"), Files.list(plugins).use { stream -> stream.map { it.fileName.toString() }.toList() })

        // Cut after them: what was moved aside goes.
        Files.createDirectories(plugins.resolve(".previous-my-chat"))
        PluginInstaller(directories, emptySet())
        assertEquals(listOf("my-chat"), Files.list(plugins).use { stream -> stream.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun anAddressThatStallsInItsBodyFailsInTime() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val release = java.util.concurrent.CountDownLatch(1)
        // Its headers, a byte of its body, then nothing.
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(200, 1000)
            exchange.responseBody.write(byteArrayOf('P'.code.toByte()))
            exchange.responseBody.flush()
            release.await(10, java.util.concurrent.TimeUnit.SECONDS)
            exchange.close()
        }
        server.start()
        try {
            val stalling = PluginInstaller(directories, emptySet(), downloadTimeout = java.time.Duration.ofMillis(300))
            val started = System.nanoTime()
            assertFailsWith<java.io.IOException> { stalling.read(PluginSource.Url("http://127.0.0.1:${server.address.port}/plugin.zip")) }
            assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000)
        } finally {
            release.countDown()
            server.stop(0)
        }
    }

    @Test
    fun aZipIsCopiedAndNotWatched() {
        val path = root.resolve("my-chat.zip")
        path.writeBytes(zip(files().mapKeys { "my-chat/${it.key}" }))
        val source = PluginSource.Zip(path.toString())
        val installed = installer.install(installer.read(source), source, watch = true)
        assertFalse(installed.watch)
        assertEquals("my-chat", installer.plugins().single().id)
    }

    @Test
    fun anAddressGivesAZipOrAManifestWithItsFiles() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val served = mapOf("/plugin.zip" to zip(files(code = 3)), "/p/manifest.json" to manifest(code = 4).encodeToByteArray(), "/p/plugin.js" to "// 4".encodeToByteArray())
        server.createContext("/") { exchange ->
            val body = served[exchange.requestURI.path]
            exchange.sendResponseHeaders(if (body == null) 404 else 200, body?.size?.toLong() ?: -1)
            body?.let { exchange.responseBody.write(it) }
            exchange.close()
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            assertEquals(3, installer.read(PluginSource.Url("$base/plugin.zip")).manifest.versionCode)
            val fromManifest = installer.read(PluginSource.Url("$base/p/manifest.json"))
            assertEquals("// 4", fromManifest.files.getValue("plugin.js").decodeToString())
            assertEquals(4, installer.readManifest(PluginSource.Url("$base/p/manifest.json")).versionCode)
            assertFailsWith<Exception> { installer.read(PluginSource.Url("$base/missing.zip")) }
        } finally {
            server.stop(0)
        }
        // Not over plain http from another machine.
        assertFailsWith<PluginPackageException> { installer.read(PluginSource.Url("http://example.com/plugin.zip")) }
    }

    @Test
    fun aBuiltInIdIsRefusedAndAnUninstalledPluginIsGone() {
        val slack = root.resolve("slack.zip").apply { writeBytes(zip(files(id = "slack"))) }
        assertFailsWith<PluginPackageException> { installer.install(installer.read(PluginSource.Zip(slack.toString())), PluginSource.Zip(slack.toString()), watch = false) }

        val source = PluginSource.Folder(folder().toString())
        installer.install(installer.read(source), source, watch = true)
        installer.setWatch("my-chat", false)
        assertFalse(installer.installed.single().watch)
        installer.uninstall("my-chat")
        assertEquals(emptyList(), installer.installed)
        assertFalse(directories.plugins.resolve("my-chat").exists())
        assertEquals(emptyList(), installer.plugins())
    }
}
