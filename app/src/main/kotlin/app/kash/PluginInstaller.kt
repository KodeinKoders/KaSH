package app.kash

import app.kash.core.InstalledPlugin
import app.kash.core.InstalledPluginsFile
import app.kash.core.KashDirectories
import app.kash.core.Plugin
import app.kash.core.PluginManifest
import app.kash.core.PluginManifestException
import app.kash.core.PluginManifests
import app.kash.core.PluginPackage
import app.kash.core.PluginPackageException
import app.kash.core.PluginSource
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Duration
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.streams.toList

private val logger = LoggerFactory.getLogger(PluginInstaller::class.java)

/**
 * The plugins that the user installs: read from a zip, a folder or an address ([read]), copied into KaSH's own folder of plugins
 * ([KashDirectories.plugins], a folder each) and listed in `plugins.json` ([InstalledPluginsFile]). They are loaded at launch, after the built-in
 * ones ([plugins]).
 *
 * Everything here reads or writes files, or downloads: **never on Swing's thread**.
 */
class PluginInstaller(
    private val directories: KashDirectories,
    private val builtInIds: Set<String>,
    // How long a download may take, its body included.
    private val downloadTimeout: Duration = Duration.ofSeconds(30),
) {
    private val file = InstalledPluginsFile(directories.pluginsFile)

    /** The installed plugins, in the order they were installed. */
    @Volatile
    var installed: List<InstalledPlugin> = file.load()
        private set

    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build()

    init {
        recover()
    }

    /**
     * What an installation that was cut short (KaSH stopped, a crash) left next to the plugins, put right: a plugin's folder moved aside
     * (`.previous-<id>`) without the new one in its place goes back there, the plugin was not installed any more; one next to the plugin's folder,
     * the installation ended, is deleted, and so is a folder being made (`.staging-<id>`).
     */
    private fun recover() {
        val root = directories.plugins
        if (!Files.isDirectory(root)) return
        val left = Files.list(root).use { stream -> stream.toList() }
        for (path in left) {
            val name = path.fileName.toString()
            runCatching {
                when {
                    name.startsWith(STAGING) -> deleteFolder(path).also { logger.info("A plugin that was being installed is deleted: {}", name.removePrefix(STAGING)) }
                    name.startsWith(PREVIOUS) -> {
                        val id = name.removePrefix(PREVIOUS)
                        val folder = directories.plugin(id) ?: return@runCatching
                        if (Files.exists(folder)) {
                            deleteFolder(path)
                        } else {
                            Files.move(path, folder, StandardCopyOption.ATOMIC_MOVE)
                            logger.warn("The plugin {} is put back: its installation was cut short", id)
                        }
                    }
                }
            }.onFailure { logger.error("What an installation left cannot be put right: {}", name, it) }
        }
    }

    /** Every installed plugin that can be loaded from its folder: one that cannot is logged and left out. */
    fun plugins(): List<Plugin> = installed.mapNotNull { plugin ->
        runCatching { load(plugin.id) }.onFailure { logger.error("The installed plugin {} is not loaded", plugin.id, it) }.getOrNull()
    }

    /** The plugin [id] from its folder: its manifest, its script and its icon. */
    private fun load(id: String): Plugin {
        val folder = directories.plugin(id) ?: throw PluginPackageException("'$id' is not a plugin's id")
        val manifest = PluginManifests.parse(Files.readString(folder.resolve(PluginPackage.MANIFEST)))
        if (manifest.id != id) throw PluginManifestException("id", "'${manifest.id}' is in the folder '$id'")
        val script = Files.readString(folder.resolve(manifest.script))
        val icon = manifest.icon?.let { Files.readAllBytes(folder.resolve(it)) }
        return Plugin(manifest, script, icon)
    }

    /**
     * The plugin at [source], read whole: a zip, a folder, or an address of a zip or of a `manifest.json` (its files are then downloaded next to
     * it). [PluginPackageException] when it is not one, or not one that KaSH takes; [IOException] when it cannot be read.
     */
    fun read(source: PluginSource): PluginPackage = when (source) {
        is PluginSource.Zip -> PluginPackage.fromZip(readFile(Paths.get(source.path)))
        is PluginSource.Folder -> PluginPackage.fromFiles(readFolder(Paths.get(source.path)))
        is PluginSource.Url -> {
            val uri = webAddress(source.url)
            val content = download(uri)
            if (isZip(content)) PluginPackage.fromZip(content) else PluginPackage.fromFiles(manifestFiles(uri, content))
        }
    }

    /** Only the manifest at [source], to see whether it is a new version: the folder's, the address's, or the one in its zip. */
    fun readManifest(source: PluginSource): PluginManifest = when (source) {
        is PluginSource.Folder -> PluginPackage.manifestOf(readFile(Paths.get(source.path).resolve(PluginPackage.MANIFEST)))
        is PluginSource.Url -> {
            val content = download(webAddress(source.url))
            if (isZip(content)) PluginPackage.fromZip(content).manifest else PluginPackage.manifestOf(content)
        }
        is PluginSource.Zip -> read(source).manifest
    }

    /**
     * Installs [plugin], from [source]: its files go to a folder of their own next to the plugin's, which then takes the place of the plugin's
     * (the old one moved aside first, then deleted: a plugin of the same id is replaced, an update; one cut short between the two is put right at
     * the next launch, [recover]). A built-in plugin's id is refused.
     */
    @Synchronized
    fun install(plugin: PluginPackage, source: PluginSource, watch: Boolean): InstalledPlugin {
        if (plugin.id in builtInIds) throw PluginPackageException("'${plugin.id}' is the id of a plugin of KaSH")
        val folder = directories.plugin(plugin.id) ?: throw PluginPackageException("'${plugin.id}' is not a plugin's id")
        val staging = directories.plugins.resolve("$STAGING${plugin.id}")
        val previous = directories.plugins.resolve("$PREVIOUS${plugin.id}")
        deleteFolder(staging)
        deleteFolder(previous)
        for ((name, bytes) in plugin.files) {
            val target = staging.resolve(name).normalize()
            // PluginPackage checked the names; a name that would still leave the folder is not written.
            if (!target.startsWith(staging)) throw PluginPackageException("A file's name leaves the plugin's folder: '$name'")
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
        }
        if (Files.exists(folder)) Files.move(folder, previous, StandardCopyOption.ATOMIC_MOVE)
        Files.move(staging, folder, StandardCopyOption.ATOMIC_MOVE)
        deleteFolder(previous)
        val entry = InstalledPlugin(plugin.id, source, watch = watch && source.watchable, version = plugin.manifest.version, versionCode = plugin.manifest.versionCode)
        installed = installed.filter { it.id != plugin.id } + entry
        file.save(installed)
        logger.info("Plugin {} {} ({}) installed from a {}", plugin.id, plugin.manifest.version, plugin.manifest.versionCode, sourceKind(source))
        return entry
    }

    /** Deletes the plugin [id]'s files and its entry. */
    @Synchronized
    fun uninstall(id: String) {
        installed = installed.filter { it.id != id }
        file.save(installed)
        directories.plugin(id)?.let(::deleteFolder)
        logger.info("Plugin {} uninstalled", id)
    }

    /** Whether KaSH watches the plugin [id]'s source for a new version. */
    @Synchronized
    fun setWatch(id: String, watch: Boolean) {
        installed = installed.map { if (it.id == id) it.copy(watch = watch && it.source.watchable) else it }
        file.save(installed)
    }

    private fun readFile(path: Path): ByteArray {
        if (!Files.isRegularFile(path)) throw IOException("$path is not a file")
        if (Files.size(path) > PluginPackage.MAX_BYTES) throw PluginPackageException("${path.fileName} is bigger than ${PluginPackage.MAX_BYTES / 1024 / 1024} MB")
        return Files.readAllBytes(path)
    }

    // The regular files of [folder], by their paths in it; links are not followed (a plugin's files are its own).
    private fun readFolder(folder: Path): Map<String, ByteArray> {
        if (!Files.isDirectory(folder)) throw IOException("$folder is not a folder")
        val paths = Files.walk(folder).use { stream ->
            stream.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) && it.fileName.toString() != ".DS_Store" }.toList()
        }
        if (paths.size > PluginPackage.MAX_FILES) throw PluginPackageException("The folder has more than ${PluginPackage.MAX_FILES} files")
        return paths.associate { folder.relativize(it).invariantSeparatorsPathString to readFile(it) }
    }

    // An address that KaSH downloads a plugin from: https, or http on this machine (a plugin that is being written, served locally).
    private fun webAddress(url: String): URI {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: throw PluginPackageException("Not an address: $url")
        val local = uri.host in LOCAL_HOSTS
        if (uri.scheme != "https" && !(uri.scheme == "http" && local)) throw PluginPackageException("A plugin is downloaded over https (or http from this machine): $url")
        return uri
    }

    // The files of the manifest at [uri]: the manifest, and its script and its icon, downloaded next to it.
    private fun manifestFiles(uri: URI, manifest: ByteArray): Map<String, ByteArray> {
        val parsed = PluginPackage.manifestOf(manifest)
        val names = listOfNotNull(parsed.script, parsed.icon)
        return mapOf(PluginPackage.MANIFEST to manifest) + names.associateWith { download(webAddress(uri.resolve(it).toString())) }
    }

    // Without cookies, of at most PluginPackage.MAX_BYTES, in downloadTimeout at most (its body too).
    private fun download(uri: URI): ByteArray {
        val request = HttpRequest.newBuilder(uri).timeout(downloadTimeout).header("User-Agent", "KaSH").GET().build()
        val response = try {
            client.download(request, PluginPackage.MAX_BYTES.toLong(), downloadTimeout)
        } catch (e: TooBigException) {
            throw PluginPackageException("${uri.path} is bigger than ${PluginPackage.MAX_BYTES / 1024 / 1024} MB")
        }
        if (response.statusCode() !in 200..299) throw IOException("${uri.host} answered ${response.statusCode()} for ${uri.path}")
        return response.body()
    }

    private fun isZip(bytes: ByteArray): Boolean = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    // Deletes [folder] and what is in it, without following links (Files.walk does not): a link's target is not the plugin's.
    private fun deleteFolder(folder: Path) {
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) return
        Files.walk(folder).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private companion object {
        val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "[::1]")

        // The folders that an installation makes next to the plugin's: the new one, then the old one moved aside.
        const val STAGING = ".staging-"
        const val PREVIOUS = ".previous-"

        // What the log says of a source: not its path or its address, which may say who the user is.
        fun sourceKind(source: PluginSource) = when (source) {
            is PluginSource.Zip -> "zip"
            is PluginSource.Folder -> "folder"
            is PluginSource.Url -> "address"
        }
    }
}
