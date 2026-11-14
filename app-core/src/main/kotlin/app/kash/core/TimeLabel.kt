package app.kash.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How the Unread tab says when a conversation's last message came: the time alone today, "yesterday" and the time the day before, the date and
 * the time on another day, with the year when it is not this year's.
 *
 * @property time The pattern of a time (`HH:mm`).
 * @property yesterdayAndTime The pattern of yesterday and a time, the word quoted (`'Hier', HH:mm`).
 * @property dayAndTime The pattern of a day of this year and a time (`d MMM, HH:mm`).
 * @property dateAndTime The pattern of a day of another year and a time (`d MMM yyyy, HH:mm`).
 */
public class TimeLabel(
    private val locale: Locale,
    time: String,
    yesterdayAndTime: String,
    dayAndTime: String,
    dateAndTime: String,
) {
    private val time = DateTimeFormatter.ofPattern(time, locale)
    private val yesterdayAndTime = DateTimeFormatter.ofPattern(yesterdayAndTime, locale)
    private val dayAndTime = DateTimeFormatter.ofPattern(dayAndTime, locale)
    private val dateAndTime = DateTimeFormatter.ofPattern(dateAndTime, locale)

    /** [epochMillis] as the Unread tab shows it, [now] being in [zone]. */
    public fun of(epochMillis: Long, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val today = now.atZone(zone)
        return when {
            at.toLocalDate() == today.toLocalDate() -> time.format(at)
            at.toLocalDate() == today.toLocalDate().minusDays(1) -> yesterdayAndTime.format(at)
            at.year == today.year -> dayAndTime.format(at)
            else -> dateAndTime.format(at)
        }
    }

    override fun toString(): String = "TimeLabel($locale)"
}
