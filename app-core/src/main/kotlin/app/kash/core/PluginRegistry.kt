package app.kash.core

import java.text.BreakIterator

/** A plugin: its manifest, the source of its script, and its icon (the image file that its manifest names; null when it names none). */
public class Plugin(public val manifest: PluginManifest, public val script: String, public val icon: ByteArray? = null) {
    public val id: String get() = manifest.id
}

/**
 * The plugins that KaSH has, in the order of the catalog of the Add service window. Two plugins of the same id are one: the first one.
 */
public class PluginRegistry(plugins: List<Plugin>) {
    public val all: List<Plugin> = plugins.distinctBy { it.id }

    private val byId = all.associateBy { it.id }

    /** The plugin [id], or null when KaSH has none of that id (or [id] is null: a web application). */
    public operator fun get(id: String?): Plugin? = id?.let(byId::get)

    /**
     * [service] with what is derived from its plugin: the value of every option (the defaults of those that it has none of), its name in
     * [language], and its URL. A web application, or a service of a plugin that KaSH does not have, is as it is.
     */
    public fun refresh(service: Service, language: String): Service {
        val manifest = get(service.plugin)?.manifest ?: return service
        val values = PluginOptions.values(manifest, service.options)
        return service.copy(
            options = values,
            name = manifest.name.of(language),
            url = PluginOptions.resolveUrl(manifest, values) ?: service.url,
        )
    }

    /** The colored square of [service]: its plugin's, or a web application's (its initials on the default color). */
    public fun square(service: Service): PluginSquare = get(service.plugin)?.manifest?.square ?: WebApplications.square(service.name)
}

/** The web applications that the user adds by their address: they are no plugin, they have no option and no script. */
public object WebApplications {
    /** The square of a web application: the design's default blue. */
    public const val COLOR: String = "#3058CF"

    /** The square of a web application named [name]: its initials ([monoOf]) on [COLOR]. */
    public fun square(name: String): PluginSquare = PluginSquare(monoOf(name), COLOR)

    /**
     * Two letters for [name]: the initials of its first two words, or its first two letters when it is one word. Letters as they are seen
     * (graphemes): an emoji is two UTF-16 units, or more, and half of one was drawn as a replacement glyph.
     */
    public fun monoOf(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> graphemes(words[0]).take(2).let { letters -> letters.first().uppercase() + letters.drop(1).joinToString("") }
            else -> graphemes(words[0]).first().uppercase() + graphemes(words[1]).first().uppercase()
        }
    }

    // The letters of [word] as they are seen: a letter and its accents, an emoji and its modifiers.
    private fun graphemes(word: String): List<String> {
        val breaks = BreakIterator.getCharacterInstance()
        breaks.setText(word)
        val letters = ArrayList<String>()
        var start = breaks.first()
        var end = breaks.next()
        while (end != BreakIterator.DONE) {
            letters += word.substring(start, end)
            start = end
            end = breaks.next()
        }
        return letters
    }
}
