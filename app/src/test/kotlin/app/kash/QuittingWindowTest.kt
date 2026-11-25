@file:OptIn(ExperimentalTestApi::class)

package app.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

class QuittingWindowTest {
    @Test
    fun theOverlaySaysThatKashQuitsInBothThemes() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                setContent {
                    KashTheme(dark) {
                        // Over a window's content, as it is shown.
                        Box(Modifier.size(640.dp, 400.dp).background(Kash.colors.background)) { QuittingContent() }
                    }
                }
                onNodeWithText("Quitting KaSH…").assertExists()
                // To look at: build/renders/quitting-light.png, quitting-dark.png.
                File("build/renders").mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/renders/quitting-${if (dark) "dark" else "light"}.png"))
            }
        }
    }
}
