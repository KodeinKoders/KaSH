@file:OptIn(ExperimentalTestApi::class)

package app.kash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import app.kash.ui.Kash
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.kash.core.KashDirectories
import app.kash.core.PluginRegistry
import app.kash.core.ProfileId
import app.kash.core.Service
import app.kash.core.ServiceId
import app.kash.ui.KashTheme
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class ToastWindowTest {
    private val whatsApp = Service(ServiceId("w"), "whatsapp", name = "WhatsApp", url = "https://web.whatsapp.com/", profile = ProfileId.random())
    private val icons = ServiceIcons(KashDirectories(Files.createTempDirectory("kash-toasts"))) { PluginRegistry(emptyList()) }
    private val toast = Toast("w|laila", whatsApp, "Laila", "À tout à l'heure", received = 3)

    @Test
    fun theCrossClosesTheNotificationWithoutClickingIt() = runComposeUiTest {
        val clicked = mutableListOf<String>()
        val closed = mutableListOf<String>()
        setContent { KashTheme(false) { ToastContent(listOf(toast), icons, { it.received }, onClick = { clicked += it.key }, onClose = { closed += it.key }) } }

        onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf("w|laila"), closed)
        assertEquals(emptyList(), clicked)

        onNodeWithText("Laila").performClick()
        assertEquals(listOf("w|laila"), clicked)
    }

    @Test
    fun aNotificationWhosePluginCanMarksItsConversationAsRead() = runComposeUiTest {
        val marked = mutableListOf<String>()
        var markable by mutableStateOf<MarkRead?>(null)
        setContent {
            KashTheme(false) { ToastContent(listOf(toast), icons, { 1 }, onClick = {}, onClose = {}, markReadOf = { markable }, onMarkRead = { marked += it.key }) }
        }
        onNodeWithContentDescription("Mark as read").assertDoesNotExist()

        markable = MarkRead.DIRECT
        onNodeWithContentDescription("Mark as read").performClick()
        assertEquals(listOf("w|laila"), marked)

        // A service that only marks a conversation as read by opening it says so.
        markable = MarkRead.BY_OPENING
        onNodeWithContentDescription("Mark as read (by opening it)").performClick()
        assertEquals(listOf("w|laila", "w|laila"), marked)
    }

    @Test
    fun aContactsNotificationShowsItsCountWhenThereAreSeveralMessages() = runComposeUiTest {
        var count by mutableStateOf(3)
        setContent { KashTheme(false) { ToastContent(listOf(toast), icons, { count }, onClick = {}, onClose = {}) } }
        onNodeWithText("3").assertExists()

        // One message: the one shown, without a count.
        count = 1
        onNodeWithText("1").assertDoesNotExist()
    }

    @Test
    fun theButtonUnderThePointerIsShown() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                setContent {
                    KashTheme(dark) {
                        Box(Modifier.background(Kash.colors.background).padding(12.dp)) {
                            Box(Modifier.width(360.dp).height(76.dp)) {
                                ToastContent(listOf(toast), icons, { 1 }, onClick = {}, onClose = {}, markReadOf = { MarkRead.DIRECT })
                            }
                        }
                    }
                }
                onNodeWithContentDescription("Close").performMouseInput { moveTo(center) }
                waitForIdle()
                // To look at: build/renders/toast-hover-light.png, toast-hover-dark.png (the cross under the pointer, the check next to it not).
                File("build/renders").mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/renders/toast-hover-${if (dark) "dark" else "light"}.png"))
            }
        }
    }
}
