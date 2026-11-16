package app.kash.core

/** The order of the services, as the user changes it by dragging one past another (the rail, Settings › Services). */
public object ServiceOrder {
    /**
     * [ids] with [id] moved past [neighbor]: right after it when it was before it, right before it otherwise. The rail shows only the enabled
     * services, whose neighbors may not be next to each other in the whole order. Unchanged when either is missing, or when they are the same.
     */
    public fun <T> moveNextTo(ids: List<T>, id: T, neighbor: T): List<T> {
        val from = ids.indexOf(id)
        val to = ids.indexOf(neighbor)
        if (from < 0 || to < 0 || from == to) return ids
        val moved = ids.toMutableList()
        moved.removeAt(from)
        val target = moved.indexOf(neighbor)
        moved.add(if (from < to) target + 1 else target, id)
        return moved
    }
}
