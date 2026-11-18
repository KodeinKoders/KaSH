package app.kash.core

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuietHoursTest {
    // 2026-11-16 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, 16 + day, hour, minute)
    private val monday = 0
    private val tuesday = 1
    private val friday = 4
    private val saturday = 5
    private val sunday = 6
    private fun minutes(hour: Int, minute: Int = 0) = hour * 60 + minute

    private val evenings = QuietRange(setOf(1, 2, 3, 4, 5), minutes(19), minutes(8))
    private val weekend = QuietRange(setOf(6, 7), 0, 0)

    @Test
    fun aRangeOfTheDayIsQuietBetweenItsTimesOnItsDays() {
        val lunch = listOf(QuietRange(setOf(1), minutes(12), minutes(14)))
        assertTrue(QuietHours.isQuiet(lunch, at(monday, 12)))
        assertTrue(QuietHours.isQuiet(lunch, at(monday, 13, 59)))
        assertFalse(QuietHours.isQuiet(lunch, at(monday, 14)))
        assertFalse(QuietHours.isQuiet(lunch, at(tuesday, 13)))
    }

    @Test
    fun aRangeThatEndsBeforeItStartsEndsTheNextDay() {
        val ranges = listOf(evenings)
        assertTrue(QuietHours.isQuiet(ranges, at(monday, 23)))
        assertTrue(QuietHours.isQuiet(ranges, at(tuesday, 7)))
        assertFalse(QuietHours.isQuiet(ranges, at(tuesday, 9)))
        // Friday's evening goes on to Saturday's morning, but Saturday has none of its own.
        assertTrue(QuietHours.isQuiet(ranges, at(saturday, 7)))
        assertFalse(QuietHours.isQuiet(ranges, at(saturday, 20)))
        // Sunday's evening is not one of its days, nor is Monday's morning.
        assertFalse(QuietHours.isQuiet(ranges, at(monday, 7)))
    }

    @Test
    fun aRangeWhoseEndIsItsStartIsTheWholeDay() {
        assertTrue(QuietHours.isQuiet(listOf(weekend), at(saturday, 0)))
        assertTrue(QuietHours.isQuiet(listOf(weekend), at(sunday, 23, 59)))
        assertFalse(QuietHours.isQuiet(listOf(weekend), at(monday, 0)))
    }

    @Test
    fun theEndOfAQuietTimeFollowsTheRangesThatFollowOneAnother() {
        val ranges = listOf(evenings, weekend)
        // Friday evening, the weekend, then Monday is not one of the evenings' mornings: Monday 00:00.
        assertEquals(at(7, 0), QuietHours.endOf(ranges, at(friday, 20)))
        assertEquals(at(tuesday, 8), QuietHours.endOf(ranges, at(monday, 22)))
        assertNull(QuietHours.endOf(ranges, at(tuesday, 12)))
        // A week that is all quiet ends after 8 days.
        val always = listOf(QuietRange((1..7).toSet(), 0, 0))
        assertEquals(at(tuesday, 12).plusDays(8), QuietHours.endOf(always, at(tuesday, 12)))
    }

    private val settings = Settings(quietHours = listOf(evenings))

    @Test
    fun theBellResumesARangeUntilItEndsAndTheNextOneComes() {
        assertTrue(DoNotDisturb.isOn(settings, at(monday, 22)))
        assertEquals(at(tuesday, 8), DoNotDisturb.until(settings, at(monday, 22)))
        val resumed = DoNotDisturb.toggle(settings, at(monday, 22))
        assertFalse(resumed.doNotDisturb)
        assertFalse(DoNotDisturb.isOn(resumed, at(monday, 23)))
        // The next one, as planned.
        assertTrue(DoNotDisturb.isOn(resumed, at(tuesday, 20)))
        // Off by a range resumed, the bell turns the switch on, which has no end.
        val on = DoNotDisturb.toggle(resumed, at(monday, 23))
        assertTrue(on.doNotDisturb)
        assertTrue(DoNotDisturb.isOn(on, at(monday, 23)))
        assertNull(DoNotDisturb.until(on, at(monday, 23)))
    }

    @Test
    fun outOfARangeTheBellIsTheSwitch() {
        val on = DoNotDisturb.toggle(settings, at(tuesday, 12))
        assertTrue(on.doNotDisturb)
        val off = DoNotDisturb.toggle(on, at(tuesday, 12))
        assertFalse(DoNotDisturb.isOn(off, at(tuesday, 12)))
        // The switch on during a range: off, and the range resumed with it.
        val both = settings.copy(doNotDisturb = true)
        assertFalse(DoNotDisturb.isOn(DoNotDisturb.toggle(both, at(monday, 22)), at(monday, 23)))
    }

    @Test
    fun aServiceInItsRangeIsQuietButForAPriorityContact() {
        val service = Service(ServiceId("s"), "slack", name = "Slack", url = "https://acme.slack.com/", profile = ProfileId.random(), quietHours = listOf(evenings))
        assertTrue(DoNotDisturb.isQuiet(service, at(monday, 22)))
        assertFalse(NotificationPolicy.decide(Settings(), service, now = at(monday, 22)).show)
        assertTrue(NotificationPolicy.decide(Settings(), service, now = at(tuesday, 12)).show)
        assertTrue(NotificationPolicy.decide(Settings(), service, priority = true, now = at(monday, 22)).show)
        // The settings' ranges too.
        assertFalse(NotificationPolicy.decide(settings, service.copy(quietHours = emptyList()), now = at(monday, 22)).show)
    }

    @Test
    fun theRangesAreKept() {
        val directory = java.nio.file.Files.createTempDirectory("kash-quiet")
        val file = StateFile(directory.resolve("state.json"))
        val service = Service(ServiceId("s"), "slack", name = "Slack", url = "https://acme.slack.com/", profile = ProfileId.random(), quietHours = listOf(weekend))
        val state = KashState(services = listOf(service), settings = settings.copy(quietResumedUntil = at(tuesday, 8).toString()))
        file.save(state)
        assertEquals(state.settings, file.load().settings)
        assertEquals(listOf(weekend), file.load().services.single().quietHours)
        directory.toFile().deleteRecursively()
    }
}
