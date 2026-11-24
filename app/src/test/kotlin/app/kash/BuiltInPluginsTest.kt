package app.kash

import app.kash.core.PluginManifest
import app.kash.core.PluginOption
import app.kash.core.PluginOptions
import app.kash.core.PluginScriptCheck
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.skia.Image as SkiaImage

class BuiltInPluginsTest {
    // The resources are a directory when the tests run: what is in it can be listed.
    private val root = File(assertNotNull(BuiltInPlugins::class.java.getResource(BuiltInPlugins.ROOT)).toURI())

    @Test
    fun theIndexListsEveryPluginOfTheResourcesAndNothingElse() {
        val folders = root.listFiles { file -> file.isDirectory }!!.map { it.name }.sorted()
        assertEquals(folders, BuiltInPlugins.ids().sorted())
        assertEquals(BuiltInPlugins.ids().distinct(), BuiltInPlugins.ids())
    }

    @Test
    fun everyBuiltInPluginIsValidAndHasItsScript() {
        val registry = BuiltInPlugins.registry()
        assertEquals(BuiltInPlugins.ids(), registry.all.map { it.id })
        for (plugin in registry.all) {
            assertTrue(plugin.script.isNotBlank(), plugin.id)
            // Its script is the body of its function, which it does not close.
            assertNull(PluginScriptCheck.check(plugin.script), plugin.id)
            // Its icon is an image that KaSH can draw.
            plugin.icon?.let { icon -> assertTrue(runCatching { SkiaImage.makeFromEncoded(icon).width > 0 }.getOrDefault(false), "The icon of ${plugin.id}") }
            // Its URL is made with values that its options accept.
            val values = PluginOptions.values(plugin.manifest, plugin.manifest.options.filterIsInstance<PluginOption.Text>().associate { it.id to "sample" })
            assertNotNull(PluginOptions.resolveUrl(plugin.manifest, values), plugin.id)
        }
    }

    @Test
    fun slacksWorkspaceIsAnOptionOfItsUrl() {
        val slack = assertNotNull(BuiltInPlugins.registry()["slack"]).manifest
        val values = PluginOptions.values(slack, mapOf("workspace" to PluginOptions.normalize(slack.options[0] as PluginOption.Text, "https://Mon-Equipe.slack.com/")))
        assertEquals("https://mon-equipe.slack.com/", PluginOptions.resolveUrl(slack, values))
    }

    @Test
    fun theSchemaHasTheFieldsThatKashReads() {
        val schema = Json.parseToJsonElement(File(root, "manifest.schema.json").readText()).jsonObject
        val definitions = schema.getValue("\$defs").jsonObject

        assertEquals(names(PluginManifest.serializer().descriptor), properties(schema))
        // The option's type is its discriminator, which the descriptors of the options do not have.
        assertEquals(names(PluginOption.Text.serializer().descriptor) + "type", properties(definitions.getValue("textOption").jsonObject))
        assertEquals(names(PluginOption.Choice.serializer().descriptor) + "type", properties(definitions.getValue("choiceOption").jsonObject))
        assertEquals(names(PluginOption.Flag.serializer().descriptor) + "type", properties(definitions.getValue("booleanOption").jsonObject))
    }

    private fun names(descriptor: SerialDescriptor): Set<String> = (0 until descriptor.elementsCount).map(descriptor::getElementName).toSet()

    private fun properties(definition: JsonObject): Set<String> = definition.getValue("properties").jsonObject.keys
}
