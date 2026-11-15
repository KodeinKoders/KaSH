package app.kash

import app.kash.core.Plugin
import app.kash.core.PluginManifestException
import app.kash.core.PluginManifests
import app.kash.core.PluginRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("app.kash.BuiltInPlugins")

/**
 * The plugins that KaSH has in its resources (`app/kash/plugins`): `index.json` lists them, in the order of the catalog, and each one is a folder of
 * that name with its `manifest.json`, its script and its icon. Plugins that the user installs are not loaded yet.
 */
object BuiltInPlugins {
    internal const val ROOT = "/app/kash/plugins"

    /** The ids of the built-in plugins, in the order of the catalog. */
    internal fun ids(): List<String> = Json.parseToJsonElement(read("index.json") ?: error("$ROOT/index.json is not in the resources"))
        .jsonArray.map { it.jsonPrimitive.content }

    /** Every built-in plugin that is valid: one that is not is logged and left out (a test of the build checks that none is). */
    fun registry(): PluginRegistry = PluginRegistry(ids().mapNotNull { id -> runCatching { load(id) }.onFailure { logger.error("The plugin {} is not loaded", id, it) }.getOrNull() })

    /** The plugin [id]: its manifest, checked, its script and its icon; its folder's name is its id. */
    internal fun load(id: String): Plugin {
        val manifest = PluginManifests.parse(read("$id/manifest.json") ?: error("$ROOT/$id/manifest.json is not in the resources"))
        if (manifest.id != id) throw PluginManifestException("id", "'${manifest.id}' is in the folder '$id'")
        val script = read("$id/${manifest.script}") ?: throw PluginManifestException("script", "$ROOT/$id/${manifest.script} is not in the resources")
        val icon = manifest.icon?.let { file ->
            BuiltInPlugins::class.java.getResource("$ROOT/$id/$file")?.readBytes() ?: throw PluginManifestException("icon", "$ROOT/$id/$file is not in the resources")
        }
        return Plugin(manifest, script, icon)
    }

    private fun read(path: String): String? = BuiltInPlugins::class.java.getResource("$ROOT/$path")?.readText()
}
