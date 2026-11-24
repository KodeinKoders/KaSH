@file:OptIn(ExperimentalTestApi::class)

package app.kash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.kash.core.QuietRange
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuietHoursEditorTest {
    @Test
    fun aTimeIsReadAsTheUserWritesIt() {
        assertEquals(8 * 60, parseTime("08:00"))
        assertEquals(8 * 60, parseTime("8:00"))
        assertEquals(21 * 60 + 30, parseTime("21h30"))
        assertEquals(19 * 60, parseTime("19h"))
        assertEquals(7 * 60, parseTime(" 7 "))
        assertNull(parseTime("25:00"))
        assertNull(parseTime("8:75"))
        assertNull(parseTime("soon"))
        assertEquals("08:05", timeText(8 * 60 + 5))
    }

    @Test
    fun rangesAreAddedTheirDaysToggledTheirTimesWrittenAndRemoved() = runComposeUiTest {
        var ranges by mutableStateOf(emptyList<QuietRange>())
        setContent { KashTheme(false) { QuietHoursEditor(ranges) { ranges = it } } }

        onNodeWithText("Add a range").performClick()
        assertEquals(listOf(NEW_QUIET_RANGE), ranges)

        // Saturday is added to the evenings of the week.
        onAllNodesWithContentDescription("Saturday")[0].performClick()
        assertEquals(setOf(1, 2, 3, 4, 5, 6), ranges.single().days)

        // A time that is not one is not kept; one that is, is.
        onNodeWithContentDescription("from").performTextReplacement("25:00")
        waitForIdle()
        assertEquals(19 * 60, ranges.single().start)
        onNodeWithContentDescription("from").performTextReplacement("21h30")
        waitForIdle()
        assertEquals(21 * 60 + 30, ranges.single().start)

        onNodeWithContentDescription("Remove the range").performClick()
        assertEquals(emptyList(), ranges)
    }

    @Test
    fun removingARangeShowsTheTimesOfTheOneInItsPlace() = runComposeUiTest {
        val evenings = QuietRange(days = setOf(1, 2, 3, 4, 5), start = 19 * 60, end = 8 * 60)
        val weekend = QuietRange(days = setOf(6, 7), start = 0, end = 0)
        var ranges by mutableStateOf(listOf(evenings, weekend))
        setContent { KashTheme(false) { QuietHoursEditor(ranges) { ranges = it } } }

        onAllNodesWithContentDescription("Remove the range")[0].performClick()
        waitForIdle()
        assertEquals(listOf(weekend), ranges)
        // The line in its place shows the weekend's times, which are the ones kept.
        onNodeWithContentDescription("from").assert(hasText("00:00"))
        onNodeWithContentDescription("to").assert(hasText("00:00"))
        assertEquals(listOf(weekend), ranges)
    }

    @Test
    fun theEditorIsDrawnInBothThemes() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                setContent {
                    KashTheme(dark) {
                        Box(Modifier.width(560.dp).background(Kash.colors.background).padding(12.dp)) {
                            QuietHoursEditor(listOf(NEW_QUIET_RANGE, QuietRange(setOf(6, 7), 0, 0))) {}
                        }
                    }
                }
                // To look at: build/renders/quiet-hours-light.png, quiet-hours-dark.png.
                File("build/renders").mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/renders/quiet-hours-${if (dark) "dark" else "light"}.png"))
            }
        }
    }
}
