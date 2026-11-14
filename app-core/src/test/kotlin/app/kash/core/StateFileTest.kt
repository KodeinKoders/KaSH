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

    private fun service(kind: ServiceKind, name: String, icon: ServiceIcon = ServiceIcon.Favicon) =
        Service(ServiceId.random(), kind, name, "https://example.com/", label = "Perso", profile = ProfileId.random(), icon = icon)

    @Test
    fun savedStateIsLoadedBack() {
        val file = StateFile(directory.resolve("state.json"))
        val whatsApp = service(ServiceKind.WHATSAPP, "WhatsApp")
        val custom = service(ServiceKind.CUSTOM, "Mon app", ServiceIcon.Square("#3058CF", "MA")).copy(muted = true, enabled = false)
        val image = service(ServiceKind.CUSTOM, "Autre", ServiceIcon.Image("icon.png"))
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
    fun noFileIsAnEmptyState() {
        assertEquals(KashState(), StateFile(directory.resolve("none.json")).load())
    }

    @Test
    fun anUnreadableFileIsAnEmptyState() {
        val path = directory.resolve("broken.json").also { it.writeText("{ not json") }
        assertEquals(KashState(), StateFile(path).load())
    }

    @Test
    fun theGenericBrowsersEnvironmentsAreDeleted() {
        val kept = "env-51b48c24-d242-4145-b93f-79b2756fc5b1"
        val deleted = "env-aaaaaaaa-0000-0000-0000-000000000000"
        val path = directory.resolve("old.json").also {
            it.writeText("""{"environments":[{"id":"$kept","name":"Environment 1","url":"https://www.google.com/"}],"selected":"$kept","deleted":["$deleted"]}""")
        }
        assertEquals(KashState(deletedProfiles = listOf(ProfileId(kept), ProfileId(deleted))), StateFile(path).load())
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
