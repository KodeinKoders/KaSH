package app.kash.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path

/** Where a plugin that the user installed comes from: what it is installed from again when it is updated. */
@Serializable
public sealed interface PluginSource {
    /** Whether KaSH can watch it for a new version: a folder or an address, not a zip, which is copied once. */
    public val watchable: Boolean

    /** A zip, copied when the plugin was installed: its path is only kept to say where the plugin came from. */
    @Serializable
    @SerialName("zip")
    public data class Zip(val path: String) : PluginSource {
        override val watchable: Boolean get() = false
    }

    /** A folder of the plugin's files, copied, and read again to update the plugin (every 10 s when it is watched). */
    @Serializable
    @SerialName("folder")
    public data class Folder(val path: String) : PluginSource {
        override val watchable: Boolean get() = true
    }

    /** An address of a zip or of a `manifest.json` (its files next to it), downloaded, and again to update the plugin (every hour when watched). */
    @Serializable
    @SerialName("url")
    public data class Url(val url: String) : PluginSource {
        override val watchable: Boolean get() = true
    }
}

/**
 * A plugin that the user installed: its files are in its own folder of KaSH's ([KashDirectories.plugin]).
 *
 * @property watch Whether KaSH looks for a new version at its [source] (a folder or an address).
 * @property version The installed version, as its manifest says it: [versionCode] is the one that a new version is compared to.
 */
@Serializable
public data class InstalledPlugin(
    val id: String,
    val source: PluginSource,
    val watch: Boolean = false,
    val version: String,
    val versionCode: Int,
)

/** What `plugins.json` holds: the plugins that the user installed, in the order they were installed. */
@Serializable
internal data class InstalledPluginsContent(val version: Int = 1, val plugins: List<InstalledPlugin> = emptyList())

/** The file that the installed plugins are kept in ([KashDirectories.pluginsFile]), as JSON. */
public class InstalledPluginsFile(path: Path) {
    private val file = JsonFile(path, InstalledPluginsContent.serializer(), ::InstalledPluginsContent, "no installed plugin is loaded")

    /**
     * The installed plugins, or none when there is no file or it cannot be read. One that cannot be read (a kind of source that this version does
     * not know) is set aside ([JsonFile]) before the next installation saves over it: the other plugins are still in it.
     */
    public fun load(): List<InstalledPlugin> = file.load().plugins

    /** Writes [plugins] durably: a crash while writing leaves the list before. */
    public fun save(plugins: List<InstalledPlugin>) {
        file.save(InstalledPluginsContent(plugins = plugins))
    }
}

/** What tells a new version of an installed plugin. */
public object PluginUpdates {
    /** Whether [manifest] is a newer version of [installed]: the same plugin, of a greater `versionCode`. */
    public fun isNewer(installed: InstalledPlugin, manifest: PluginManifest): Boolean =
        manifest.id == installed.id && manifest.versionCode > installed.versionCode
}
