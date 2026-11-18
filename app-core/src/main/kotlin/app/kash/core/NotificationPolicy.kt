package app.kash.core

import java.time.LocalDateTime

/** Whether a notification of a service is shown, and whether it makes a sound. */
public data class NotificationDecision(val show: Boolean, val sound: Boolean)

public object NotificationPolicy {
    /**
     * A notification of [service] is shown at [now] when notifications are on, Do Not Disturb is off (the switch, the ranges of the settings:
     * [DoNotDisturb.isOn]), and the service is enabled, has its notifications on and is not quiet (muted, or in one of its ranges); a [priority]
     * contact's ([PriorityContact]) even in Do Not Disturb and when the service is quiet (the user's choice). It makes a sound when it is shown
     * and the sound is on.
     */
    public fun decide(settings: Settings, service: Service, priority: Boolean = false, now: LocalDateTime = LocalDateTime.now()): NotificationDecision {
        val quiet = DoNotDisturb.isOn(settings, now) || DoNotDisturb.isQuiet(service, now)
        val show = settings.notifications && service.enabled && service.desktopNotifications && (priority || !quiet)
        return NotificationDecision(show = show, sound = show && settings.sound)
    }
}
