package app.kash

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IconSizeTest {
    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", it) }.toByteArray()

    @Test
    fun anIconThatIsTooBigInPixelsIsNotDecoded() {
        // A small file of 4096 × 4096 transparent pixels: refused before its bitmap is made.
        assertNull(decodeIcon(png(4096, 4096)))
        assertNotNull(decodeIcon(png(64, 64)))
        assertNotNull(decodeIcon(png(1024, 512)))
        assertNull(decodeIcon(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun anIconThatCannotBeHadIsTriedAgainLaterAndLater() {
        assertEquals(listOf(1, 2, 4, 8, 16, 32, 60, 60).map { it * 60_000L }, (1..8).map(::retryDelay))
    }
}
