package app.kash.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt

/** A list whose elements are dragged to change their order (see [reorderable]): the one being dragged, how far, and their sizes. */
@Stable
class ReorderState<T> {
    /** The element being dragged, null when none is. */
    var dragged: T? by mutableStateOf(null)
        private set

    // How far the dragged element is from its place, in pixels along the list.
    private var offset by mutableFloatStateOf(0f)

    // The size of each element along the list, in pixels: how far the dragged one goes to take its neighbor's place.
    private val sizes = mutableMapOf<T, Int>()

    internal fun start(id: T) {
        dragged = id
        offset = 0f
    }

    internal fun end() {
        dragged = null
        offset = 0f
    }

    internal fun offsetOf(id: T): Float = if (dragged == id) offset else 0f

    internal fun measured(id: T, size: Int) {
        sizes[id] = size
    }

    /**
     * Moves the dragged element by [amount]: past half of its neighbor, it takes the neighbor's place ([onMove]), and its offset is from there.
     */
    internal fun drag(id: T, amount: Float, ids: List<T>, spacing: Float, onMove: (T, T) -> Unit) {
        offset += amount
        val position = ids.indexOf(id)
        if (position < 0 || offset == 0f) return
        val neighbor = ids.getOrNull(if (offset > 0) position + 1 else position - 1) ?: return
        val step = (sizes[neighbor] ?: return) + spacing
        if (abs(offset) > step / 2) {
            onMove(id, neighbor)
            offset -= if (offset > 0) step else -step
        }
    }
}

/**
 * Makes the element [id] of a list ([ids], in their order) dragged along it ([vertical] or not, [spacing] between its elements) to change its
 * place: it follows the pointer, over the others, and takes its neighbor's place past half of it ([onMove] with its neighbor's id). A click is
 * still a click: the drag starts once the pointer moved a little.
 *
 * **Each element has to be in a `key` of its id** (`key(id) { … }`): otherwise, once it takes another's place, it is another element of the
 * composition, whose gesture is not the one going on, and the drag stops at its first move.
 */
@Composable
fun <T> Modifier.reorderable(state: ReorderState<T>, id: T, vertical: Boolean, spacing: Dp, ids: List<T>, onMove: (T, T) -> Unit): Modifier {
    // The gesture outlives the composition it started in: it reads the order and the callback as they are now.
    val currentIds by rememberUpdatedState(ids)
    val currentOnMove by rememberUpdatedState(onMove)
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    return onSizeChanged { state.measured(id, if (vertical) it.height else it.width) }
        .zIndex(if (state.dragged == id) 1f else 0f)
        .offset {
            val offset = state.offsetOf(id).roundToInt()
            if (vertical) IntOffset(0, offset) else IntOffset(offset, 0)
        }
        .pointerInput(id, vertical) {
            detectDragGestures(
                onDragStart = { state.start(id) },
                onDragEnd = { state.end() },
                onDragCancel = { state.end() },
                onDrag = { change, amount ->
                    change.consume()
                    state.drag(id, if (vertical) amount.y else amount.x, currentIds, spacingPx, currentOnMove)
                },
            )
        }
}
