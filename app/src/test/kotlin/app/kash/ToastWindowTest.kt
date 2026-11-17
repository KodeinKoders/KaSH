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
    fun aContactsNotificationShowsItsCountWhenThereAreSeveralMessages() = runComposeUiTest {
        var count by mutableStateOf(3)
        setContent { KashTheme(false) { ToastContent(listOf(toast), icons, { count }, onClick = {}, onClose = {}) } }
        onNodeWithText("3").assertExists()

        // One message: the one shown, without a count.
        count = 1
        onNodeWithText("1").assertDoesNotExist()
    }
}
