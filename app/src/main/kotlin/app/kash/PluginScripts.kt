package app.kash

import app.kash.core.Plugin
import app.kash.core.PluginOptions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The script that a service of a plugin gets in each of its documents: the API of the plugins (`plugin-api.js`, see `PLUGINS.adoc`), given the
 * plugin, the service's options and KaSH's language, and the plugin's script, given `kash`.
 */
object PluginScripts {
    private val api: String by lazy {
        requireNotNull(PluginScripts::class.java.getResource("/app/kash/plugin-api.js")) { "plugin-api.js is not in the resources" }.readText()
    }

    /** The API version that `plugin-api.js` implements. */
    private const val API_VERSION = 3

    /** The script of a service of [plugin] whose options have [values], for an interface in [language] (`fr`, `en`). */
    fun of(plugin: Plugin, values: Map<String, String>, language: String): String {
        val manifest = plugin.manifest
        val config = JsonObject(
            mapOf(
                "apiVersion" to JsonPrimitive(API_VERSION),
                "plugin" to JsonObject(mapOf("id" to JsonPrimitive(manifest.id), "version" to JsonPrimitive(manifest.version))),
                "options" to PluginOptions.forScript(manifest, values),
                "language" to JsonPrimitive(language),
                "matches" to JsonArray(manifest.matches.map(::JsonPrimitive)),
            ),
        )
        // The API is the body of a function of `config` and `run`, and the plugin's script the body of `run`: neither one's names leak into the page,
        // and a script that ends with a line comment still closes its function (the line breaks).
        return "(function (config, run) {\n$api\n})($config, function (kash) {\n${plugin.script}\n});\n"
    }
}
