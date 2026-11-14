package app.kash.core

/**
 * A service that KaSH knows: its address, and the colored square that stands for it while its site's icon is not there.
 *
 * @property mono The text of the square: two letters at most.
 * @property color The square's color, `#RRGGBB`.
 */
public data class ServicePreset(
    val kind: ServiceKind,
    val name: String,
    val url: String,
    val mono: String,
    val color: String,
)

/** The services that can be added: the known ones, then a custom application. */
public object ServiceCatalog {
    public val whatsApp: ServicePreset = ServicePreset(ServiceKind.WHATSAPP, "WhatsApp", "https://web.whatsapp.com/", "WA", "#1A7F4B")

    /** Google Chat alone, without Gmail: where `chat.google.com` sends to. */
    public val googleChat: ServicePreset = ServicePreset(ServiceKind.GOOGLE_CHAT, "Google Chat", "https://mail.google.com/chat/u/0/", "GC", "#188038")

    /** The known services, in the order of the catalog. More are added as KaSH learns to read their pages. */
    public val presets: List<ServicePreset> = listOf(whatsApp, googleChat)

    /** The square of a custom application: the design's default blue. */
    public const val CUSTOM_COLOR: String = "#3058CF"

    public fun preset(kind: ServiceKind): ServicePreset? = presets.firstOrNull { it.kind == kind }

    /** The text of a service's square: its preset's, or the first letters of a custom application's name (`Mon application` → `MA`). */
    public fun mono(service: Service): String = preset(service.kind)?.mono ?: monoOf(service.name)

    /** The color of a service's square. */
    public fun color(service: Service): String = preset(service.kind)?.color ?: CUSTOM_COLOR

    /** Two letters for [name]: the initials of its first two words, or its first two letters when it is one word. */
    public fun monoOf(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2).replaceFirstChar { it.uppercaseChar() }
            else -> "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
        }
    }
}
