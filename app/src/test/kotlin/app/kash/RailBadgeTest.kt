@file:OptIn(ExperimentalTestApi::class)

package app.kash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.kash.core.IconSize
import app.kash.core.KashDirectories
import app.kash.core.PluginRegistry
import app.kash.core.ProfileId
import app.kash.core.RailSide
import app.kash.core.Selection
import app.kash.core.Service
import app.kash.core.ServiceId
import app.kash.ui.KashTheme
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test

class RailBadgeTest {
    private val icons = ServiceIcons(KashDirectories(Files.createTempDirectory("kash-rail"))) { PluginRegistry(emptyList()) }
    private val whatsApp = Service(ServiceId("w"), "whatsapp", name = "WhatsApp", url = "https://web.whatsapp.com/", profile = ProfileId.random())
    private val slack = Service(ServiceId("s"), "slack", name = "Slack", url = "https://kodein.slack.com/", profile = ProfileId.random(), muted = true)

    @Test
    fun theBadgeOfAServiceWhoseNotificationsAreOffIsGray() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                setContent {
                    KashTheme(dark) {
                        Box(Modifier.height(360.dp)) {
                            ServiceRail(
                                RailModel(
                                    services = listOf(whatsApp, slack), selection = Selection.Inbox, badges = mapOf(whatsApp.id to 3, slack.id to 5),
                                    inboxCount = 8, showBadges = true, showLabels = true, doNotDisturb = false,
                                    onSelect = {}, onAdd = {}, onToggleDoNotDisturb = {}, onSettings = {}, onMove = { _, _ -> },
                                    contextMenu = { _, _ -> }, quiet = setOf(slack.id),
                                ),
                                icons, RailSide.LEFT, RailMetrics.of(IconSize.MEDIUM),
                            )
                        }
                    }
                }
                onNodeWithText("5", useUnmergedTree = true).assertExists()
                // To look at: build/renders/rail-badges-light.png, rail-badges-dark.png (WhatsApp's 3 red, Slack's 5 gray).
                File("build/renders").mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File("build/renders/rail-badges-${if (dark) "dark" else "light"}.png"))
            }
        }
    }
}
