package app.kash.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginOptionsTest {
    private val workspace = PluginOption.Text(
        id = "workspace", label = LocalizedText(mapOf("en" to "Workspace")), required = true,
        prefix = "https://", suffix = ".slack.com", lowercase = true,
        pattern = "[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*",
    )
    private val region = PluginOption.Choice(
        id = "region", label = LocalizedText(mapOf("en" to "Region")),
        choices = listOf(PluginChoice("eu", LocalizedText(mapOf("en" to "Europe"))), PluginChoice("us", LocalizedText(mapOf("en" to "US")))),
    )
    private val threads = PluginOption.Flag(id = "threads", label = LocalizedText(mapOf("en" to "Threads")))
    private val manifest = PluginManifest(
        manifestVersion = 1, id = "slack", version = "1", versionCode = 1, name = LocalizedText(mapOf("en" to "Slack")),
        square = PluginSquare("Sl", "#5B2C6F"), url = "https://\${workspace}.slack.com/", matches = listOf("https://app.slack.com/*"),
        script = "plugin.js", options = listOf(workspace, region, threads),
    )

    @Test
    fun whatIsTypedIsNormalized() {
        assertEquals("mon-equipe", PluginOptions.normalize(workspace, "mon-equipe"))
        assertEquals("mon-equipe", PluginOptions.normalize(workspace, "  Mon-Equipe "))
        // A pasted address: its prefix, whatever its case, and everything from its suffix on.
        assertEquals("mon-equipe", PluginOptions.normalize(workspace, "HTTPS://Mon-Equipe.slack.com/client/T0"))
        assertEquals("mon-equipe", PluginOptions.normalize(workspace, "mon-equipe.slack.com"))
        // An Enterprise Grid organization.
        assertEquals("acme.enterprise", PluginOptions.normalize(workspace, "acme.enterprise.slack.com"))
    }

    @Test
    fun aValueIsCheckedAgainstTheOption() {
        assertNull(PluginOptions.error(workspace, "mon-equipe"))
        assertEquals(OptionError.MISSING, PluginOptions.error(workspace, ""))
        assertEquals(OptionError.PATTERN, PluginOptions.error(workspace, "mon équipe"))
        assertEquals(OptionError.PATTERN, PluginOptions.error(workspace, "-equipe"))
        assertEquals(OptionError.PATTERN, PluginOptions.error(workspace, "mon_equipe"))
        assertEquals(OptionError.TOO_LONG, PluginOptions.error(workspace.copy(maxLength = 3), "equipe"))
        // An optional option may be empty.
        assertNull(PluginOptions.error(workspace.copy(required = false), ""))
    }

    @Test
    fun aPatternThatBacktracksWithoutEndIsAValueThatDoesNotMatch() {
        val catastrophic = workspace.copy(pattern = "(a+)+\$")
        val started = System.nanoTime()
        assertEquals(OptionError.PATTERN, PluginOptions.error(catastrophic, "a".repeat(40) + "b"))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000)
        assertNull(PluginOptions.error(catastrophic, "aaaa"))
    }

    @Test
    fun theOptionsWithoutAValueHaveTheirDefault() {
        assertEquals(mapOf("workspace" to "", "region" to "eu", "threads" to "false"), PluginOptions.values(manifest))
        assertEquals("us", PluginOptions.values(manifest, mapOf("region" to "us"))["region"])
    }

    @Test
    fun theUrlIsTheTemplateWithTheValues() {
        val values = PluginOptions.values(manifest, mapOf("workspace" to "mon-equipe"))
        assertTrue(PluginOptions.isValid(manifest, values))
        assertEquals("https://mon-equipe.slack.com/", PluginOptions.resolveUrl(manifest, values))
        // A value that is not accepted, or a choice that is not one, makes no URL.
        assertNull(PluginOptions.resolveUrl(manifest, PluginOptions.values(manifest)))
        assertFalse(PluginOptions.isValid(manifest, values + ("region" to "asia")))
    }

    @Test
    fun anEmptyOptionalOptionIsItsDefaultInTheUrl() {
        val zone = PluginOption.Text(id = "zone", label = LocalizedText(mapOf("en" to "Zone")), default = "eu")
        val regional = manifest.copy(url = "https://\${zone}.example.com/", options = listOf(zone))
        assertEquals("https://eu.example.com/", PluginOptions.resolveUrl(regional, mapOf("zone" to "")))
        assertEquals("https://fr.example.com/", PluginOptions.resolveUrl(regional, mapOf("zone" to "fr")))
        // What is saved and what the script sees stay as the user left them.
        assertEquals("", PluginOptions.values(regional, mapOf("zone" to ""))["zone"])
    }

    @Test
    fun theValuesAreEncodedInTheUrl() {
        assertEquals("https://example.com/a%20b%2Fc%C3%A9", PluginOptions.url("https://example.com/\${path}", mapOf("path" to "a b/cé")))
    }

    @Test
    fun theScriptSeesTheBooleansAsBooleans() {
        val values = PluginOptions.forScript(manifest, mapOf("workspace" to "mon-equipe", "region" to "eu", "threads" to "true"))
        assertEquals("""{"workspace":"mon-equipe","region":"eu","threads":true}""", values.toString())
    }

    @Test
    fun aServiceTakesItsNameAndItsUrlFromItsPlugin() {
        val registry = PluginRegistry(listOf(Plugin(manifest.copy(name = LocalizedText(mapOf("en" to "Slack", "fr" to "Slack FR"))), "")))
        val service = Service(ServiceId.random(), "slack", mapOf("workspace" to "mon-equipe"), name = "", url = "", profile = ProfileId.random())

        val refreshed = registry.refresh(service, "fr")

        assertEquals("Slack FR", refreshed.name)
        assertEquals("https://mon-equipe.slack.com/", refreshed.url)
        assertEquals(mapOf("workspace" to "mon-equipe", "region" to "eu", "threads" to "false"), refreshed.options)
        assertEquals(PluginSquare("Sl", "#5B2C6F"), registry.square(service))
        // A web application has its own name and address, and a square of its initials.
        val web = Service(ServiceId.random(), null, name = "Mon application", url = "https://example.com/", profile = ProfileId.random())
        assertEquals(web, registry.refresh(web, "fr"))
        assertEquals(PluginSquare("MA", WebApplications.COLOR), registry.square(web))
    }
}
