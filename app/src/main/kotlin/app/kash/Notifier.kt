package app.kash

import app.kash.core.PluginManifest
import app.kash.ui.stringsOf
import androidx.compose.runtime.mutableStateListOf
import app.kash.core.NotificationDuration
import app.kash.core.NotificationPolicy
import app.kash.core.NotificationStyle
import app.kash.core.Service
import app.kash.core.ServiceId
import app.kash.core.Settings
import app.kash.macos.MacosNotifications
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

/** A notification shown in KaSH's own window ([ToastWindow]): of a service, or a test (no service). */
data class Toast(val key: String, val service: Service?, val title: String, val body: String)

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

    init {
        val system = usesSystem(model.settings)
        logger.info("Notifications are shown {}", if (system) "by the Notification Center" else "in KaSH's own window")
        model.onPageNotification = ::received
        model.onPluginUpdate = ::pluginUpdate
    }

    /**
     * Says that a watched plugin has a new version ([manifest]), which Settings › Plugins installs: KaSH's own notification, as the settings show
     * one, unless they turn the notifications off or Do Not Disturb is on.
     */
    private fun pluginUpdate(manifest: PluginManifest) {
        val settings = model.settings
        if (!settings.notifications || settings.doNotDisturb) return
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
        val decision = NotificationPolicy.decide(model.settings, service)
        // Never what the notification says: it is the user's messages.
        logger.debug("Notification of {}: {}", service.title, decision)
        if (!decision.show) return
        // The key says which service and which notification of its page: what the click is given back.
        show(
            "${service.id.value}|$id", service, title.ifBlank { service.title }, service.title, body, decision.sound && !silent, usesSystem(model.settings),
            model.settings.notificationDuration,
        )
    }

    private fun show(
        key: String, service: Service?, title: String, subtitle: String, body: String, sound: Boolean, system: Boolean, duration: NotificationDuration,
    ) {
        if (system) {
            // How long it stays is macOS's to decide (KaSH's settings in its Notification settings: banners or alerts).
            MacosNotifications.post(key, title, subtitle, body, sound)
        } else {
            // A new notification of the same conversation replaces the one before, as the Notification Center does.
            val toast = Toast(key, service, title, body)
            toasts.removeAll { it.key == key }
            toasts += toast
            if (sound) Toolkit.getDefaultToolkit().beep()
            // A permanent one stays until it is clicked or closed; the window shows the last four, the others once those are gone.
            if (duration == NotificationDuration.TEMPORARY) {
                scope.launch {
                    delay(TOAST_DURATION_MS)
                    toasts.removeAll { it === toast }
                }
            }
        }
    }

    /** The notification [key] was clicked: its service is shown, and its page given the click (a messaging service opens the conversation). */
    fun clicked(key: String) {
        toasts.removeAll { it.key == key }
        val serviceId = key.substringBefore('|')
        // A test notification is no service's: KaSH comes to the front, as a click on a notification brings it.
        if (serviceId == TEST_KEY) return model.activateWindow()
        // A plugin's new version: Settings › Plugins, which installs it.
        if (serviceId == PLUGIN_KEY) return model.openSettings(SettingsPage.PLUGINS)
        val notificationId = key.substringAfter('|')
        val service = model.service(ServiceId(serviceId)) ?: return
        model.show(service)
        val quoted = Json.encodeToString(kotlinx.serialization.json.JsonPrimitive(notificationId))
        model.loaded[service.id]?.tab?.execute("window.__kashNotificationClick && window.__kashNotificationClick($quoted)")
    }

    fun dismiss(key: String) {
        toasts.removeAll { it.key == key }
    }
}
