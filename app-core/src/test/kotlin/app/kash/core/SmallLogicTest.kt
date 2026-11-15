package app.kash.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmallLogicTest {
    @Test
    fun theCountIsReadAtTheStartOfTheTitle() {
        assertEquals(3, UnreadCount.fromTitle("(3) Mon application"))
        assertEquals(99, UnreadCount.fromTitle(" (99+) Inbox"))
        assertNull(UnreadCount.fromTitle("Mon application"))
        assertNull(UnreadCount.fromTitle("Mon application (3)"))
        assertEquals("99+", UnreadCount.badge(100))
        assertEquals("7", UnreadCount.badge(7))
    }

    @Test
    fun aNotificationNeedsEverythingOn() {
        val service = Service(ServiceId.random(), "whatsapp", name = "WhatsApp", url = "https://web.whatsapp.com/", profile = ProfileId.random())
        val settings = Settings()
        assertEquals(NotificationDecision(show = true, sound = true), NotificationPolicy.decide(settings, service))
        assertEquals(NotificationDecision(show = true, sound = false), NotificationPolicy.decide(settings.copy(sound = false), service))
        assertFalse(NotificationPolicy.decide(settings.copy(doNotDisturb = true), service).show)
        assertFalse(NotificationPolicy.decide(settings.copy(notifications = false), service).show)
        assertFalse(NotificationPolicy.decide(settings, service.copy(muted = true)).show)
        assertFalse(NotificationPolicy.decide(settings, service.copy(desktopNotifications = false)).show)
        assertFalse(NotificationPolicy.decide(settings, service.copy(enabled = false)).sound)
    }

    @Test
    fun colorsAreParsedAsTheDesignDoes() {
        assertEquals("#3058CF", SquareColors.parse("3058cf"))
        assertEquals("#AABBCC", SquareColors.parse("#abc"))
        assertNull(SquareColors.parse("12345"))
        assertNull(SquareColors.parse("GGGGGG"))
        assertTrue(SquareColors.whiteTextOn("#3058CF"))
        assertFalse(SquareColors.whiteTextOn("#EBD48E"))
    }

    @Test
    fun monoIsTwoLetters() {
        assertEquals("MA", WebApplications.monoOf("Mon application"))
        assertEquals("Ex", WebApplications.monoOf("exemple"))
        assertEquals("?", WebApplications.monoOf("  "))
    }
}
