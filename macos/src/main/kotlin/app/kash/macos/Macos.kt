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

/**
 * How macOS shows KaSH's notifications, as the user set it in System Settings › Notifications › KaSH (its alert style): KaSH cannot choose it.
 */
public enum class NotificationAlertStyle {
    /** Not shown on the screen (only in the Notification Center's list). */
    NONE,

    /** Banners, which go away after a few seconds. */
    BANNERS,

    /** Alerts, which stay until they are closed. */
    ALERTS,

    /** Not an application bundle, or macOS did not answer. */
    UNAVAILABLE,
}

/** The button "Mark as read" of a notification ([MacosNotifications.post]): none, its plugin's own, or by opening the conversation. */
public enum class MarkReadButton { NONE, DIRECT, BY_OPENING }

/** The notifications of macOS's Notification Center. */
public object MacosNotifications {
    private val authorization = MacosLibrary.function("kash_notifications_authorization", FunctionDescriptor.of(ValueLayout.JAVA_INT))
    private val alertStyle = MacosLibrary.function("kash_notifications_alert_style", FunctionDescriptor.of(ValueLayout.JAVA_INT))
    private val start = MacosLibrary.function("kash_notifications_start", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
    private val post = MacosLibrary.function(
        "kash_notifications_post",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
    )
    private val remove = MacosLibrary.function("kash_notifications_remove", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))
    private val delivered = MacosLibrary.function(
        "kash_notifications_delivered",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
    )
    private val markReadTitles = MacosLibrary.function(
        "kash_notifications_set_mark_read_titles",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
    )

    /** What [start]'s callback is given, before the identifier, when the button "Mark as read" of a notification was clicked. */
    public const val MARK_READ_PREFIX: String = "mark-read:"

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

    /** How macOS shows KaSH's notifications (the user's choice). Waits for macOS's answer (2 s at most): not on Swing's thread. */
    public fun alertStyle(): NotificationAlertStyle = when (alertStyle?.invoke() as Int?) {
        0 -> NotificationAlertStyle.NONE
        1 -> NotificationAlertStyle.BANNERS
        2 -> NotificationAlertStyle.ALERTS
        else -> NotificationAlertStyle.UNAVAILABLE
    }

    /**
     * Titles the buttons of the notifications of a conversation ([post]'s `markRead`), in KaSH's language, once [start] succeeded: [direct] its
     * plugin's own "Mark as read", [byOpening] by opening the conversation.
     */
    public fun setMarkReadTitles(direct: String, byOpening: String) {
        val function = markReadTitles ?: return
        if (!started) return
        Arena.ofConfined().use { arena -> function.invoke(MacosLibrary.cString(arena, direct), MacosLibrary.cString(arena, byOpening)) }
    }

    /**
     * Shows a notification, once [start] succeeded. [identifier] is what [start]'s callback is given when it is clicked, after [MARK_READ_PREFIX]
     * when its button [markRead] is.
     */
    public fun post(identifier: String, title: String, subtitle: String, body: String, sound: Boolean, markRead: MarkReadButton = MarkReadButton.NONE) {
        val function = post ?: return
        if (!started) return
        Arena.ofConfined().use { arena ->
            function.invoke(
                MacosLibrary.cString(arena, identifier),
                MacosLibrary.cString(arena, title),
                MacosLibrary.cString(arena, subtitle),
                MacosLibrary.cString(arena, body),
                if (sound) 1 else 0,
                markRead.ordinal,
            )
        }
    }

    /** Takes the notification [identifier] away, from the screen and from the Notification Center's list, once [start] succeeded. */
    public fun remove(identifier: String) {
        val function = remove ?: return
        if (!started) return
        Arena.ofConfined().use { arena -> function.invoke(MacosLibrary.cString(arena, identifier)) }
    }

    /**
     * The identifiers of KaSH's notifications that the Notification Center still shows, those of the last launches too, once [start] succeeded;
     * null when macOS did not answer. Waits for macOS's answer (2 s at most): not on Swing's thread.
     */
    public fun delivered(): List<String>? {
        val function = delivered ?: return null
        if (!started) return null
        var size = 64 * 1024
        repeat(2) {
            Arena.ofConfined().use { arena ->
                val buffer = arena.allocate(size.toLong())
                val length = function.invoke(buffer, size) as Int
                if (length < 0) return null
                if (length < size) return buffer.getString(0).split('\u001F').filter(String::isNotEmpty)
                // More than the buffer: once more, with room for them.
                size = length + 1
            }
        }
        return null
    }
}

/** KaSH's windows, as AppKit has them. */
public object MacosWindows {
    private val nonactivating = MacosLibrary.function("kash_panel_make_nonactivating", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))

    /**
     * Makes KaSH's shown panels titled [title] (AWT windows of type `UTILITY`) not activate KaSH when they are clicked, as macOS's notifications:
     * a click on any other window of KaSH brings it to the front. Done later, on AppKit's thread; nothing outside macOS.
     */
    public fun makeNonactivating(title: String) {
        val function = nonactivating ?: return
        Arena.ofConfined().use { arena -> function.invoke(MacosLibrary.cString(arena, title)) }
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
