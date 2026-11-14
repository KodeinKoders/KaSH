package app.kash.core

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeLabelTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val now = Instant.parse("2026-11-14T15:00:00Z")
    private val french = TimeLabel(Locale.FRENCH, "HH:mm", "'Hier', HH:mm", "d MMM, HH:mm", "d MMM yyyy, HH:mm")
    private val english = TimeLabel(Locale.ENGLISH, "h:mm a", "'Yesterday', h:mm a", "MMM d, h:mm a", "MMM d, yyyy, h:mm a")

    private fun at(text: String) = Instant.parse(text).toEpochMilli()

    @Test
    fun todayIsTheTimeAlone() {
        assertEquals("09:05", french.of(at("2026-11-14T08:05:00Z"), now, zone))
        assertEquals("9:05 AM", english.of(at("2026-11-14T08:05:00Z"), now, zone))
    }

    @Test
    fun yesterdayIsSaid() {
        assertEquals("Hier, 18:30", french.of(at("2026-11-13T17:30:00Z"), now, zone))
        assertEquals("Yesterday, 6:30 PM", english.of(at("2026-11-13T17:30:00Z"), now, zone))
        // Yesterday at 00:10 in Paris is still yesterday, though it is the day before in UTC.
        assertEquals("Hier, 00:10", french.of(at("2026-11-12T23:10:00Z"), now, zone))
    }

    @Test
    fun anotherDayHasTheDate() {
        assertEquals("12 nov., 18:30", french.of(at("2026-11-12T17:30:00Z"), now, zone))
        assertEquals("Nov 12, 6:30 PM", english.of(at("2026-11-12T17:30:00Z"), now, zone))
        // The day before yesterday is a date.
        assertEquals("12 nov., 23:50", french.of(at("2026-11-12T22:50:00Z"), now, zone))
    }

    @Test
    fun anotherYearHasTheYear() {
        assertEquals("31 déc. 2025, 23:00", french.of(at("2025-12-31T22:00:00Z"), now, zone))
    }

    @Test
    fun todayIsTheDayInTheZoneNotInUtc() {
        // 23:30 on the 13th in UTC is 00:30 on the 14th in Paris: today, in the zone where it is shown.
        assertEquals("00:30", french.of(at("2026-11-13T23:30:00Z"), now, zone))
    }
}
