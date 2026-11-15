package app.kash.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class PluginManifestTest {
    // Slack's manifest, as KaSH has it, with a choice and a boolean more.
    private val slack = """
        {
          "${'$'}schema": "../manifest.schema.json",
          "manifestVersion": 1,
          "id": "slack",
          "version": "1.0.0",
          "name": "Slack",
          "description": {"en": "One workspace", "fr": "Un espace de travail"},
          "square": {"text": "Sl", "color": "#5B2C6F"},
          "url": "https://${'$'}{workspace}.slack.com/",
          "matches": ["https://app.slack.com/*"],
          "script": "plugin.js",
          "options": [
            {
              "id": "workspace", "type": "text", "label": {"en": "Workspace", "fr": "Espace de travail"},
              "required": true, "placeholder": {"en": "my-team", "fr": "mon-equipe"},
              "prefix": "https://", "suffix": ".slack.com", "lowercase": true,
              "pattern": "[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*",
              "patternError": {"en": "Letters, digits and hyphens", "fr": "Des lettres, des chiffres et des tirets"}
            },
            {"id": "region", "type": "choice", "label": "Region", "choices": [{"value": "eu", "label": "Europe"}, {"value": "us", "label": "United States"}]},
            {"id": "threads", "type": "boolean", "label": "Threads", "default": true}
          ]
        }
    """.trimIndent()

    private fun refused(path: String, json: String) {
        val error = assertFailsWith<PluginManifestException> { PluginManifests.parse(json) }
        assertEquals(path, error.path, error.message)
    }

    private fun slackWith(old: String, new: String): String {
        check(old in slack) { old }
        return slack.replace(old, new)
    }

    @Test
    fun aManifestIsReadWithItsOptions() {
        val manifest = PluginManifests.parse(slack)

        assertEquals("slack", manifest.id)
        assertEquals("Slack", manifest.name.of("fr"))
        assertEquals(PluginSquare("Sl", "#5B2C6F"), manifest.square)
        val workspace = assertIs<PluginOption.Text>(manifest.options[0])
        assertEquals("Espace de travail", workspace.label.of("fr"))
        assertEquals(".slack.com", workspace.suffix)
        assertEquals(listOf("eu", "us"), assertIs<PluginOption.Choice>(manifest.options[1]).choices.map { it.value })
        assertEquals(true, assertIs<PluginOption.Flag>(manifest.options[2]).default)
    }

    @Test
    fun aLocalizedTextIsInTheLanguageElseInEnglishElseTheFirstOne() {
        val text = LocalizedText(mapOf("de" to "Arbeitsbereich", "en" to "Workspace", "fr" to "Espace de travail"))
        assertEquals("Espace de travail", text.of("fr"))
        assertEquals("Workspace", text.of("es"))
        assertEquals("Arbeitsbereich", LocalizedText(mapOf("de" to "Arbeitsbereich")).of("fr"))
    }

    @Test
    fun theFormatTheIdAndTheVersionAreChecked() {
        refused("manifestVersion", slackWith("\"manifestVersion\": 1", "\"manifestVersion\": 2"))
        refused("id", slackWith("\"id\": \"slack\"", "\"id\": \"Slack App\""))
        refused("version", slackWith("\"version\": \"1.0.0\"", "\"version\": \" \""))
        refused("name", slackWith("\"name\": \"Slack\"", "\"name\": {}"))
        refused("name.en", slackWith("\"name\": \"Slack\"", "\"name\": \"\""))
    }

    @Test
    fun theSquareTheMatchesAndTheScriptAreChecked() {
        refused("square.text", slackWith("\"text\": \"Sl\"", "\"text\": \"Slac\""))
        refused("square.color", slackWith("\"#5B2C6F\"", "\"purple\""))
        refused("matches", slackWith("[\"https://app.slack.com/*\"]", "[]"))
        refused("script", slackWith("\"plugin.js\"", "\"../plugin.js\""))
        refused("script", slackWith("\"plugin.js\"", "\"plugin.ts\""))
    }

    @Test
    fun theOptionsAreChecked() {
        refused("options[1].id", slackWith("\"id\": \"region\"", "\"id\": \"workspace\""))
        refused("options[1].id", slackWith("\"id\": \"region\"", "\"id\": \"2region\""))
        refused("options[0].pattern", slackWith("\"pattern\": \"[a-z0-9]", "\"pattern\": \"(["))
        refused("options[1].choices", slackWith("[{\"value\": \"eu\", \"label\": \"Europe\"}, {\"value\": \"us\", \"label\": \"United States\"}]", "[]"))
        refused("options[1].choices[1].value", slackWith("{\"value\": \"us\"", "{\"value\": \"eu\""))
        refused("options[1].default", slackWith("\"label\": \"Region\",", "\"label\": \"Region\", \"default\": \"asia\","))
    }

    @Test
    fun theUrlOnlyUsesOptionsThatAlwaysHaveAValue() {
        refused("url", slackWith("\${workspace}", "\${team}"))
        refused("url", slackWith("\${workspace}", "\${threads}"))
        refused("url", slackWith("\"required\": true, ", ""))
        refused("url", slackWith("\"https://\${workspace}.slack.com/\"", "\"slack://\${workspace}\""))
        // A choice always has a value.
        PluginManifests.parse(slackWith("\"https://\${workspace}.slack.com/\"", "\"https://\${workspace}.\${region}.slack.com/\""))
    }

    @Test
    fun aFieldThatKashDoesNotKnowIsAMistake() {
        refused("", slackWith("\"script\": \"plugin.js\",", "\"script\": \"plugin.js\", \"scirpt\": \"x.js\","))
        refused("", slackWith("\"type\": \"boolean\"", "\"type\": \"date\""))
    }
}
