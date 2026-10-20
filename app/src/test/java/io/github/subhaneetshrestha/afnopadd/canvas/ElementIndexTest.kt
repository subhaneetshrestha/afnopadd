package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.canvas.HitTestFixtures.idOf
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class ElementIndexTest {

    private val random = Random(7)
    private var nextZ: FractionalIndex? = null

    private fun randomStroke(id: Int): StrokeElement {
        val from = WorldPoint(random.nextDouble(-5_000.0, 5_000.0), random.nextDouble(-5_000.0, 5_000.0))
        val to = WorldPoint(from.x + random.nextDouble(-200.0, 200.0), from.y + random.nextDouble(-200.0, 200.0))
        // A moved element gets a fresh top-most z, so paint order drifts away from id order.
        val z = FractionalIndex.between(nextZ, null).also { nextZ = it }
        return HitTestFixtures.line(id, from, to).copy(z = z)
    }

    private fun bruteForce(snapshot: ElementSnapshot, rect: WorldRect): List<Element> = snapshot.ordered.filter {
        it.bounds.left <= rect.right && rect.left <= it.bounds.right && it.bounds.top <= rect.bottom && rect.top <= it.bounds.bottom
    }

    @Test
    fun `visible elements match a brute force pass in paint order as the snapshot changes`() {
        val index = ElementIndex()
        var elements: Map<ElementId, Element> = (0 until 5_000).associate { idOf(it) to randomStroke(it) }
        var previous: ElementSnapshot? = null
        var changed = emptySet<ElementId>()

        repeat(6) { round ->
            // Odd rounds arrive as the direct successor of the last snapshot (the per-transaction
            // path); even rounds as an unrelated snapshot (the full comparison).
            val snapshot = previous?.takeIf { round % 2 == 1 }?.next(elements, changed) ?: ElementSnapshot(elements)
            previous = snapshot
            index.sync(snapshot)
            repeat(20) {
                val x = random.nextDouble(-6_000.0, 6_000.0)
                val y = random.nextDouble(-6_000.0, 6_000.0)
                val size = if (it % 5 == 0) 20_000.0 else random.nextDouble(10.0, 1_500.0)
                val viewport = WorldRect(x, y, x + size, y + size * 0.6)
                assertEquals("round $round", bruteForce(snapshot, viewport).map { e -> e.id }, index.visible(snapshot, viewport).map { e -> e.id })
            }
            // Next round: move some, delete some, add some.
            val next = elements.toMutableMap()
            elements.keys.shuffled(random).take(300).forEachIndexed { i, id ->
                if (i % 2 == 0) next.remove(id) else next[id] = randomStroke(id.value.takeLast(4).toInt())
            }
            repeat(150) { next[idOf(5_000 + round * 150 + it)] = randomStroke(5_000 + round * 150 + it) }
            changed = (elements.keys + next.keys).filterTo(HashSet()) { elements[it] !== next[it] }
            elements = next
        }
    }

    @Test
    fun `nearest returns the closest element within the distance`() {
        val index = ElementIndex()
        val near = HitTestFixtures.line(1, WorldPoint(10.0, 0.0), WorldPoint(10.0, 50.0), widthWorld = 0.0)
        val far = HitTestFixtures.line(2, WorldPoint(40.0, 0.0), WorldPoint(40.0, 50.0), widthWorld = 0.0)
        index.sync(ElementSnapshot(mapOf(near.id to near, far.id to far)))

        assertEquals(near.id, index.nearest(WorldPoint(0.0, 25.0), maxDistance = 15.0))
        assertNull(index.nearest(WorldPoint(0.0, 25.0), maxDistance = 5.0))
        assertEquals(far.id, index.nearest(WorldPoint(35.0, 25.0), maxDistance = 15.0))
    }
}
