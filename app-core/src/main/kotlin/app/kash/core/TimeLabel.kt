package app.kash.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How the Unread tab says when a conversation's last message came: the time alone today, "yesterday" and the time the day before, the date and
 * the time on another day, with the year when it is not this year's. A time that only tells the day (a service that shows no time) is said
 * without a time: "today", "yesterday", the date.
 *
 * @property time The pattern of a time (`HH:mm`).
 * @property yesterdayAndTime The pattern of yesterday and a time, the word quoted (`'Hier', HH:mm`).
 * @property dayAndTime The pattern of a day of this year and a time (`d MMM, HH:mm`).
 * @property dateAndTime The pattern of a day of another year and a time (`d MMM yyyy, HH:mm`).
 * @property today The word for today, quoted (`'Aujourd''hui'`).
 * @property yesterday The word for yesterday, quoted (`'Hier'`).
 * @property day The pattern of a day of this year (`d MMM`).
 * @property date The pattern of a day of another year (`d MMM yyyy`).
 */
public class TimeLabel(
    private val locale: Locale,
    time: String,
    yesterdayAndTime: String,
    dayAndTime: String,
    dateAndTime: String,
    today: String,
    yesterday: String,
    day: String,
    date: String,
) {
    private val time = DateTimeFormatter.ofPattern(time, locale)
    private val yesterdayAndTime = DateTimeFormatter.ofPattern(yesterdayAndTime, locale)
    private val dayAndTime = DateTimeFormatter.ofPattern(dayAndTime, locale)
    private val dateAndTime = DateTimeFormatter.ofPattern(dateAndTime, locale)
    private val today = DateTimeFormatter.ofPattern(today, locale)
    private val yesterday = DateTimeFormatter.ofPattern(yesterday, locale)
    private val day = DateTimeFormatter.ofPattern(day, locale)
    private val date = DateTimeFormatter.ofPattern(date, locale)

    /** [epochMillis] as the Unread tab shows it, [now] being in [zone]; only its day when [dayOnly]. */
    public fun of(epochMillis: Long, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault(), dayOnly: Boolean = false): String {
        val at = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val current = now.atZone(zone)
        return when {
            at.toLocalDate() == current.toLocalDate() -> (if (dayOnly) today else time).format(at)
            at.toLocalDate() == current.toLocalDate().minusDays(1) -> (if (dayOnly) yesterday else yesterdayAndTime).format(at)
            at.year == current.year -> (if (dayOnly) day else dayAndTime).format(at)
            else -> (if (dayOnly) date else dateAndTime).format(at)
        }
    }

    override fun toString(): String = "TimeLabel($locale)"
}
