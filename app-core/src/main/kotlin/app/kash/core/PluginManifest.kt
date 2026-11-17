package app.kash.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A text of a manifest in several languages (`{"en": "Workspace", "fr": "Espace de travail"}`), or in one (`"Slack"`, kept under `en`).
 *
 * @property texts The text of each language, by its code (`en`, `fr`).
 */
@Serializable(with = LocalizedTextSerializer::class)
public data class LocalizedText(val texts: Map<String, String>) {
    /** The text in [language], else in English, else the first one. */
    public fun of(language: String): String = texts[language] ?: texts[ENGLISH] ?: texts.values.firstOrNull().orEmpty()

    internal companion object {
        const val ENGLISH = "en"
    }
}

/** A [LocalizedText] is a string or an object of strings. */
internal object LocalizedTextSerializer : KSerializer<LocalizedText> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): LocalizedText {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: throw SerializationException("A localized text is only read from JSON")
        return when (element) {
            is JsonPrimitive -> if (element.isString) LocalizedText(mapOf(LocalizedText.ENGLISH to element.content)) else throw SerializationException("A localized text is a string or an object of strings")
            is JsonObject -> LocalizedText(element.mapValues { (_, value) ->
                (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw SerializationException("A localized text is a string or an object of strings")
            })
            else -> throw SerializationException("A localized text is a string or an object of strings")
        }
    }

    override fun serialize(encoder: Encoder, value: LocalizedText) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("A localized text is only written as JSON")
        json.encodeJsonElement(JsonObject(value.texts.mapValues { JsonPrimitive(it.value) }))
    }
}

/** The colored square that stands for a service while its site's icon is not loaded: [text] (1 to 3 characters) on [color] (`#RRGGBB`). */
@Serializable
public data class PluginSquare(val text: String, val color: String)

/** A value that a `choice` option offers. */
@Serializable
public data class PluginChoice(val value: String, val label: LocalizedText)

/**
 * A field of the form that adds a service of a plugin: its value is saved with the service ([Service.options]), may be used in the plugin's URL
 * (`${id}`, for a text or a choice), and is given to its script (`kash.options`).
 */
@Serializable
public sealed interface PluginOption {
    /** What names it in the URL (`${workspace}`) and in the script (`kash.options.workspace`). */
    public val id: String
    public val label: LocalizedText

    /** A line under the field. */
    public val hint: LocalizedText?

    /**
     * A text field. What is typed is normalized ([PluginOptions.normalize]): [prefix] removed from its start, cut at [suffix], lowercased when
     * [lowercase]; it must then match [pattern] whole.
     *
     * @property prefix Shown before the field (`https://`), and removed from a value that starts with it (a pasted address).
     * @property suffix Shown after the field (`.slack.com`); a value is cut at its first occurrence.
     */
    @Serializable
    @SerialName("text")
    public data class Text(
        override val id: String,
        override val label: LocalizedText,
        override val hint: LocalizedText? = null,
        val required: Boolean = false,
        val default: String? = null,
        val placeholder: LocalizedText? = null,
        val prefix: String? = null,
        val suffix: String? = null,
        val lowercase: Boolean = false,
        val pattern: String? = null,
        val patternError: LocalizedText? = null,
        val maxLength: Int? = null,
    ) : PluginOption

    /** One of [choices]: [default], else the first one. */
    @Serializable
    @SerialName("choice")
    public data class Choice(
        override val id: String,
        override val label: LocalizedText,
        override val hint: LocalizedText? = null,
        val choices: List<PluginChoice>,
        val default: String? = null,
    ) : PluginOption

    /** A check box: [default] when the service is added. Not in the URL. */
    @Serializable
    @SerialName("boolean")
    public data class Flag(
        override val id: String,
        override val label: LocalizedText,
        override val hint: LocalizedText? = null,
        val default: Boolean = false,
    ) : PluginOption
}

