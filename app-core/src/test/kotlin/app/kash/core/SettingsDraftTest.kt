package app.kash.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsDraftTest {
    private val base = Settings()

    @Test
    fun whatChangedElsewhereGoesIntoTheDraft() {
        // Do Not Disturb turned on from the rail, the theme from the title bar, while Settings is open and its draft unchanged.
        val current = base.copy(doNotDisturb = true, theme = ThemeMode.DARK)
        assertEquals(current, SettingsDraft.rebase(base, base, current))
    }

    @Test
    fun whatChangedInSettingsIsKept() {
        val draft = base.copy(sound = !base.sound)
        val current = base.copy(doNotDisturb = true)
        assertEquals(current.copy(sound = !base.sound), SettingsDraft.rebase(draft, base, current))
    }

    @Test
    fun aSettingChangedOnBothSidesIsSettings() {
        val draft = base.copy(theme = ThemeMode.LIGHT)
        val current = base.copy(theme = ThemeMode.DARK, doNotDisturb = true)
        assertEquals(current.copy(theme = ThemeMode.LIGHT), SettingsDraft.rebase(draft, base, current))
    }

    @Test
    fun aListIsOneSetting() {
        val range = QuietRange(days = setOf(1, 2, 3, 4, 5), start = 19 * 60, end = 8 * 60)
        val draft = base.copy(quietHours = listOf(range))
        val current = base.copy(quietResumedUntil = "2026-11-24T19:00")
        assertEquals(current.copy(quietHours = listOf(range)), SettingsDraft.rebase(draft, base, current))
    }
}
