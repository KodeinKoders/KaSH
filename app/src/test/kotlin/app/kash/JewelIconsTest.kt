@file:OptIn(ExperimentalTestApi::class)

package app.kash

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.kash.ui.KashTheme
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JewelIconsTest {
    @Test
    fun theIconsOfJewelsComponentsThatKashUsesAreThere() {
        // Jewel's standalone theme draws them from IntelliJ's icons, which it does not ship: KaSH has them in its resources.
        val chevron = AllIconsKeys.General.ChevronDown.path(true)
        assertNotNull(AllIconsKeys::class.java.classLoader.getResource(chevron), chevron)
    }

    @Test
    fun aComboBoxHasItsChevronAndNotJewelsPinkSquare() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                setContent { KashTheme(dark) { ListComboBox(listOf("Gauche", "Droite"), 0, {}, Modifier.width(160.dp)) } }
                val image = onRoot().captureToImage()
                // To look at: build/renders/combo-box-light.png, combo-box-dark.png.
                File("build/renders").mkdirs()
                ImageIO.write(image.toAwtImage(), "png", File("build/renders/combo-box-${if (dark) "dark" else "light"}.png"))
                val pixels = image.toPixelMap()
                var pink = 0
                for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
                    val c = pixels[x, y]
                    // What Jewel draws for an icon that it cannot find: magenta.
                    if (c.red > .8f && c.blue > .8f && c.green < .3f) pink++
                }
                assertEquals(0, pink, "pink pixels in the ${if (dark) "dark" else "light"} theme")
            }
        }
    }
}
