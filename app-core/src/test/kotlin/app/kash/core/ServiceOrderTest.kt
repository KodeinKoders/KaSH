package app.kash.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceOrderTest {
    private val ids = listOf("a", "b", "c", "d")

    @Test
    fun aServiceMovedDownGoesRightAfterItsNeighbor() {
        assertEquals(listOf("b", "a", "c", "d"), ServiceOrder.moveNextTo(ids, "a", "b"))
    }

    @Test
    fun aServiceMovedUpGoesRightBeforeItsNeighbor() {
        assertEquals(listOf("a", "c", "b", "d"), ServiceOrder.moveNextTo(ids, "c", "b"))
    }

    @Test
    fun aServiceGoesPastAHiddenOneWithItsNeighbor() {
        // b is disabled: the rail shows a, c, d, and a dragged past c goes after it, b staying where it was.
        assertEquals(listOf("b", "c", "a", "d"), ServiceOrder.moveNextTo(ids, "a", "c"))
        assertEquals(listOf("a", "d", "b", "c"), ServiceOrder.moveNextTo(ids, "d", "b"))
    }

    @Test
    fun aMissingServiceOrItselfChangesNothing() {
        assertEquals(ids, ServiceOrder.moveNextTo(ids, "x", "b"))
        assertEquals(ids, ServiceOrder.moveNextTo(ids, "a", "x"))
        assertEquals(ids, ServiceOrder.moveNextTo(ids, "b", "b"))
    }
}
