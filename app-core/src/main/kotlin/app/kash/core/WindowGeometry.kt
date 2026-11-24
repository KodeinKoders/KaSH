package app.kash.core

import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.nio.file.Path

private val logger = LoggerFactory.getLogger(WindowFile::class.java)

/** How the window is shown. A window that is minimized is not kept: it would open where it cannot be seen. */
@Serializable
public enum class WindowMode { FLOATING, MAXIMIZED, FULLSCREEN }

/**
 * Where the main window is, how big it is and in which mode, as it was when KaSH was closed (as KaTTY keeps its own). Sizes and positions are in
 * dp, as the window's own state has them.
 *
 * [x], [y], [width] and [height] are those of the **floating** window: when it is maximized or full screen, they are what it goes back to when it
 * is restored. [x] and [y] are `null` for the platform's own place.
 */
@Serializable
public data class WindowGeometry(
    val version: Int = VERSION,
    val x: Float? = null,
    val y: Float? = null,
    val width: Float = DEFAULT_WIDTH,
    val height: Float = DEFAULT_HEIGHT,
    val mode: WindowMode = WindowMode.FLOATING,
) {
    /**
     * This geometry, corrected so that the window can be used: not smaller than [MIN_WIDTH] by [MIN_HEIGHT], and, when its place is not on any
     * of [screens] (one that was unplugged, a resolution that changed), at the platform's place.
     */
    public fun sanitized(screens: List<ScreenBounds>): WindowGeometry {
        val w = width.coerceAtLeast(MIN_WIDTH)
        val h = height.coerceAtLeast(MIN_HEIGHT)
        val left = x
        val top = y
        val reachable = left != null && top != null && screens.any { it.visibleBy(left, top, w, h) }
        return copy(x = if (reachable) left else null, y = if (reachable) top else null, width = w, height = h)
    }

    public companion object {
        public const val VERSION: Int = 1
        public const val DEFAULT_WIDTH: Float = 1360f
        public const val DEFAULT_HEIGHT: Float = 860f
        public const val MIN_WIDTH: Float = 480f
        public const val MIN_HEIGHT: Float = 320f

        /** How much of a window must be on a screen for it to be reachable: enough of its title bar to grab it. */
        internal const val VISIBLE_WIDTH: Float = 120f
        internal const val VISIBLE_HEIGHT: Float = 60f
    }
}

/** A screen, in the same units as the window. */
public data class ScreenBounds(val x: Float, val y: Float, val width: Float, val height: Float) {
    internal fun visibleBy(left: Float, top: Float, windowWidth: Float, windowHeight: Float): Boolean {
        val overlapWidth = minOf(left + windowWidth, x + width) - maxOf(left, x)
        val overlapHeight = minOf(top + windowHeight, y + height) - maxOf(top, y)
        return overlapWidth >= WindowGeometry.VISIBLE_WIDTH && overlapHeight >= WindowGeometry.VISIBLE_HEIGHT
    }
}

/**
 * The file that keeps the window's geometry, apart from the state of KaSH ([StateFile]): it changes whenever the window is moved, and what is lost
 * with it is nothing worth keeping aside. A file that is missing, unreadable or of a later version means the default window.
 */
public class WindowFile(path: Path) {
    // Its version is checked here (a later one is the default window), and an unreadable one is not kept aside.
    private val file = JsonFile(path, WindowGeometry.serializer(), ::WindowGeometry, "the window opens at the default place", version = null, keepUnreadable = false)

    /** What is in the file, or the default geometry. */
    public fun load(): WindowGeometry =
        file.load().takeIf { it.version <= WindowGeometry.VERSION } ?: WindowGeometry().also { logger.warn("The window's file is of a later version") }

    /** Writes [geometry] durably, so that a crash does not leave half of it; one write at a time. */
    public fun save(geometry: WindowGeometry) {
        file.save(geometry)
    }
}
