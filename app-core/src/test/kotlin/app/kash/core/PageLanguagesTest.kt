package app.kash.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PageLanguagesTest {
    @Test
    fun frenchThenEnglish() {
        assertEquals("fr-FR,fr,en-US,en", PageLanguages.acceptLanguages("fr"))
    }

    @Test
    fun englishAlone() {
        assertEquals("en-US,en", PageLanguages.acceptLanguages("en"))
    }

    @Test
    fun aLanguageWithoutRegionThenEnglish() {
        assertEquals("de,en-US,en", PageLanguages.acceptLanguages("de"))
    }

    @Test
    fun chromiumLocales() {
        assertEquals("fr", PageLanguages.locale("fr"))
        assertEquals("en-US", PageLanguages.locale("en"))
    }
}
