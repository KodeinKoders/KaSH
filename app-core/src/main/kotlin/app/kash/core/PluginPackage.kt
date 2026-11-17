package app.kash.core

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** A plugin that cannot be installed: its files are not one, or not ones that KaSH takes. */
public class PluginPackageException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The files of a plugin that the user installs (a zip, a folder, what an address gives), checked: its manifest ([PluginManifests.parse]), its
 * script and its icon are there, and every file has a name of its own folder. KaSH copies all of them into its own folder of plugins.
 *
 * @property files Every file, by its path in the plugin's folder (`manifest.json`, `plugin.js`, `images/icon.png`).
 */
public class PluginPackage private constructor(public val manifest: PluginManifest, public val files: Map<String, ByteArray>) {
    public val id: String get() = manifest.id

    public companion object {
        /** The name of the manifest, in the plugin's folder. */
        public const val MANIFEST: String = "manifest.json"

        /** How many files a plugin has at most. */
        public const val MAX_FILES: Int = 200

        /** How big a plugin's files are at most, all together (and its zip). */
        public const val MAX_BYTES: Int = 10 * 1024 * 1024

        // What macOS's Finder adds to the zips it makes, which no plugin needs.
        private val SYSTEM_FILES = listOf("__MACOSX/", ".DS_Store")

        /**
         * The plugin in a zip: its files at the zip's root, or in its only folder (a zipped folder). An entry whose name leaves the folder (`..`,
         * an absolute path) is refused: it would be written elsewhere than in the plugin's folder.
         */
        public fun fromZip(zip: ByteArray): PluginPackage {
            if (zip.size > MAX_BYTES) throw PluginPackageException("The zip is bigger than ${MAX_BYTES / 1024 / 1024} MB")
            val files = LinkedHashMap<String, ByteArray>()
            var total = 0L
            try {
                ZipInputStream(ByteArrayInputStream(zip)).use { input ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val name = entry.name.replace('\\', '/')
                        if (SYSTEM_FILES.any { name.startsWith(it) || name.endsWith("/$it") || name == it }) continue
                        checkName(name)
                        if (files.size >= MAX_FILES) throw PluginPackageException("The zip has more than $MAX_FILES files")
                        val bytes = input.readNBytes(MAX_BYTES + 1 - total.toInt())
                        total += bytes.size
                        if (total > MAX_BYTES) throw PluginPackageException("The plugin's files are bigger than ${MAX_BYTES / 1024 / 1024} MB")
                        files[name] = bytes
                    }
                }
            } catch (e: ZipException) {
                throw PluginPackageException("Not a zip: ${e.message}", e)
            } catch (e: IOException) {
                throw PluginPackageException("The zip cannot be read: ${e.message}", e)
            }
            if (files.isEmpty()) throw PluginPackageException("The zip is empty, or is not a zip")
            return fromFiles(withoutRootFolder(files))
        }

        /** The plugin of [files], by their paths in its folder. */
        public fun fromFiles(files: Map<String, ByteArray>): PluginPackage {
            if (files.size > MAX_FILES) throw PluginPackageException("The plugin has more than $MAX_FILES files")
            if (files.values.sumOf { it.size.toLong() } > MAX_BYTES) throw PluginPackageException("The plugin's files are bigger than ${MAX_BYTES / 1024 / 1024} MB")
            files.keys.forEach(::checkName)
            val manifest = manifestOf(files[MANIFEST] ?: throw PluginPackageException("No $MANIFEST"))
            if (manifest.script !in files) throw PluginPackageException("$MANIFEST: script: ${manifest.script} is not in the plugin")
            manifest.icon?.let { if (it !in files) throw PluginPackageException("$MANIFEST: icon: $it is not in the plugin") }
            return PluginPackage(manifest, files.toMap())
        }

        /** The manifest in [bytes], checked; [PluginPackageException] with the field that is wrong otherwise. */
        public fun manifestOf(bytes: ByteArray): PluginManifest = try {
            PluginManifests.parse(bytes.decodeToString())
        } catch (e: PluginManifestException) {
            throw PluginPackageException("$MANIFEST: ${e.message}", e)
        }

        // The files of a zipped folder are in that folder: its manifest is not at the root, and every file is under the same first folder.
        private fun withoutRootFolder(files: Map<String, ByteArray>): Map<String, ByteArray> {
            if (MANIFEST in files) return files
            val roots = files.keys.map { it.substringBefore('/', missingDelimiterValue = "") }.toSet()
            val root = roots.singleOrNull()?.takeIf { it.isNotEmpty() } ?: return files
            return files.mapKeys { (name, _) -> name.removePrefix("$root/") }
        }

        // A path of the plugin's folder: relative, without `..`, `.` or an empty part, without a drive.
        private fun checkName(name: String) {
            val parts = name.split('/')
            val valid = name.isNotEmpty() && !name.startsWith('/') && ':' !in name && parts.none { it.isEmpty() || it == "." || it == ".." }
            if (!valid) throw PluginPackageException("A file's name leaves the plugin's folder: '$name'")
        }
    }
}
