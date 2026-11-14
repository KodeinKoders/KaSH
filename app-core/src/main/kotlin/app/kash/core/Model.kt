package app.kash.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * What names a browser profile, and its directory (see [KashDirectories.profile]).
 *
 * It starts with `env-` so that it can never be one of the directories that Chromium keeps next to the profiles in the same root (`Default`,
 * `Safe Browsing`, `GraphiteDawnCache`…).
 */
@JvmInline
@Serializable
public value class ProfileId(public val value: String) {
    init {
        require(PATTERN.matches(value)) { "Not a profile id: $value" }
    }

    override fun toString(): String = value

    public companion object {
        private val PATTERN = Regex("env-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        public fun random(): ProfileId = ProfileId("env-${UUID.randomUUID()}")
    }
}

/** What names a service: stable for its whole life, even when its profile is replaced (see [Service.profile]). */
@JvmInline
@Serializable
public value class ServiceId(public val value: String) {
    override fun toString(): String = value

    public companion object {
        public fun random(): ServiceId = ServiceId(UUID.randomUUID().toString())
    }
}

/** Which kind of service it is: a known one, whose page KaSH knows how to read, or any web application. */
@Serializable
public enum class ServiceKind {
    WHATSAPP,
    GOOGLE_CHAT,
    SLACK,
    CUSTOM,
}

/** The icon of a service's tab. */
@Serializable
public sealed interface ServiceIcon {
    /** The site's own icon, fetched from its address; the service's colored square while there is none. */
    @Serializable
    @SerialName("favicon")
    public data object Favicon : ServiceIcon

    /** An image that the user chose, copied in [KashDirectories.icons] under [file]. */
    @Serializable
    @SerialName("image")
    public data class Image(val file: String) : ServiceIcon

    /** A square of [color] (`#RRGGBB`) with [text] on it (3 characters at most). */
    @Serializable
    @SerialName("square")
    public data class Square(val color: String, val text: String) : ServiceIcon
}

/**
 * A messaging service, shown in a tab of the rail, in a Chromium profile of its own.
 *
 * @property name What it is called (the catalog's name, or the user's for a custom application).
 * @property label What is shown under its icon, to tell two accounts of the same service apart.
 * @property profile Its Chromium profile: replaced by a new one when its data is erased (the old one is then in [KashState.deletedProfiles]).
 * @property enabled A disabled service is not loaded, and is shown neither in the rail nor in the Unread tab.
 * @property showBadge Whether its tab shows how many messages are unread.
 * @property includeInInbox Whether its conversations are listed in the Unread tab.
 * @property desktopNotifications Whether its notifications are shown on the desktop.
 * @property muted The bell of its header: its notifications are silenced until it is rung again.
 */
@Serializable
public data class Service(
    val id: ServiceId,
    val kind: ServiceKind,
    val name: String,
    val url: String,
    val label: String = "",
    val profile: ProfileId,
    val enabled: Boolean = true,
    val showBadge: Boolean = true,
    val includeInInbox: Boolean = true,
    val desktopNotifications: Boolean = true,
    val muted: Boolean = false,
    val icon: ServiceIcon = ServiceIcon.Favicon,
) {
    /** The name with its label: `WhatsApp · Perso`. */
    val title: String get() = if (label.isBlank()) name else "$name · $label"
}

@Serializable
public enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Serializable
public enum class Language { SYSTEM, FRENCH, ENGLISH }

@Serializable
public enum class IconSize { SMALL, MEDIUM, LARGE }

@Serializable
public enum class RailSide { LEFT, RIGHT, TOP, BOTTOM }

/** What the icon of the Dock shows. */
@Serializable
public enum class DockBadge { TOTAL, DOT, NONE }

/** How the Unread tab lists the conversations. */
@Serializable
public enum class InboxSort { BY_DATE, BY_COUNT, BY_SERVICE }

/**
 * The settings of the application.
 *
 * @property launchAtLogin Off by default: registering a login item is the user's decision.
 * @property directOnly Only direct messages and mentions in the Unread tab: kept, but without effect until the services are instrumented.
 * @property doNotDisturb Kept across launches.
 */
@Serializable
public data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: Language = Language.SYSTEM,
    val launchAtLogin: Boolean = false,
    val iconSize: IconSize = IconSize.MEDIUM,
    val railSide: RailSide = RailSide.LEFT,
    val showLabels: Boolean = true,
    val showBadges: Boolean = true,
    val notifications: Boolean = true,
    val sound: Boolean = true,
    val dockBadge: DockBadge = DockBadge.TOTAL,
    // The sort of the Unread tab, as the user last chose it.
    val inboxSort: InboxSort = InboxSort.BY_DATE,
    val openInboxOnLaunch: Boolean = true,
    val skipMuted: Boolean = true,
    val directOnly: Boolean = false,
    val doNotDisturb: Boolean = false,
)

/** What is selected in the rail: the Unread tab, or a service. */
@Serializable
public sealed interface Selection {
    @Serializable
    @SerialName("inbox")
    public data object Inbox : Selection

    @Serializable
    @SerialName("service")
    public data class OfService(val id: ServiceId) : Selection
}

/**
 * What KaSH keeps between launches.
 *
 * @property services The services, in the order of their tabs.
 * @property deletedProfiles The profiles of the services that were deleted or erased, which are deleted at the next launch, before the browser engine
 *   starts: while it runs, Chromium may still hold or write files of a profile that is closed.
 */
@Serializable
public data class KashState(
    val services: List<Service> = emptyList(),
    val selection: Selection = Selection.Inbox,
    val settings: Settings = Settings(),
    val deletedProfiles: List<ProfileId> = emptyList(),
)
