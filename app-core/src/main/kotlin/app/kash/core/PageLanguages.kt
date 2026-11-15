package app.kash.core

/**
 * The languages that the services' pages are given: KaSH's own (`fr`, `en`), which a site that follows the browser's language (Google Messages)
 * shows itself in, then English, which most sites have. A site that follows its account's language (Google Chat, Slack) keeps it.
 */
public object PageLanguages {
    // The region of each language of KaSH, as Chrome gives it.
    private val REGIONS = mapOf("fr" to "FR", "en" to "US")

    /** What Chromium says the user reads, in this order: its `Accept-Language` header and `navigator.languages` (`fr-FR,fr,en-US,en`). */
    public fun acceptLanguages(code: String): String {
        val own = listOfNotNull(REGIONS[code]?.let { "$code-$it" }, code)
        val english = if (code == ENGLISH) emptyList() else listOf("en-US", ENGLISH)
        return (own + english).joinToString(",")
    }

    /**
     * The locale of Chromium itself (its menus, its error pages, `Intl`'s default in the pages), named as its resources are (`fr`, `en-US`). It is
     * given when Chromium starts, and changes only at the next launch.
     */
    public fun locale(code: String): String = if (code == ENGLISH) "en-US" else code

    private const val ENGLISH = "en"
}
