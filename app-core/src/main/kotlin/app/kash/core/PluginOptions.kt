package app.kash.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

/** Why the value of a text option is not accepted. */
public enum class OptionError {
    /** The option is required, and has no value. */
    MISSING,

    /** The value is longer than the option's `maxLength`. */
    TOO_LONG,

    /** The value does not match the option's `pattern`. */
    PATTERN,
}

/** The values of a plugin's options: what is typed, what is saved ([Service.options]), and the URL they make. */
public object PluginOptions {
    /**
     * What is typed in [option]'s field, as it is saved: without spaces around it, without the option's prefix at its start (`https://` of a pasted
     * address, whatever its case), cut at its suffix (`.slack.com/…`), lowercased when the option says so. `https://Mon-Equipe.slack.com/x` is
     * `mon-equipe` for Slack's workspace.
     */
    public fun normalize(option: PluginOption.Text, typed: String): String {
        var value = typed.trim()
        option.prefix?.takeIf { it.isNotEmpty() && value.startsWith(it, ignoreCase = true) }?.let { value = value.substring(it.length) }
        option.suffix?.takeIf { it.isNotEmpty() }?.let { suffix ->
            val at = value.indexOf(suffix, ignoreCase = true)
            if (at >= 0) value = value.substring(0, at)
        }
        if (option.lowercase) value = value.lowercase()
        return value
    }

    /** Why the normalized [value] of [option] is not accepted, or null when it is. An empty value is accepted when the option is not required. */
    public fun error(option: PluginOption.Text, value: String): OptionError? {
        if (value.isEmpty()) return if (option.required) OptionError.MISSING else null
        option.maxLength?.let { if (value.length > it) return OptionError.TOO_LONG }
        option.pattern?.let { if (!matchesWithin(it, value)) return OptionError.PATTERN }
        return null
    }

    // The options' patterns, compiled once: they are checked at each key typed, on Swing's thread.
    private val patterns = java.util.concurrent.ConcurrentHashMap<String, Regex>()

    // How many characters a pattern may read of a value before it is taken for one that does not match.
    private const val MAX_READS = 1_000_000

    /**
     * Whether [value] matches [pattern], read [MAX_READS] characters at most: an installed plugin's pattern that backtracks without end
     * (`(a+)+$`) froze KaSH's window at each key typed in its field; it is a value that does not match.
     */
    private fun matchesWithin(pattern: String, value: String): Boolean {
        val regex = patterns.getOrPut(pattern) { Regex(pattern) }
        return try {
            regex.matches(Bounded(value, intArrayOf(MAX_READS)))
        } catch (e: TooLong) {
            false
        }
    }

    private class TooLong : RuntimeException(null, null, false, false)

    // [text], which throws once [left] reads of its characters are spent (shared with its parts).
    private class Bounded(private val text: CharSequence, private val left: IntArray) : CharSequence {
        override val length: Int get() = text.length
        override fun get(index: Int): Char {
            if (--left[0] < 0) throw TooLong()
            return text[index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = Bounded(text.subSequence(startIndex, endIndex), left)
        override fun toString(): String = text.toString()
    }

    /** The value of every option of [manifest]: the one in [saved], else its default. */
    public fun values(manifest: PluginManifest, saved: Map<String, String> = emptyMap()): Map<String, String> =
        manifest.options.associate { option -> option.id to (saved[option.id] ?: defaultOf(option)) }

    private fun defaultOf(option: PluginOption): String = when (option) {
        is PluginOption.Text -> option.default?.let { normalize(option, it) }.orEmpty()
        is PluginOption.Choice -> option.default ?: option.choices.first().value
        is PluginOption.Flag -> option.default.toString()
    }

    /** Whether every option of [manifest] has an accepted value in [values]. */
    public fun isValid(manifest: PluginManifest, values: Map<String, String>): Boolean = manifest.options.all { option ->
        val value = values[option.id] ?: return@all false
        when (option) {
            is PluginOption.Text -> error(option, value) == null
            is PluginOption.Choice -> option.choices.any { it.value == value }
            is PluginOption.Flag -> value == "true" || value == "false"
        }
    }

    /**
     * The address of a service of [manifest] with [values], or null when a value is not accepted. An option that is empty (an optional one that
     * the user emptied) is its default there: the address was null, Add stayed disabled without a word, and an edited service kept its old one.
     */
    public fun resolveUrl(manifest: PluginManifest, values: Map<String, String>): String? {
        if (!isValid(manifest, values)) return null
        val filled = manifest.options.associate { option -> option.id to (values[option.id]?.takeIf { it.isNotEmpty() } ?: defaultOf(option)) }
        return url(manifest.url, values + filled)
    }

    /** [template] with the `${id}` replaced by their values, encoded as components of a URI; null when one has none, or when it is not an http(s) address. */
    internal fun url(template: String, values: Map<String, String>): String? {
        var missing = false
        val url = PluginManifests.PLACEHOLDER.replace(template) { match ->
            val value = values[match.groupValues[1]]
            if (value.isNullOrEmpty()) {
                missing = true
                ""
            } else {
                encode(value)
            }
        }
        if (missing) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        return url.takeIf { uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty() }
    }

    /** [value] as a component of a URI: what is not a letter, a digit or one of `-._~` is percent-encoded, in UTF-8. */
    private fun encode(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xFF
            if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c.toChar() in "-._~") append(c.toChar())
            else append('%').append("%02X".format(c))
        }
    }

    /** The values as the script sees them (`kash.options`): a boolean as a boolean, the others as strings. */
    public fun forScript(manifest: PluginManifest, values: Map<String, String>): JsonObject = JsonObject(
        manifest.options.associate { option ->
            val value = values[option.id] ?: defaultOf(option)
            option.id to if (option is PluginOption.Flag) JsonPrimitive(value == "true") else JsonPrimitive(value)
        },
    )
}
