package app.kash

import app.kash.core.PluginManifest
import app.kash.ui.stringsOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import app.kash.core.NotificationDuration
import app.kash.core.NotificationGroups
import app.kash.core.NotificationPolicy
import app.kash.core.Priorities
import app.kash.core.NotificationStyle
import app.kash.core.Service
import app.kash.core.ServiceId
import app.kash.core.ServiceUnread
import app.kash.core.Settings
import app.kash.macos.MacosNotifications
import app.kash.macos.MarkReadButton
import app.kash.macos.inAppBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.awt.Toolkit
import javax.swing.SwingUtilities

private val logger = LoggerFactory.getLogger(Notifier::class.java)

/** How long a notification of KaSH's own window stays, when it is temporary ([Settings.notificationDuration]). */
private const val TOAST_DURATION_MS = 6_000L

/** The key of a test notification ([Notifier.test]): no service's. */
private const val TEST_KEY = "test"

/** The key of the notification of a plugin's new version ([Notifier.pluginUpdate]), followed by the plugin's id. */
private const val PLUGIN_KEY = "plugin"

/**
 * A notification shown in KaSH's own window ([ToastWindow]): of a service, or a test (no service). A service's is a contact's, its last message,
 * and the notifications of it that came since it was shown ([received]; see [NotificationGroups]).
 */
data class Toast(val key: String, val service: Service?, val title: String, val body: String, val received: Int = 0)

// A contact's notifications (see NotificationGroups): the page's id of the last one, which a click is given, and how many came since it was shown.
private class Group(var latest: String, var received: Int)

/**
 * Shows the notifications that the pages send (see `notification-shim.js`), when [NotificationPolicy] allows them, as the settings say
 * ([Settings.notificationStyle]): in KaSH's own window ([ToastWindow]), or in macOS's Notification Center, which only KaSH.app has (KaSH's own
 * window otherwise, `./gradlew :app:run`). Clicking one shows its service and gives the click to the page.
 */
