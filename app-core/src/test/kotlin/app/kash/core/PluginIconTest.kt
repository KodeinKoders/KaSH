package app.kash.core

import java.net.URI
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PluginIconTest {
    @Test
    fun nullIsNoIcon() {
        assertEquals(PluginIcon.None, PluginIcon.parse(null))
    }

    @Test
    fun anHttpsUrlIsDownloaded() {
        assertEquals(PluginIcon.Remote(URI("https://avatars.example.com/team_132.png")), PluginIcon.parse("https://avatars.example.com/team_132.png"))
    }

    @Test
    fun otherAddressesAreRefused() {
        for (source in listOf("http://example.com/a.png", "file:///etc/passwd", "javascript:alert(1)", "https:///a.png", "a.png", "", "data:text/html;base64,PGI+", "data:image/svg+xml;base64,PHN2Zy8+", "data:image/png,raw")) {
            assertNull(PluginIcon.parse(source), source)
        }
    }

    @Test
    fun aDataUrlOfAnImageIsDecoded() {
        val bytes = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        val icon = assertIs<PluginIcon.Inline>(PluginIcon.parse("data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)))
        assertContentEquals(bytes, icon.bytes)
        assertNull(PluginIcon.parse("data:image/png;base64,not base64!"))
        assertNull(PluginIcon.parse("data:image/png;base64,"))
    }

    @Test
    fun aTooBigImageIsRefused() {
        assertIs<PluginIcon.Inline>(PluginIcon.parse("data:image/png;base64," + Base64.getEncoder().encodeToString(ByteArray(PluginIcon.MAX_BYTES))))
        assertNull(PluginIcon.parse("data:image/png;base64," + Base64.getEncoder().encodeToString(ByteArray(PluginIcon.MAX_BYTES + 1))))
    }
}
