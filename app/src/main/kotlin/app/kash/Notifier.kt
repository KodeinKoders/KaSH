package app.kash

import app.kash.core.PluginManifest
import app.kash.ui.stringsOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import app.kash.core.NotificationDuration
import app.kash.core.NotificationGroups
import app.kash.core.NotificationPolicy
import app.kash.core.NotificationReads
import app.kash.core.UnreadTimes
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
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
// A contact's notifications: the page's id of its last one, whether that one came from a service worker's showNotification (whose click no page
// listener takes: KaSH opens its conversation itself), and how many came.
private class Group(var latest: String, var received: Int, var fromWorker: Boolean = false)

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

    // The last click given to a page (its service, the page's id of the notification, and its conversation as the plugin listed it then): when the
    // page says that it does not have that notification any more (it was loaded again since), KaSH opens the conversation itself.
    private var lastClick: Triple<ServiceId, String, String?>? = null

    // When a group is over because what it says was read (in KaSH, on the phone…): its notification goes, KaSH's own and the Notification
    // Center's. What it knew of the groups of the last launch, whose notifications the Notification Center may still show, is in its file.
    private val reads = NotificationReads(model.notificationReadsFile.load())

    // The groups whose conversation was listed, as last written to the file.
    private var savedReads = emptySet<String>()

    init {
        val system = usesSystem(model.settings)
        logger.info("Notifications are shown {}", if (system) "by the Notification Center" else "in KaSH's own window")
        model.onPageNotification = ::received
        model.onPluginUpdate = ::pluginUpdate
        // A contact's notification goes away once its conversation is read: by what the services whose page said it have unread, not those
        // whose page is closed or still loading, which would seem to have nothing.
        scope.launch {
            snapshotFlow { model.reporting.let { reporting -> model.unread.filterKeys { it in reporting } } to model.services.mapTo(HashSet()) { it.id } }
                .collect { (unread, services) -> unreadChanged(unread, services) }
        }
        // Those of the last launch that the Notification Center still shows, once the services had the time to say what they have unread.
        if (system) scope.launch { delay(UnreadTimes.SETTLE_MS); restoreDelivered() }
    }

    // The notifications of the last launches that the Notification Center still shows: those whose conversation was read meanwhile go, the others
    // are followed as this launch's.
    private suspend fun restoreDelivered() {
        val delivered = withContext(Dispatchers.IO) { MacosNotifications.delivered() } ?: return logger.debug("The Notification Center did not say what it shows")
        val restored = delivered.filter { key -> key !in groups && !key.startsWith("$TEST_KEY|") && !key.startsWith("$PLUGIN_KEY|") }
        // What a service has unread only once it said it since the launch: one that has not (still loading, disabled, its plugin failing) is
        // not taken for one that has nothing, and its notifications wait for it.
        val reporting = model.reporting
        val unread = model.unread
        val over = restored.filter { key ->
            val service = NotificationReads.serviceOf(key)
            reads.restored(key, unread[service]?.takeIf { service in reporting }, serviceThere = model.service(service) != null)
        }
        over.forEach(MacosNotifications::remove)
        logger.debug("Notifications of the last launch: {} shown, {} read meanwhile", restored.size, over.size)
        saveReads()
    }

    // Writes the groups whose conversation was listed, when they changed, off Swing's thread.
    private fun saveReads() {
        val seen = reads.seen
        if (seen == savedReads) return
        savedReads = seen
        scope.launch(Dispatchers.IO) {
            try {
                model.notificationReadsFile.save(seen)
            } catch (e: Exception) {
                logger.error("Cannot save the notifications whose conversation was listed", e)
            }
        }
    }

    /**
     * The count that a notification shows: the notifications of its contact, or the unread messages of its conversation when the plugin counts
     * more; 0 for a test's or a plugin's.
     */
    fun countOf(toast: Toast): Int {
        val service = toast.service ?: return 0
        return NotificationGroups.count(toast.received, NotificationGroups.conversationOf(model.unread[service.id]?.conversations.orEmpty(), toast.title))
    }

    private fun unreadChanged(unread: Map<ServiceId, ServiceUnread>, services: Set<ServiceId>) {
        val over = reads.update(unread, services)
        // Never which ones: their titles are the user's contacts.
        if (over.isNotEmpty()) logger.debug("Notifications read: {}", over.size)
        over.forEach(::end)
        saveReads()
    }

    // The notifications of the group [key] are over: its notification goes (KaSH's own, and the Notification Center's), and the next one starts
    // a new count.
    private fun end(key: String) {
        groups.remove(key)
        titles.remove(key)
        reads.forget(key)
        toasts.removeAll { it.key == key }
        // Also when the notifications are KaSH's own now: the Notification Center may show one from before.
        if (systemStarted) MacosNotifications.remove(key)
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
        val id = json["id"]?.primitive()?.contentOrNull ?: return
        when (json["type"]?.primitive()?.contentOrNull) {
            "notification-close" -> return closed(service, id, json["after"]?.primitive()?.longOrNull)
            "notification-click-missed" -> return clickMissed(service, id)
        }
        val title = json["title"]?.primitive()?.contentOrNull.orEmpty()
        val body = json["body"]?.primitive()?.contentOrNull.orEmpty()
        val silent = json["silent"]?.primitive()?.booleanOrNull ?: false
        val fromWorker = json["fromWorker"]?.primitive()?.booleanOrNull ?: false
        notify(service, id, title, body, silent, fromWorker)
    }

    // The page of [service] does not have the notification [id] that was clicked (it was loaded again since): the conversation of its group, when
    // the plugin lists it, is opened by the plugin (the service is shown already).
    private fun clickMissed(service: Service, id: String) {
        val (clickedService, clickedId, conversation) = lastClick ?: return
        if (clickedService != service.id || clickedId != id) return
        lastClick = null
        logger.debug("A notification of {} was clicked after its page was loaded again: {}", service.title, if (conversation == null) "its service is shown" else "its conversation is opened")
        if (conversation != null) model.openConversation(service, conversation)
    }

    /**
     * The page of [service] closed its notification [id], [after] milliseconds after it showed it: Chrome would take it away. KaSH does for a web
     * application, whose page is all it knows of what was read, when it is the last one of its group; a plugin's service says what was read
     * itself (some pages close their notifications after a while, read or not).
     */
    private fun closed(service: Service, id: String, after: Long?) {
        // The ids are the page's own: another service's page may have the same.
        val key = groups.entries.firstOrNull { (key, group) -> group.latest == id && NotificationReads.serviceOf(key) == service.id }?.key
        // How the pages close their notifications, to see which ones say what was read: never which notification.
        logger.debug("Notification of {} closed by its page after {} ms: {}", service.title, after, if (key == null) "not the last of its group" else "the last of its group")
        if (key != null && service.plugin == null) end(key)
    }

    private fun notify(service: Service, id: String, title: String, body: String, silent: Boolean, fromWorker: Boolean = false) {
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
        group.fromWorker = fromWorker
        group.received++
        titles[key] = shownTitle
        reads.shown(key, model.unread[service.id])
        saveReads()
        // Whether the services' notifications are titled as their conversations, to see which ones are followed by their conversation and which
        // by their service's total: never the title.
        logger.debug("Notification of {}: {}", service.title, if (NotificationGroups.listedOf(model.unread[service.id]?.conversations.orEmpty(), shownTitle) != null) "its conversation is listed" else "no conversation listed of its title")
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

    // The unread conversation of the notification [key] (a contact's), as its plugin lists it now. Its title is the one that this launch showed,
    // or, for a notification of an earlier launch, the key's (in lower case: the conversations are matched without case).
    private fun conversationOf(key: String): Pair<Service, app.kash.core.Conversation>? {
        val service = model.service(ServiceId(key.substringBefore('|'))) ?: return null
        val title = toasts.firstOrNull { it.key == key }?.title ?: titles[key] ?: NotificationReads.titleOf(key)
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
                    if (toasts.removeAll { it === toast }) {
                        groups.remove(key)
                        titles.remove(key)
                        reads.forget(key)
                    }
                }
            }
        }
    }

    /** The notification [key] was clicked: its service is shown, and its page given the click (a messaging service opens the conversation). */
    fun clicked(key: String) {
        // The button "Mark as read" of the Notification Center's.
        if (key.startsWith(MacosNotifications.MARK_READ_PREFIX)) return markRead(key.removePrefix(MacosNotifications.MARK_READ_PREFIX))
        // Its conversation, as the plugin lists it, before what is known of the notification goes: the one that KaSH opens when the page does not
        // have the notification any more.
        val conversation = conversationOf(key)?.second?.id
        toasts.removeAll { it.key == key }
        reads.forget(key)
        // A contact's: its page's last notification is the one clicked.
        val latest = groups[key]?.latest
        val fromWorker = groups[key]?.fromWorker == true
        if (latest != null) end(key)
        val serviceId = key.substringBefore('|')
        // A test notification is no service's: KaSH comes to the front, as a click on a notification brings it.
        if (serviceId == TEST_KEY) return model.activateWindow()
        // A plugin's new version: Settings › Plugins, which installs it.
        if (serviceId == PLUGIN_KEY) return model.openSettings(SettingsPage.PLUGINS)
        val service = model.service(ServiceId(serviceId)) ?: return
        // One of an earlier launch: its page has no id for it, the plugin opens its conversation when it lists it. One of a service worker
        // (showNotification): no listener of the page takes its click, which its service worker's notificationclick would have.
        if (latest == null || fromWorker) return model.openConversation(service, conversation)
        model.show(service)
        val notificationId = latest
        lastClick = Triple(service.id, notificationId, conversation)
        val quoted = Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(notificationId))
        model.loaded[service.id]?.tab?.execute("window.__kashNotificationClick && window.__kashNotificationClick($quoted)")
    }

    fun dismiss(key: String) {
        groups.remove(key)
        // A contact's name: not kept longer than its notification.
        titles.remove(key)
        reads.forget(key)
        toasts.removeAll { it.key == key }
    }
}
