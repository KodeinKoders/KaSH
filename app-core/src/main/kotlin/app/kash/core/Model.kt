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

/** The icon of a service's tab. */
@Serializable
public sealed interface ServiceIcon {
    /** The icon of the service's plugin (its manifest's `icon`); its colored square when the plugin has none. */
    @Serializable
    @SerialName("plugin")
    public data object Plugin : ServiceIcon

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
 * A messaging service, shown in a tab of the rail, in a Chromium profile of its own: a plugin's (see [PluginManifest]), or a web application that the
 * user added by its address.
 *
 * @property plugin The id of its plugin, null for a web application that the user added.
 * @property options The values of its plugin's options, normalized ([PluginOptions]): a boolean is `true` or `false`.
 * @property name What it is called: the user's for a web application; for a plugin's service, its plugin's name in the language of the interface,
 *   derived from the plugin ([PluginRegistry.refresh]).
 * @property url Its address: the user's for a web application; for a plugin's service, its plugin's URL with the values of the options, derived
 *   from the plugin.
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
    val plugin: String? = null,
    val options: Map<String, String> = emptyMap(),
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

/**
 * Where the notifications are shown: in KaSH's own window (at the top right of the screen), or in macOS's Notification Center, which only an
 * application bundle has (KaSH.app: KaSH's own window otherwise).
 */
@Serializable
public enum class NotificationStyle { KASH, SYSTEM }

/**
 * How long KaSH's own notifications stay: a few seconds, or until they are clicked or closed. The Notification Center's are macOS's to decide (its
 * settings of KaSH: banners or alerts).
 */
@Serializable
public enum class NotificationDuration { TEMPORARY, PERMANENT }

/** How the Unread tab lists the conversations. */
@Serializable
public enum class InboxSort { BY_DATE, BY_COUNT, BY_SERVICE }

/**
 * The settings of the application.
 *
 * @property launchAtLogin Off by default: registering a login item is the user's decision.
 * @property directOnly Only direct messages and mentions in the Unread tab: kept, but without effect until the services are instrumented.
 * @property showApproximateTimes Whether the Unread tab shows the times of the conversations that came while KaSH was not watching their services
 * ([WhileClosed], with a clock). Without, they have no time (last when sorted by date); KaSH still keeps them ([UnreadTimes]), for when it is on
 * again.
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
    val notificationStyle: NotificationStyle = NotificationStyle.SYSTEM,
    val notificationDuration: NotificationDuration = NotificationDuration.TEMPORARY,
    val dockBadge: DockBadge = DockBadge.TOTAL,
    // The sort of the Unread tab, as the user last chose it.
    val inboxSort: InboxSort = InboxSort.BY_DATE,
    val openInboxOnLaunch: Boolean = true,
    val skipMuted: Boolean = true,
    val directOnly: Boolean = false,
    val showApproximateTimes: Boolean = true,
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
 * @property priorities The contacts that the user marked as priority ([Priorities]).
 */
@Serializable
public data class KashState(
    val services: List<Service> = emptyList(),
    val selection: Selection = Selection.Inbox,
    val settings: Settings = Settings(),
    val deletedProfiles: List<ProfileId> = emptyList(),
    val priorities: List<PriorityContact> = emptyList(),
)
