package app.kash.core

/** Whether a notification of a service is shown, and whether it makes a sound. */
public data class NotificationDecision(val show: Boolean, val sound: Boolean)

public object NotificationPolicy {
    /**
     * A notification of [service] is shown when notifications are on, Do Not Disturb is off, and the service is enabled, has its notifications on
     * and is not muted; a [priority] contact's ([PriorityContact]) even in Do Not Disturb and when the service is muted (the user's choice). It
     * makes a sound when it is shown and the sound is on.
     */
    public fun decide(settings: Settings, service: Service, priority: Boolean = false): NotificationDecision {
        val quiet = settings.doNotDisturb || service.muted
        val show = settings.notifications && service.enabled && service.desktopNotifications && (priority || !quiet)
        return NotificationDecision(show = show, sound = show && settings.sound)
    }
}
