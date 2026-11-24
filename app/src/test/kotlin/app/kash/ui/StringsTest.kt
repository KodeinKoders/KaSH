package app.kash.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class StringsTest {
    @Test
    fun englishPutsAPluralOnEverythingButOne() {
        assertEquals("0 unread messages in 0 conversations", English.inboxSummary(0, 0))
        assertEquals("1 unread message in 1 conversation", English.inboxSummary(1, 1))
        assertEquals("2 unread messages in 2 conversations", English.inboxSummary(2, 2))
        assertEquals("0 services · 0 enabled", English.servicesCount(0, 0))
        assertEquals("1 service · 1 enabled", English.servicesCount(1, 1))
    }

    @Test
    fun frenchPutsOneOnTwoAndMore() {
        assertEquals("0 service · 0 activé", French.servicesCount(0, 0))
        assertEquals("2 services · 2 activés", French.servicesCount(2, 2))
    }
}
