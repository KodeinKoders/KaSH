package app.kash.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZoomTest {
    @Test
    fun theZoomGoesThroughChromesSteps() {
        assertEquals(110, Zoom.zoomIn(100))
        assertEquals(90, Zoom.zoomOut(100))
        assertEquals(500, Zoom.zoomIn(500))
        assertEquals(25, Zoom.zoomOut(25))
        // A value that is not a step is taken as the nearest one.
        assertEquals(125, Zoom.nearest(120))
        assertEquals(150, Zoom.zoomIn(120))
    }

    @Test
    fun chromiumsLevelIsTwentyPercentAStep() {
        assertEquals(0.0, Zoom.chromiumLevel(100))
        assertTrue(kotlin.math.abs(Zoom.chromiumLevel(120) - 1.0) < 1e-9)
        assertTrue(Zoom.chromiumLevel(50) < 0)
    }

    @Test
    fun aServicesZoomIsItsOwnElseTheDefault() {
        val service = Service(ServiceId("s"), "slack", name = "Slack", url = "https://acme.slack.com/", profile = ProfileId.random())
        assertEquals(100, Zoom.of(service, Settings()))
        assertEquals(125, Zoom.of(service, Settings(defaultZoom = 125)))
        assertEquals(80, Zoom.of(service.copy(zoom = 80), Settings(defaultZoom = 125)))
    }

    @Test
    fun theTextsSizeIsOneOfSettings() {
        assertEquals(175, UiScales.nearest(400))
        assertEquals(100, UiScales.nearest(103))
    }

    @Test
    fun theZoomsAndTheTextsSizeAreKept() {
        val directory = java.nio.file.Files.createTempDirectory("kash-zoom")
        val file = StateFile(directory.resolve("state.json"))
        val service = Service(ServiceId("s"), "slack", name = "Slack", url = "https://acme.slack.com/", profile = ProfileId.random(), zoom = 125)
        val state = KashState(services = listOf(service), settings = Settings(uiScale = 125, defaultZoom = 90))
        file.save(state)
        assertEquals(state.settings, file.load().settings)
        assertEquals(125, file.load().services.single().zoom)
        directory.toFile().deleteRecursively()
    }
}