/**
 * What a plugin is: its manifest, `manifest.json` (see `PLUGINS.adoc` and `manifest.schema.json`), read by [PluginManifests.parse].
 *
 * @property schema The JSON Schema that editors check the file with (`$schema`): KaSH does not read it.
 * @property manifestVersion The version of this format: 1.
 * @property id What names the plugin, in the services that use it ([Service.plugin]).
 * @property version The plugin's own version, as it is shown and given to its script: any text.
 * @property versionCode What tells a newer version of the plugin: an integer, at least 1, that each version makes greater (as Android's), which
 * KaSH compares to propose an update.
 * @property icon The file of its icon, next to the manifest: what the tiles of its services show by default ([ServiceIcon.Plugin]).
 * @property url The address of a service of the plugin, where `${id}` is the value of an option.
 * @property matches The addresses of the pages where its script runs, in the top frame: `*` matches any run of characters.
 * @property script The file of its script, next to the manifest.
 * @property openInKash The addresses of other sites than the service's that its pages open in KaSH, in the service's profile, rather than in the
 * system's browser: a sign-in with another site's account that KaSH does not know (Instagram's with Facebook: the addresses under `https://www.facebook.com/oidc/`).
 * `*` matches any run of characters.
 */
@Serializable
public data class PluginManifest(
    @SerialName("\$schema") val schema: String? = null,
    val manifestVersion: Int,
    val id: String,
    val version: String,
    val versionCode: Int,
    val name: LocalizedText,
    val description: LocalizedText? = null,
    val square: PluginSquare,
    val icon: String? = null,
    val url: String,
    val matches: List<String>,
    val script: String,
    val options: List<PluginOption> = emptyList(),
    val openInKash: List<String> = emptyList(),
) {
    public companion object {
        /** The version of the format that KaSH reads. */
        public const val VERSION: Int = 1
    }
}

/** A manifest that KaSH does not accept, at [path] (`options[0].pattern`, empty for the whole file). */
public class PluginManifestException(public val path: String, message: String) : Exception(if (path.isEmpty()) message else "$path: $message")

/** Reads and checks the manifests of plugins. */
public object PluginManifests {
    // Strict: a field that KaSH does not know is a mistake of the manifest (a typo), which would otherwise be ignored silently.
    private val json = Json {
        ignoreUnknownKeys = false
    }

    private val PLUGIN_ID = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    private val OPTION_ID = Regex("[a-zA-Z][a-zA-Z0-9]*")
    private val SCRIPT = Regex("[A-Za-z0-9_][A-Za-z0-9._-]*\\.js")
    private val COLOR = Regex("#[0-9A-Fa-f]{6}")

    // An address of openInKash: of the web, with a host, which may hold a `*` (`https://*.example.com/login/*`).
    private val WEB_ADDRESS = Regex("https?://[^/\\s]+(/\\S*)?", RegexOption.IGNORE_CASE)

    /** An image that KaSH can draw (Skia: not SVG), in the plugin's folder. */
    private val ICON = Regex("[A-Za-z0-9_][A-Za-z0-9._-]*\\.(png|jpe?g|gif|webp|ico)", RegexOption.IGNORE_CASE)

    /** The `${id}` of a URL template. */
    internal val PLACEHOLDER = Regex("\\$\\{([^}]*)}")

    /** The manifest in [text], checked against every rule of the format, or [PluginManifestException]. */
    public fun parse(text: String): PluginManifest {
        val manifest = try {
            json.decodeFromString(PluginManifest.serializer(), text)
        } catch (e: SerializationException) {
            throw PluginManifestException("", e.message ?: "Not a manifest")
        } catch (e: IllegalArgumentException) {
            throw PluginManifestException("", e.message ?: "Not a manifest")
        }
        check(manifest)
        return manifest
    }

