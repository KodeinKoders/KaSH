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
    public val googleChat: ServicePreset = ServicePreset(ServiceKind.GOOGLE_CHAT, "Google Chat", "https://mail.google.com/chat/u/0/", "GC", "#0B7A75")

    /**
     * Slack's web application. A Slack service is one workspace, whose address the user gives ([slackUrl]); this address is the one of its
     * icon, before there is a workspace.
     */
    public val slack: ServicePreset = ServicePreset(ServiceKind.SLACK, "Slack", "https://app.slack.com/client", "Sl", "#5B2C6F")

    /** The known services, in the order of the catalog. More are added as KaSH learns to read their pages. */
    public val presets: List<ServicePreset> = listOf(whatsApp, googleChat, slack)

    /** The square of a custom application: the design's default blue. */
    public const val CUSTOM_COLOR: String = "#3058CF"

    public fun preset(kind: ServiceKind): ServicePreset? = presets.firstOrNull { it.kind == kind }

    // A workspace's name in its address: labels of letters, digits and hyphens, which neither start nor end with a hyphen, and dots between them
    // (`acme.enterprise` for an Enterprise Grid organization).
    private val SLACK_WORKSPACE = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*")

    /**
     * The workspace that the user typed in the Slack field (`mon-equipe`), or null when it is not one. Its address, pasted whole
     * (`https://mon-equipe.slack.com/`), is read as its workspace.
     */
    public fun slackWorkspace(input: String): String? {
        val workspace = input.trim().lowercase()
            .removePrefix("https://").removePrefix("http://")
            .substringBefore('/')
            .removeSuffix(".slack.com")
        return workspace.takeIf { SLACK_WORKSPACE.matches(it) }
    }

    /** The address of a Slack [workspace]: `https://mon-equipe.slack.com/`, which signs in to it, then opens it in Slack's web application. */
    public fun slackUrl(workspace: String): String = "https://$workspace.slack.com/"

    /** The workspace of a Slack service's [url], or null when it is not a workspace's address. */
    public fun slackWorkspaceOf(url: String): String? {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return null
        if (!host.endsWith(".slack.com")) return null
        return slackWorkspace(host).takeUnless { it == "app" || it == "www" }
    }

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
