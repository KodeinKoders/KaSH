@file:OptIn(ExperimentalTestApi::class)

package app.kash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
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
import java.nio.file.Files
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
}
