package app.kash.core

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * A range of Do Not Disturb: the [days] when it starts (ISO, 1 Monday to 7 Sunday), from [start] to [end] (minutes since midnight). One whose
 * end is before its start ends the next day ("19:00–08:00"); one whose end is its start lasts the whole day.
 */
@Serializable
public data class QuietRange(
    val days: Set<Int>,
    val start: Int,
    val end: Int,
)

/** When ranges of Do Not Disturb ([QuietRange]) are quiet. */
public object QuietHours {
    private const val DAY = 24 * 60

    // The times when the range is quiet, starting on [day] (none when it does not start that day).
    private fun intervalOn(range: QuietRange, day: LocalDate): Pair<LocalDateTime, LocalDateTime>? {
        if (day.dayOfWeek.value !in range.days) return null
        val start = day.atStartOfDay().plusMinutes(range.start.toLong())
        val length = (range.end - range.start).let { if (it <= 0) it + DAY else it }
        return start to start.plusMinutes(length.toLong())
    }

    // Every time that [ranges] are quiet, starting from the day before [from] to [days] days after it.
    private fun intervals(ranges: List<QuietRange>, from: LocalDate, days: Long) =
        (-1L..days).flatMap { offset -> ranges.mapNotNull { intervalOn(it, from.plusDays(offset)) } }

    /** Whether [ranges] are quiet at [at]: a range that ends the next day is quiet that morning for the day it started. */
    public fun isQuiet(ranges: List<QuietRange>, at: LocalDateTime): Boolean =
        intervals(ranges, at.toLocalDate(), 0).any { (start, end) -> !at.isBefore(start) && at.isBefore(end) }

    /**
     * When the quiet time that [at] is in ends, through the ranges that follow one another or overlap ("Monday to Friday 19:00–08:00" and the
     * weekend's whole days are one quiet time, from Friday evening to Monday morning); null when [at] is not quiet. A week that is all quiet
     * ends after 8 days, to say something.
     */
    public fun endOf(ranges: List<QuietRange>, at: LocalDateTime): LocalDateTime? {
        if (!isQuiet(ranges, at)) return null
        val all = intervals(ranges, at.toLocalDate(), 9)
        val limit = at.plusDays(8)
        var current = at
        while (current.isBefore(limit)) {
            val next = all.filter { (start, end) -> !current.isBefore(start) && current.isBefore(end) }.maxOfOrNull { it.second } ?: return current
            current = next
        }
        return limit
    }
}

/**
 * Do Not Disturb as it is now: the rail's switch ([Settings.doNotDisturb]), or a range of [Settings.quietHours] that is not resumed; and a
 * service's own quiet: its mute, or one of its ranges ([Service.quietHours]).
 */
public object DoNotDisturb {
    private fun resumedUntil(settings: Settings): LocalDateTime? = settings.quietResumedUntil?.let {
        try {
            LocalDateTime.parse(it)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    // A range of the settings is quiet now, and the rail's bell did not resume it.
    private fun scheduled(settings: Settings, now: LocalDateTime): Boolean =
        QuietHours.isQuiet(settings.quietHours, now) && resumedUntil(settings)?.let { now.isBefore(it) } != true

    /** Whether Do Not Disturb is on at [now]: the switch, or a range of the settings that was not resumed. */
    public fun isOn(settings: Settings, now: LocalDateTime): Boolean = settings.doNotDisturb || scheduled(settings, now)

    /** When the range of the settings that makes Do Not Disturb on at [now] ends; null when it is not a range's (the switch's, or off). */
    public fun until(settings: Settings, now: LocalDateTime): LocalDateTime? =
        if (!settings.doNotDisturb && scheduled(settings, now)) QuietHours.endOf(settings.quietHours, now) else null

    /**
     * What a click on the rail's bell does at [now]: the switch on goes off; a range going on is resumed until it ends (the next one comes as
     * planned), also when the switch was on; Do Not Disturb off (a range resumed too) turns the switch on.
     */
    public fun toggle(settings: Settings, now: LocalDateTime): Settings {
        val rangeEnd = QuietHours.endOf(settings.quietHours, now)
        return when {
            settings.doNotDisturb -> settings.copy(doNotDisturb = false, quietResumedUntil = rangeEnd?.toString() ?: settings.quietResumedUntil)
            scheduled(settings, now) -> settings.copy(quietResumedUntil = rangeEnd?.toString())
            else -> settings.copy(doNotDisturb = true)
        }
    }

    /** Whether [service] is quiet at [now]: muted, or in one of its ranges. */
    public fun isQuiet(service: Service, now: LocalDateTime): Boolean = service.muted || QuietHours.isQuiet(service.quietHours, now)
}
