package app.kash

import androidx.compose.runtime.mutableStateListOf
import app.kash.core.NotificationPolicy
import app.kash.core.Service
import app.kash.core.ServiceId
import app.kash.macos.MacosNotifications
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

/** How long a notification of the fallback window stays. */
private const val TOAST_DURATION_MS = 6_000L

/** A notification shown in the fallback window ([ToastWindow]), when macOS's Notification Center is unavailable. */
data class Toast(val key: String, val service: Service, val title: String, val body: String)

/**
 * Shows the notifications that the pages send (see `notification-shim.js`), when [NotificationPolicy] allows them: in macOS's Notification Center
 * when KaSH runs as KaSH.app, in [ToastWindow] otherwise (`./gradlew :app:run`). Clicking one shows its service and gives the click to the page.
 */
class Notifier(private val model: KashModel) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Whether the Notification Center is used: macOS calls the click callback on a thread of its own. */
    private val native: Boolean = MacosNotifications.start { key -> SwingUtilities.invokeLater { clicked(key) } }

    /** The notifications of the fallback window, newest last. */
    val toasts = mutableStateListOf<Toast>()

    init {
        logger.info("Notifications are shown {}", if (native) "by the Notification Center" else "in KaSH's own window (not an application bundle)")
        model.onPageNotification = ::received
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
        val sound = decision.sound && !silent
        // The key says which service and which notification of its page: what the click is given back.
        val key = "${service.id.value}|$id"
        if (native) {
            MacosNotifications.post(key, title.ifBlank { service.title }, service.title, body, sound)
        } else {
            toasts += Toast(key, service, title.ifBlank { service.title }, body)
            if (sound) Toolkit.getDefaultToolkit().beep()
            scope.launch {
                delay(TOAST_DURATION_MS)
                toasts.removeAll { it.key == key }
            }
        }
    }

    /** The notification [key] was clicked: its service is shown, and its page given the click (a messaging service opens the conversation). */
    fun clicked(key: String) {
        toasts.removeAll { it.key == key }
        val serviceId = key.substringBefore('|')
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
