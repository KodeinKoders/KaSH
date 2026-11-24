@file:OptIn(ExperimentalTestApi::class)

package app.kash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.kash.core.ConversationKind
import app.kash.core.KashDirectories
import app.kash.core.PluginRegistry
import app.kash.core.ProfileId
import app.kash.core.SearchEntry
import app.kash.core.Service
import app.kash.core.ServiceId
import app.kash.ui.Kash
import app.kash.ui.KashTheme
import app.kash.ui.LocalUiScale
import app.kash.ui.MenuFrame
import org.jetbrains.jewel.ui.component.DefaultMenuController
import org.jetbrains.jewel.ui.component.LocalMenuController
import app.kash.ui.UiScaled
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test

class UiScaleRenderTest {
    private val whatsApp = Service(ServiceId("w"), "whatsapp", name = "WhatsApp", url = "https://web.whatsapp.com/", profile = ProfileId.random())
    private val icons = ServiceIcons(KashDirectories(Files.createTempDirectory("kash-scale"))) { PluginRegistry(emptyList()) }

    @Test
    fun theTextGrowsWithTheSettingAndStaysInItsBoxes() {
        for (scale in listOf(100, 175)) {
            runComposeUiTest {
                setContent {
                    CompositionLocalProvider(LocalUiScale provides scale / 100f) {
                        KashTheme(false) {
                            UiScaled {
                                Column(Modifier.width(720.dp).background(Kash.colors.background).padding(12.dp)) {
                                    Box(Modifier.width(360.dp).height(76.dp * (scale / 100f))) {
                                        ToastContent(listOf(Toast("w|laila", whatsApp, "Laila", "À tout à l'heure, on se retrouve devant", 3)), icons, { 3 }, {}, {})
                                    }
                                    Box(Modifier.height(320.dp)) {
                                        SearchContent(listOf(SearchEntry(whatsApp, "laila", "Laila", ConversationKind.DIRECT, 2)), icons, {}, {})
                                    }
                                    // The service's menu, its shortcuts at the right of their rows.
                                    CompositionLocalProvider(LocalMenuController provides DefaultMenuController(onDismissRequest = { true })) {
                                        MenuFrame(260.dp * (scale / 100f)) { serviceMenu(whatsApp, ServiceActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}), devToolsEnabled = true) {} }
                                    }
                                }
                            }
                        }
                    }
                }
                onNodeWithText("Laila", useUnmergedTree = true)
                // To look at: build/renders/ui-scale-100.png, ui-scale-175.png.
                File("build/renders").mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/renders/ui-scale-$scale.png"))
            }
        }
    }

    @Test
    fun aDialogKeepsItsButtonsAtABiggerTextWithALongMessage() {
        val scale = 1.75f
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalUiScale provides scale) {
                    KashTheme(false) {
                        UiScaled {
                            Box(Modifier.width(MESSAGE_DIALOG_SIZE.width * scale).height(MESSAGE_DIALOG_SIZE.height * scale)) {
                                MessageContent("Effacer les données de WhatsApp · Travail ?", "Une très longue raison. ".repeat(40)) {
                                    org.jetbrains.jewel.ui.component.DefaultButton(onClick = {}) { org.jetbrains.jewel.ui.component.Text("OK") }
                                }
                            }
                        }
                    }
                }
            }
            onNodeWithText("OK").assertIsDisplayed()
            // To look at: build/renders/ui-scale-dialog-175.png.
            File("build/renders").mkdirs()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/renders/ui-scale-dialog-175.png"))
        }
    }
}
