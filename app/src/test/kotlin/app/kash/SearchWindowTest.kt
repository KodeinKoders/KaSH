@file:OptIn(ExperimentalTestApi::class)

package app.kash

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import app.kash.core.ConversationKind
import app.kash.core.KashDirectories
import app.kash.core.PluginRegistry
import app.kash.core.ProfileId
import app.kash.core.SearchEntry
import app.kash.core.Service
import app.kash.core.ServiceId
import app.kash.ui.KashTheme
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchWindowTest {
    private val whatsApp = Service(ServiceId("w"), "whatsapp", name = "WhatsApp", url = "https://web.whatsapp.com/", profile = ProfileId.random())
    private val slack = Service(ServiceId("s"), "slack", name = "Slack", url = "https://acme.slack.com/", label = "Acme", profile = ProfileId.random())
    private val entries = listOf(
        SearchEntry(whatsApp, "laura", "Laura", ConversationKind.DIRECT),
        SearchEntry(whatsApp, "lucas", "Lucas", ConversationKind.DIRECT, count = 2),
        SearchEntry(slack, "C1", "général", ConversationKind.CHANNEL, topic = true),
    )
    private val icons = ServiceIcons(KashDirectories(Files.createTempDirectory("kash-search"))) { PluginRegistry(emptyList()) }

    @Test
    fun itListsTheContactsThenTheTopicsWithTheirServices() = runComposeUiTest {
        setContent { KashTheme(false) { SearchContent(entries, icons, onOpen = {}, onDismiss = {}) } }

        onNodeWithText("CONTACTS").assertIsDisplayed()
        onNodeWithText("TOPICS").assertIsDisplayed()
        onNodeWithContentDescription("Lucas WhatsApp").assertIsDisplayed()
        onNodeWithContentDescription("général Slack · Acme").assertIsDisplayed()
    }

    @Test
    fun typingFiltersAndEnterOpensTheSelectedLine() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent { KashTheme(false) { SearchContent(entries, icons, onOpen = { opened += it.conversation }, onDismiss = {}) } }

        val field = onNode(hasSetTextAction())
        field.performTextInput("gene")
        onNodeWithContentDescription("Laura WhatsApp").assertDoesNotExist()
        field.performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("C1"), opened)
    }

    @Test
    fun theArrowsMoveTheSelectionRoundTheListAndEscapeCloses() = runComposeUiTest {
        val opened = mutableListOf<String>()
        var dismissed = false
        setContent { KashTheme(false) { SearchContent(entries, icons, onOpen = { opened += it.conversation }, onDismiss = { dismissed = true }) } }

        val field = onNode(hasSetTextAction())
        // With no query, the unread contact is first: Lucas, then Laura, then the topic.
        field.performKeyInput { pressKey(Key.DirectionDown) }
        field.performKeyInput { pressKey(Key.Enter) }
        field.performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionUp) }
        field.performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("laura", "C1"), opened)
        field.performKeyInput { pressKey(Key.Escape) }
        assertEquals(true, dismissed)
    }

    @Test
    fun aClickOpensALine() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent { KashTheme(false) { SearchContent(entries, icons, onOpen = { opened += it.conversation }, onDismiss = {}) } }

        onNodeWithContentDescription("Laura WhatsApp").performClick()
        assertEquals(listOf("laura"), opened)
    }

    @Test
    fun withNothingToListItSaysSo() = runComposeUiTest {
        setContent { KashTheme(false) { SearchContent(emptyList(), icons, onOpen = {}, onDismiss = {}) } }
        onNodeWithText("The services have not shown any contact yet.").assertIsDisplayed()
    }

    @Test
    fun theMatchedLettersAreMarked() {
        val text: AnnotatedString = highlighted("Lucas", listOf(0, 2), Color.Red)
        assertEquals("Lucas", text.text)
        assertEquals(listOf(0 to 1, 2 to 3), text.spanStyles.map { it.start to it.end })
    }
}
