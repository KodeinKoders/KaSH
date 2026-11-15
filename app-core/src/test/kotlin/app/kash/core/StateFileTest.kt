package app.kash.core

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StateFileTest {
    private val directory = Files.createTempDirectory("kash-state")

    // A plugin's service ([plugin] is its id), or a web application (null).
    private fun service(plugin: String?, name: String, icon: ServiceIcon = ServiceIcon.Favicon) =
        Service(ServiceId.random(), plugin, name = name, url = "https://example.com/", label = "Perso", profile = ProfileId.random(), icon = icon)

    @Test
    fun savedStateIsLoadedBack() {
        val file = StateFile(directory.resolve("state.json"))
        val whatsApp = service("slack", "Slack", ServiceIcon.Plugin).copy(options = mapOf("workspace" to "mon-equipe"))
        val custom = service(null, "Mon app", ServiceIcon.Square("#3058CF", "MA")).copy(muted = true, enabled = false)
        val image = service(null, "Autre", ServiceIcon.Image("icon.png"))
        val state = KashState(
            services = listOf(whatsApp, custom, image),
            selection = Selection.OfService(custom.id),
            settings = Settings(theme = ThemeMode.DARK, railSide = RailSide.TOP, doNotDisturb = true),
            deletedProfiles = listOf(ProfileId.random()),
        )
        file.save(state)
        assertEquals(state, file.load())
    }

    @Test
    fun theSortOfTheUnreadTabIsKept() {
        val file = StateFile(directory.resolve("state.json"))

        file.save(KashState(settings = Settings(inboxSort = InboxSort.BY_SERVICE)))

        assertEquals(InboxSort.BY_SERVICE, file.load().settings.inboxSort)
    }

    @Test
    fun noFileIsAnEmptyState() {
        assertEquals(KashState(), StateFile(directory.resolve("none.json")).load())
    }

    @Test
    fun anUnreadableFileIsAnEmptyState() {
        val path = directory.resolve("broken.json").also { it.writeText("{ not json") }
        assertEquals(KashState(), StateFile(path).load())
    }

    @Test
    fun closedProfilesAreDeleted() {
        val directories = KashDirectories(directory)
        val kept = ProfileId.random()
        val closed = ProfileId.random()
        directories.profile(kept).resolve("IndexedDB").createDirectories()
        directories.profile(closed).resolve("IndexedDB").createDirectories()
        val state = deleteClosedProfiles(KashState(deletedProfiles = listOf(closed)), directories)
        assertEquals(emptyList(), state.deletedProfiles)
        assertFalse(directories.profile(closed).exists())
        assertTrue(directories.profile(kept).exists())
    }
}
