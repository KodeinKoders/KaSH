package app.kash.macos

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.ValueLayout

/** Whether KaSH runs as an application bundle (KaSH.app): the Notification Center and the login items only know those. */
public val inAppBundle: Boolean by lazy {
    val function = MacosLibrary.function("kash_in_app_bundle", FunctionDescriptor.of(ValueLayout.JAVA_INT)) ?: return@lazy false
    (function.invoke() as Int) == 1
}

/** Whether the user lets KaSH show notifications in the Notification Center (System Settings › Notifications). */
public enum class NotificationAuthorization {
    /** macOS has not asked the user yet: it does when KaSH starts using the Notification Center. */
    NOT_DETERMINED,
    DENIED,
    AUTHORIZED,

    /** Not an application bundle, or macOS did not answer. */
    UNAVAILABLE,
}

/** The notifications of macOS's Notification Center. */
public object MacosNotifications {
    private val authorization = MacosLibrary.function("kash_notifications_authorization", FunctionDescriptor.of(ValueLayout.JAVA_INT))
    private val start = MacosLibrary.function("kash_notifications_start", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
    private val post = MacosLibrary.function(
        "kash_notifications_post",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
    )

    @Volatile
    private var started = false

    /**
     * Starts receiving the clicks on KaSH's notifications, which [onClick] is given the identifier of, on a thread of macOS. macOS asks the user for
     * the authorization the first time. False outside an application bundle: the notifications are then unavailable.
     */
    public fun start(onClick: (String) -> Unit): Boolean {
        val function = start ?: return false
        started = (function.invoke(MacosLibrary.stringCallback(onClick)) as Int) == 1
        return started
    }

    /** Whether the user lets KaSH show notifications. Waits for macOS's answer (2 s at most): not on Swing's thread. */
    public fun authorization(): NotificationAuthorization = when (authorization?.invoke() as Int?) {
        0 -> NotificationAuthorization.NOT_DETERMINED
        1 -> NotificationAuthorization.DENIED
        2 -> NotificationAuthorization.AUTHORIZED
        else -> NotificationAuthorization.UNAVAILABLE
    }

    /** Shows a notification, once [start] succeeded. [identifier] is what [start]'s callback is given when it is clicked. */
    public fun post(identifier: String, title: String, subtitle: String, body: String, sound: Boolean) {
        val function = post ?: return
        if (!started) return
        Arena.ofConfined().use { arena ->
            function.invoke(
                MacosLibrary.cString(arena, identifier),
                MacosLibrary.cString(arena, title),
                MacosLibrary.cString(arena, subtitle),
                MacosLibrary.cString(arena, body),
                if (sound) 1 else 0,
            )
        }
    }
}

/** The status of KaSH's login item. */
public enum class LoginItemStatus {
    NOT_REGISTERED,
    ENABLED,

    /** Registered, but the user has to allow it in the System Settings (General › Login Items). */
    REQUIRES_APPROVAL,
    NOT_FOUND,

    /** Not an application bundle: there is no login item to register. */
    UNAVAILABLE,
}

/** KaSH as a login item of macOS (SMAppService.mainAppService), which macOS opens when the user logs in. */
public object MacosLoginItem {
    private val status = MacosLibrary.function("kash_login_item_status", FunctionDescriptor.of(ValueLayout.JAVA_INT))
    private val set = MacosLibrary.function("kash_login_item_set", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))

    public fun status(): LoginItemStatus = when (status?.invoke() as Int?) {
        0 -> LoginItemStatus.NOT_REGISTERED
        1 -> LoginItemStatus.ENABLED
        2 -> LoginItemStatus.REQUIRES_APPROVAL
        3 -> LoginItemStatus.NOT_FOUND
        else -> LoginItemStatus.UNAVAILABLE
    }

    /** Registers or unregisters KaSH as a login item. False when it did not work (logged by macOS) or there is no bundle. */
    public fun set(enabled: Boolean): Boolean = (set?.invoke(if (enabled) 1 else 0) as Int?) == 1
}
