@file:OptIn(ExperimentalTestApi::class)

package app.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.kash.ui.Kash
import app.kash.ui.KashTheme
import app.kash.ui.TooltipContent
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RailTooltipTest {
    @Test
    fun theFirstNineServicesHaveTheirShortcut() {
        assertEquals("⌘1", serviceShortcut(0))
        assertEquals("⌘9", serviceShortcut(8))
        assertNull(serviceShortcut(9))
        assertEquals("⌘0", INBOX_SHORTCUT)
    }

    @Test
    fun theTooltipIsDrawnInBothThemes() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                setContent {
                    KashTheme(dark) { Box(Modifier.background(Kash.colors.panel).padding(12.dp)) { TooltipContent("WhatsApp", "⌘1") } }
                }
                onNodeWithText("⌘1").assertExists()
                // To look at: build/renders/tooltip-light.png, tooltip-dark.png.
                File("build/renders").mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/renders/tooltip-${if (dark) "dark" else "light"}.png"))
            }
        }
    }
}
