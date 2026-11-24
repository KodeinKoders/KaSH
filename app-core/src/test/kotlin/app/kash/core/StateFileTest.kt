package app.kash.core

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
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
    fun theNotificationStyleIsKept() {
        val file = StateFile(directory.resolve("state.json"))

        file.save(KashState(settings = Settings(notificationStyle = NotificationStyle.KASH)))

        assertEquals(NotificationStyle.KASH, file.load().settings.notificationStyle)
    }

    @Test
    fun theDurationOfTheNotificationsIsKeptAndTemporaryBeforeIt() {
        val file = StateFile(directory.resolve("state.json"))
        file.save(KashState(settings = Settings(notificationDuration = NotificationDuration.PERMANENT)))
        assertEquals(NotificationDuration.PERMANENT, file.load().settings.notificationDuration)

        val old = directory.resolve("older.json").also { it.writeText("""{"settings": {"sound": false}}""") }
        assertEquals(NotificationDuration.TEMPORARY, StateFile(old).load().settings.notificationDuration)
    }

    @Test
    fun aStateOfBeforeTheNotificationStyleUsesTheSystem() {
        val path = directory.resolve("old.json").also { it.writeText("""{"settings": {"sound": false}}""") }

        val settings = StateFile(path).load().settings

        assertEquals(NotificationStyle.SYSTEM, settings.notificationStyle)
        assertEquals(false, settings.sound)
    }

    @Test
    fun noFileIsAnEmptyState() {
        assertEquals(KashState(), StateFile(directory.resolve("none.json")).load())
    }

    @Test
    fun anUnreadableFileIsSetAsideAndTheStateIsEmpty() {
        val path = directory.resolve("broken.json").also { it.writeText("{ not json") }
        val file = StateFile(path)
        assertEquals(KashState(), file.load())
        // Saving the empty state does not replace what could not be read: it is next to it, as it was.
        file.save(KashState())
        val aside = Files.list(directory).use { files -> files.filter { it.fileName.toString().startsWith("broken.json.unreadable-") }.toList() }
        assertEquals(1, aside.size)
        assertEquals("{ not json", aside.single().readText())
    }

    @Test
    fun aValueThatThisVersionDoesNotKnowIsTheDefaultAndTheServicesAreKept() {
        val path = directory.resolve("state.json")
        val file = StateFile(path)
        val services = listOf(service("whatsapp", "WhatsApp"))
        file.save(KashState(services = services))
        path.writeText(path.readText().replace("\"theme\": \"SYSTEM\"", "\"theme\": \"PURPLE\""))
        assertTrue("PURPLE" in path.readText())
        val state = file.load()
        assertEquals(services, state.services)
        assertEquals(ThemeMode.SYSTEM, state.settings.theme)
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
