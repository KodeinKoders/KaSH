package app.kash.ui

import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowMenuTest {
    // A screen of 1440 × 875 under a menu bar of 25.
    private val screen = Rectangle(0, 25, 1440, 875)

    @Test
    fun aMenuThatFitsStaysWhereItIs() {
        assertEquals(Point(100, 200), menuLocation(100, 200, 300, 400, 170, 16, 2, screen))
    }

    @Test
    fun aMenuPastTheRightEdgeIsMovedLeft() {
        assertEquals(Point(1140, 200), menuLocation(1300, 200, 300, 400, 170, 16, 2, screen))
        // Never past the left edge, even wider than the screen.
        assertEquals(Point(0, 200), menuLocation(100, 200, 2000, 400, 170, 16, 2, screen))
    }

    @Test
    fun aMenuPastTheBottomOpensAboveItsButton() {
        // Its button from 780 to 800: the menu ends 2 above it, its margin of 16 below.
        assertEquals(Point(100, 780 - 2 - 400 + 16), menuLocation(100, 786, 300, 400, 780, 16, 2, screen))
        // No room above either: against the bottom.
        assertEquals(Point(100, 900 - 860), menuLocation(100, 300, 300, 860, 200, 16, 2, screen))
    }
}