    private fun check(manifest: PluginManifest) {
        rule(manifest.manifestVersion == PluginManifest.VERSION, "manifestVersion", "KaSH reads the version ${PluginManifest.VERSION}, not ${manifest.manifestVersion}")
        rule(PLUGIN_ID.matches(manifest.id), "id", "lowercase letters and digits, in words separated by hyphens: '${manifest.id}'")
        rule(manifest.version.isNotBlank(), "version", "empty")
        rule(manifest.versionCode >= 1, "versionCode", "at least 1: ${manifest.versionCode}")
        text(manifest.name, "name")
        manifest.description?.let { text(it, "description") }
        val squareText = manifest.square.text
        rule(squareText.codePointCount(0, squareText.length) in 1..3, "square.text", "1 to 3 characters")
        rule(COLOR.matches(manifest.square.color), "square.color", "a color #RRGGBB: '${manifest.square.color}'")
        manifest.icon?.let { rule(ICON.matches(it), "icon", "a PNG, JPEG, GIF, WebP or ICO image of the plugin's folder: '$it'") }
        rule(manifest.matches.isNotEmpty(), "matches", "empty")
        manifest.matches.forEachIndexed { index, match -> rule(match.isNotBlank(), "matches[$index]", "empty") }
        rule(SCRIPT.matches(manifest.script), "script", "a .js file of the plugin's folder: '${manifest.script}'")
        manifest.openInKash.forEachIndexed { index, address ->
            rule(WEB_ADDRESS.matches(address), "openInKash[$index]", "an address of the web, http(s)://, with a host: '$address'")
        }

        val ids = mutableSetOf<String>()
        manifest.options.forEachIndexed { index, option ->
            val path = "options[$index]"
            rule(OPTION_ID.matches(option.id), "$path.id", "letters and digits, starting with a letter: '${option.id}'")
            rule(ids.add(option.id), "$path.id", "'${option.id}' is already an option")
            text(option.label, "$path.label")
            option.hint?.let { text(it, "$path.hint") }
            when (option) {
                is PluginOption.Text -> {
                    option.placeholder?.let { text(it, "$path.placeholder") }
                    option.patternError?.let { text(it, "$path.patternError") }
                    option.pattern?.let { pattern ->
                        rule(runCatching { Regex(pattern) }.isSuccess, "$path.pattern", "not a regular expression: '$pattern'")
                    }
                    option.maxLength?.let { rule(it > 0, "$path.maxLength", "at least 1") }
                    option.default?.let { default ->
                        rule(PluginOptions.error(option, PluginOptions.normalize(option, default)) == null, "$path.default", "not a valid value: '$default'")
                    }
                }
                is PluginOption.Choice -> {
                    rule(option.choices.isNotEmpty(), "$path.choices", "empty")
                    val values = mutableSetOf<String>()
                    option.choices.forEachIndexed { choiceIndex, choice ->
                        rule(choice.value.isNotEmpty(), "$path.choices[$choiceIndex].value", "empty")
                        rule(values.add(choice.value), "$path.choices[$choiceIndex].value", "'${choice.value}' is already a choice")
                        text(choice.label, "$path.choices[$choiceIndex].label")
                    }
                    option.default?.let { rule(it in values, "$path.default", "not one of the choices: '$it'") }
                }
                is PluginOption.Flag -> Unit
            }
        }

        // Every ${id} of the URL is a text or a choice that always has a value, and the URL is an address of the web with any value.
        for (match in PLACEHOLDER.findAll(manifest.url)) {
            val id = match.groupValues[1]
            val option = manifest.options.firstOrNull { it.id == id } ?: throw PluginManifestException("url", "\${$id} is not an option")
            when (option) {
                is PluginOption.Flag -> throw PluginManifestException("url", "\${$id} is a boolean, which cannot be in the URL")
                is PluginOption.Text -> rule(option.required || option.default != null, "url", "\${$id} is neither required nor has a default")
                is PluginOption.Choice -> Unit
            }
        }
        val sample = manifest.options.associate { option ->
            option.id to when (option) {
                is PluginOption.Text -> option.default ?: "sample"
                is PluginOption.Choice -> option.default ?: option.choices.first().value
                is PluginOption.Flag -> option.default.toString()
            }
        }
        rule(PluginOptions.url(manifest.url, sample) != null, "url", "not an http(s) address with a host: '${manifest.url}'")
    }

    private fun text(text: LocalizedText, path: String) {
        rule(text.texts.isNotEmpty(), path, "no text")
        text.texts.forEach { (language, value) ->
            rule(language.isNotBlank(), path, "a text without its language")
            rule(value.isNotBlank(), "$path.$language", "empty")
        }
    }

    private fun rule(holds: Boolean, path: String, message: String) {
        if (!holds) throw PluginManifestException(path, message)
    }
}
