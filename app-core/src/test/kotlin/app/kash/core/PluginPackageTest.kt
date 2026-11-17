package app.kash.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PluginPackageTest {
    private val manifest = """
        {
          "manifestVersion": 1, "id": "my-chat", "version": "1.2.0", "versionCode": 4, "name": "My chat",
          "square": {"text": "MC", "color": "#336699"}, "icon": "icon.png",
          "url": "https://chat.example.com/", "matches": ["https://chat.example.com/*"], "script": "plugin.js"
        }
    """.trimIndent()

    private val files = mapOf(
        "manifest.json" to manifest.encodeToByteArray(),
        "plugin.js" to "kash.watch(() => []);".encodeToByteArray(),
        "icon.png" to byteArrayOf(1, 2, 3),
    )

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun refused(block: () -> Unit): String = assertFailsWith<PluginPackageException> { block() }.message.orEmpty()

    @Test
    fun aPluginIsItsFiles() {
        val plugin = PluginPackage.fromFiles(files + ("docs/README.md" to byteArrayOf()))
        assertEquals("my-chat", plugin.id)
        assertEquals(4, plugin.manifest.versionCode)
        assertEquals(setOf("manifest.json", "plugin.js", "icon.png", "docs/README.md"), plugin.files.keys)
    }

    @Test
    fun aZipHasThePluginAtItsRootOrInItsOnlyFolder() {
        assertEquals(files.keys, PluginPackage.fromZip(zip(files)).files.keys)
        // A zipped folder, as the Finder makes it, with its own files.
        val folder = files.mapKeys { "my-chat/${it.key}" } + ("__MACOSX/my-chat/._plugin.js" to byteArrayOf(0)) + ("my-chat/.DS_Store" to byteArrayOf(0))
        assertEquals(files.keys, PluginPackage.fromZip(zip(folder)).files.keys)
    }

    @Test
    fun aFileThatLeavesThePluginsFolderIsRefused() {
        assertTrue("leaves" in refused { PluginPackage.fromZip(zip(files + ("../evil.js" to byteArrayOf()))) })
        assertTrue("leaves" in refused { PluginPackage.fromZip(zip(files + ("/etc/evil" to byteArrayOf()))) })
        assertTrue("leaves" in refused { PluginPackage.fromFiles(files + ("a/../../evil" to byteArrayOf())) })
    }

    @Test
    fun theManifestTheScriptAndTheIconAreThere() {
        assertTrue("No manifest.json" in refused { PluginPackage.fromFiles(files - "manifest.json") })
        assertTrue("script" in refused { PluginPackage.fromFiles(files - "plugin.js") })
        assertTrue("icon" in refused { PluginPackage.fromFiles(files - "icon.png") })
        val withoutCode = manifest.replace("\"versionCode\": 4, ", "")
        assertTrue(refused { PluginPackage.fromFiles(files + ("manifest.json" to withoutCode.encodeToByteArray())) }.startsWith("manifest.json"))
    }

    @Test
    fun whatIsNotAZipOrTooBigIsRefused() {
        refused { PluginPackage.fromZip("not a zip".encodeToByteArray()) }
        refused { PluginPackage.fromFiles(files + ("big.bin" to ByteArray(PluginPackage.MAX_BYTES))) }
        refused { PluginPackage.fromFiles(files + (1..PluginPackage.MAX_FILES).associate { "f$it.txt" to byteArrayOf() }) }
    }
}
