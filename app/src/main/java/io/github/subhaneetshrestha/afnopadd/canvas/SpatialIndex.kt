package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.canvas.internal.RTree
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import kotlin.math.hypot

/** Finds elements by where their bounds are, without looking at every element. */
interface SpatialIndex {
    /** Elements whose bounds intersect [rect] (touching counts), in no particular order. */
    fun query(rect: WorldRect): List<ElementId>

    /** The element whose bounds are closest to [point], if any is within [maxDistance] world units. */
    fun nearest(point: WorldPoint, maxDistance: Double): ElementId?
}

/**
 * The [SpatialIndex] of one sheet: an R-tree over element bounds that follows the sheet snapshot by
 * snapshot. A snapshot made by one transaction from the last one indexed names its changed ids, so
 * only those are touched; any other snapshot is compared element by element (a reference check
 * each, since elements are immutable), and the first one is bulk-loaded. Thread-safe; the canvas
 * and the active tool share one instance.
 */
class ElementIndex : SpatialIndex {

    private val lock = Any()
    private val tree = RTree<ElementId>()
    private var synced: ElementSnapshot = ElementSnapshot.EMPTY

    /** Brings the index up to date with [snapshot]; cheap when it is the snapshot already indexed. */
    fun sync(snapshot: ElementSnapshot) = synchronized(lock) {
        if (snapshot === synced) return
        val before = synced.elements
        val after = snapshot.elements
        val changed = snapshot.changedSince(synced)
        if (changed != null) {
            for (id in changed) {
                val element = after[id]
                if (element == null) tree.remove(id) else if (before[id]?.bounds != element.bounds) tree.insert(id, element.bounds)
            }
        } else if (before.isEmpty()) {
            tree.load(after.map { (id, element) -> id to element.bounds })
        } else {
            for ((id, old) in before) {
                val new = after[id]
                when {
                    new == null -> tree.remove(id)
                    new !== old && new.bounds != old.bounds -> tree.insert(id, new.bounds)
                }
            }
            for ((id, element) in after) if (id !in before) tree.insert(id, element.bounds)
        }
        synced = snapshot
    }

    override fun query(rect: WorldRect): List<ElementId> = synchronized(lock) { tree.query(rect) }

    override fun nearest(point: WorldPoint, maxDistance: Double): ElementId? = synchronized(lock) {
        var best: ElementId? = null
        var bestDistance = Double.POSITIVE_INFINITY
        tree.query(WorldRect(point.x - maxDistance, point.y - maxDistance, point.x + maxDistance, point.y + maxDistance)) { id ->
            val bounds = synced.elements[id]?.bounds ?: return@query
            val distance = distanceToRect(point, bounds)
            if (distance <= maxDistance && distance < bestDistance) {
                best = id
                bestDistance = distance
            }
        }
        best
    }

    /**
     * The elements of [snapshot] whose bounds intersect [rect], back to front. When most of the sheet
     * is in view, filtering the already-sorted list beats sorting the hits, so that path is taken.
     */
    fun visible(snapshot: ElementSnapshot, rect: WorldRect): List<Element> {
        sync(snapshot)
        val hits = query(rect)
        if (hits.size * 4 > snapshot.elements.size) {
            return snapshot.ordered.filter { it.bounds.intersects(rect) }
        }
        return hits.mapNotNull { snapshot.elements[it] }.sortedBy { it.z }
    }

    private fun distanceToRect(point: WorldPoint, rect: WorldRect): Double {
        val dx = maxOf(rect.left - point.x, 0.0, point.x - rect.right)
        val dy = maxOf(rect.top - point.y, 0.0, point.y - rect.bottom)
        return hypot(dx, dy)
    }
}

internal fun WorldRect.intersects(other: WorldRect): Boolean =
    left <= other.right && other.left <= right && top <= other.bottom && other.top <= bottom
