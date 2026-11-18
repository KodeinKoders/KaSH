package app.kash.core

import kotlin.math.abs
import kotlin.math.ln

/** The zoom of the services' pages, in percent, as Chrome's: its steps, and Chromium's level for each. */
public object Zoom {
    /** Chrome's steps, from 25 % to 500 %, which ⌘+ and ⌘− go through. */
    public val LEVELS: List<Int> = listOf(25, 33, 50, 67, 75, 80, 90, 100, 110, 125, 150, 175, 200, 250, 300, 400, 500)

    public const val DEFAULT: Int = 100

    /** The step that is nearest to [percent]: a file edited by hand cannot make a page unreadable. */
    public fun nearest(percent: Int): Int = LEVELS.minBy { abs(it - percent) }

    /** The step after [percent], or the last one. */
    public fun zoomIn(percent: Int): Int = LEVELS.firstOrNull { it > nearest(percent) } ?: LEVELS.last()

    /** The step before [percent], or the first one. */
    public fun zoomOut(percent: Int): Int = LEVELS.lastOrNull { it < nearest(percent) } ?: LEVELS.first()

    /** Chromium's zoom level of [percent]: each level is 20 % more than the one before (0 is 100 %). */
    public fun chromiumLevel(percent: Int): Double = ln(percent / 100.0) / ln(1.2)

    /** The zoom of [service]'s page: its own (⌘+, ⌘−, its menu), else the default of [settings]. */
    public fun of(service: Service, settings: Settings): Int = nearest(service.zoom ?: settings.defaultZoom)
}

/** How big the text of KaSH's own windows is, in percent of its usual size (not the pages': they have their [Zoom]), as KaTTY's. */
public object UiScales {
    /** The sizes that Settings offers. */
    public val LEVELS: List<Int> = listOf(80, 90, 100, 110, 125, 150, 175)

    public const val DEFAULT: Int = 100

    /** The size that is nearest to [percent]: a file edited by hand cannot make the text unreadable. */
    public fun nearest(percent: Int): Int = LEVELS.minBy { abs(it - percent) }
}