class Notifier(private val model: KashModel) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Whether KaSH is the Notification Center's delegate, which gets the clicks (on a thread of macOS) and makes macOS ask the user for the
    // authorization the first time. At launch when the settings use it, so that the clicks on the notifications of the last launch still come;
    // the first time a notification goes to it otherwise.
    private var systemStarted = false

    /** The notifications of KaSH's own window, newest last. */
    val toasts = mutableStateListOf<Toast>()

    // The notifications of each contact, by NotificationGroups.key, until they are clicked, closed, gone, or read.
    private val groups = HashMap<String, Group>()

    // The titles of the groups, which the Notification Center's notifications do not keep.
    private val titles = HashMap<String, String>()

    // The groups whose conversation the plugin listed as unread: once it is not any more (read, here or elsewhere), the group is over.
    private val seenUnread = HashSet<String>()

    init {
        val system = usesSystem(model.settings)
        logger.info("Notifications are shown {}", if (system) "by the Notification Center" else "in KaSH's own window")
        model.onPageNotification = ::received
        model.onPluginUpdate = ::pluginUpdate
        // A contact's notification goes away once its conversation is read.
        scope.launch { snapshotFlow { model.unread }.collect(::unreadChanged) }
    }

    /**
     * The count that a notification shows: the notifications of its contact, or the unread messages of its conversation when the plugin counts
     * more; 0 for a test's or a plugin's.
     */
    fun countOf(toast: Toast): Int {
        val service = toast.service ?: return 0
        return NotificationGroups.count(toast.received, NotificationGroups.conversationOf(model.unread[service.id]?.conversations.orEmpty(), toast.title))
    }

    private fun unreadChanged(unread: Map<ServiceId, ServiceUnread>) {
        for (key in groups.keys.toList() + toasts.mapNotNull { toast -> toast.key.takeIf { toast.service != null } }) {
            val service = model.service(ServiceId(key.substringBefore('|'))) ?: continue
            val title = toasts.firstOrNull { it.key == key }?.title ?: titles[key] ?: continue
            val conversation = NotificationGroups.conversationOf(unread[service.id]?.conversations.orEmpty(), title)
            if (conversation != null) seenUnread += key else if (key in seenUnread) end(key)
        }
    }

    // The notifications of the group [key] are over: its notification goes, and the next one starts a new count.
    private fun end(key: String) {
        groups.remove(key)
        titles.remove(key)
        seenUnread.remove(key)
        toasts.removeAll { it.key == key }
    }

    /**
     * Says that a watched plugin has a new version ([manifest]), which Settings › Plugins installs: KaSH's own notification, as the settings show
     * one, unless they turn the notifications off or Do Not Disturb is on.
     */
    private fun pluginUpdate(manifest: PluginManifest) {
        val settings = model.settings
        if (!settings.notifications || model.doNotDisturb) return
        val strings = stringsOf(settings.language)
        val title = strings.pluginUpdateTitle(manifest.name.of(strings.code), manifest.version)
        show("$PLUGIN_KEY|${manifest.id}", null, title, "KaSH", strings.pluginUpdateBody, settings.sound, usesSystem(settings), settings.notificationDuration)
    }

    /** Whether a notification goes to the Notification Center with [settings]: they say so, and KaSH is an application bundle (KaSH.app). */
    private fun usesSystem(settings: Settings): Boolean {
        if (settings.notificationStyle != NotificationStyle.SYSTEM || !inAppBundle) return false
        if (!systemStarted) systemStarted = MacosNotifications.start { key -> SwingUtilities.invokeLater { clicked(key) } }
        return systemStarted
    }

    /**
     * Shows a notification that says it is one, as [settings] would show a service's (their style and their sound, which the user may not have
     * applied yet), whether or not they let the services notify: to see that notifications work, and what they look like.
     */
    fun test(settings: Settings, title: String, body: String) {
        logger.info("Test notification, shown {}", if (usesSystem(settings)) "by the Notification Center" else "in KaSH's own window")
        // A key of its own each time: the Notification Center replaces a notification of the same identifier.
        show("$TEST_KEY|${System.currentTimeMillis()}", null, title, "KaSH", body, settings.sound, usesSystem(settings), settings.notificationDuration)
    }

    private fun received(service: Service, json: JsonObject) {
        val id = json["id"]?.jsonPrimitive?.content ?: return
        val title = json["title"]?.jsonPrimitive?.content.orEmpty()
        val body = json["body"]?.jsonPrimitive?.content.orEmpty()
        val silent = json["silent"]?.jsonPrimitive?.booleanOrNull ?: false
        notify(service, id, title, body, silent)
    }

    private fun notify(service: Service, id: String, title: String, body: String, silent: Boolean) {
        // A priority contact's goes through Do Not Disturb and the service's mute (the user's choice).
        val conversation = NotificationGroups.conversationOf(model.unread[service.id]?.conversations.orEmpty(), title.ifBlank { service.title })
        val priority = Priorities.isNotification(model.priorities, service.id, title.ifBlank { service.title }, conversation?.id)
        val decision = NotificationPolicy.decide(model.settings, service, priority, java.time.LocalDateTime.now())
        // Never what the notification says: it is the user's messages.
        logger.debug("Notification of {}: {}", service.title, decision)
        if (!decision.show) return
        // One notification per contact, which the next one of the same contact replaces: its last message, and how many came.
        val shownTitle = title.ifBlank { service.title }
        val key = NotificationGroups.key(service.id, shownTitle)
        val group = groups.getOrPut(key) { Group(id, 0) }
        group.latest = id
        group.received++
        titles[key] = shownTitle
        val system = usesSystem(model.settings)
        val count = NotificationGroups.count(
            group.received, NotificationGroups.conversationOf(model.unread[service.id]?.conversations.orEmpty(), shownTitle),
        )
        // The Notification Center's has no pill: its count is in its subtitle.
        val subtitle = if (system && count > 1) "${service.title} · ${stringsOf(model.settings.language).unread(count)}" else service.title
        // Its button "Mark as read", when the plugin can: the conversation is found when it is clicked, the plugin may not list it yet.
        val markable = if (system) model.markReadOf(service.id) else null
        if (markable != null) titleMarkRead()
        show(key, service, shownTitle, subtitle, body, decision.sound && !silent, system, model.settings.notificationDuration, group.received, markable)
    }

    // The language that the Notification Center's button "Mark as read" was last titled in.
    private var markReadLanguage: String? = null

    // Titles the Notification Center's button "Mark as read" in KaSH's language, when it changed.
    private fun titleMarkRead() {
        val strings = stringsOf(model.settings.language)
        if (markReadLanguage == strings.code) return
        MacosNotifications.setMarkReadTitles(strings.markRead, strings.markReadByOpening)
        markReadLanguage = strings.code
    }

    // The unread conversation of the notification [key] (a contact's), as its plugin lists it now.
    private fun conversationOf(key: String): Pair<Service, app.kash.core.Conversation>? {
        val service = model.service(ServiceId(key.substringBefore('|'))) ?: return null
        val title = toasts.firstOrNull { it.key == key }?.title ?: titles[key] ?: return null
        val conversation = NotificationGroups.conversationOf(model.unread[service.id]?.conversations.orEmpty(), title) ?: return null
        return service to conversation
    }

    /** How the conversation of [toast] is marked as read: its plugin can, and lists it as unread; null when it cannot. */
    fun markReadOf(toast: Toast): MarkRead? = toast.service?.let(Service::id)?.let(model::markReadOf)?.takeIf { conversationOf(toast.key) != null }

    /** Marks the conversation of the notification [key] as read, without showing its service, and the notification goes. */
    fun markRead(key: String) {
        val (service, conversation) = conversationOf(key) ?: return logger.debug("Mark as read: no unread conversation of the notification")
        model.markRead(service.id, conversation.id)
        end(key)
    }

    private fun show(
        key: String, service: Service?, title: String, subtitle: String, body: String, sound: Boolean, system: Boolean, duration: NotificationDuration,
        received: Int = 0, markable: MarkRead? = null,
    ) {
        if (system) {
            // How long it stays is macOS's to decide (KaSH's settings in its Notification settings: banners or alerts). The same identifier
            // replaces the contact's notification before.
            val button = when (markable) {
                null -> MarkReadButton.NONE
                MarkRead.DIRECT -> MarkReadButton.DIRECT
                MarkRead.BY_OPENING -> MarkReadButton.BY_OPENING
            }
            MacosNotifications.post(key, title, subtitle, body, sound, button)
        } else {
            // A new notification of the same contact replaces the one before, as the Notification Center does.
            val toast = Toast(key, service, title, body, received)
            toasts.removeAll { it.key == key }
            toasts += toast
            if (sound) Toolkit.getDefaultToolkit().beep()
            // A permanent one stays until it is clicked or closed; the window shows the last four, the others once those are gone.
            if (duration == NotificationDuration.TEMPORARY) {
                scope.launch {
                    delay(TOAST_DURATION_MS)
                    // Gone: the next notification of the contact counts from its unread messages again.
                    if (toasts.removeAll { it === toast }) groups.remove(key)
                }
            }
        }
    }

    /** The notification [key] was clicked: its service is shown, and its page given the click (a messaging service opens the conversation). */
    fun clicked(key: String) {
        // The button "Mark as read" of the Notification Center's.
        if (key.startsWith(MacosNotifications.MARK_READ_PREFIX)) return markRead(key.removePrefix(MacosNotifications.MARK_READ_PREFIX))
        toasts.removeAll { it.key == key }
        // A contact's: its page's last notification is the one clicked.
        val latest = groups[key]?.latest
        if (latest != null) end(key)
        val serviceId = key.substringBefore('|')
        // A test notification is no service's: KaSH comes to the front, as a click on a notification brings it.
        if (serviceId == TEST_KEY) return model.activateWindow()
        // A plugin's new version: Settings › Plugins, which installs it.
        if (serviceId == PLUGIN_KEY) return model.openSettings(SettingsPage.PLUGINS)
        val notificationId = latest ?: key.substringAfter('|')
        val service = model.service(ServiceId(serviceId)) ?: return
        model.show(service)
        val quoted = Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(notificationId))
        model.loaded[service.id]?.tab?.execute("window.__kashNotificationClick && window.__kashNotificationClick($quoted)")
    }

    fun dismiss(key: String) {
        groups.remove(key)
        toasts.removeAll { it.key == key }
    }
}
