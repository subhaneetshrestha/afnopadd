package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.canvas.internal.RTree
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RTreeTest {

    private val random = Random(42)

    private fun randomRect(): WorldRect {
        val x = random.nextDouble(-1e6, 1e6)
        val y = random.nextDouble(-1e6, 1e6)
        // Mix of tiny, medium and huge rectangles, as on a deep-zoom sheet.
        val size = when (random.nextInt(3)) {
            0 -> random.nextDouble(1e-4, 1.0)
            1 -> random.nextDouble(1.0, 1e3)
            else -> random.nextDouble(1e3, 2e5)
        }
        return WorldRect(x, y, x + size * random.nextDouble(0.1, 1.0), y + size * random.nextDouble(0.1, 1.0))
    }

    private fun intersects(a: WorldRect, b: WorldRect) =
        a.left <= b.right && b.left <= a.right && a.top <= b.bottom && b.top <= a.bottom

    private fun assertMatchesBruteForce(tree: RTree<Int>, rects: Map<Int, WorldRect>, queries: Int) {
        repeat(queries) {
            val query = randomRect()
            val expected = rects.filterValues { intersects(it, query) }.keys
            assertEquals(expected, tree.query(query).toSet())
        }
    }

    @Test
    fun `queries match brute force on 50,000 random rectangles`() {
        val tree = RTree<Int>()
        val rects = HashMap<Int, WorldRect>()
        repeat(50_000) { key ->
            val rect = randomRect()
            rects[key] = rect
            tree.insert(key, rect)
        }

        assertEquals(50_000, tree.size)
        assertMatchesBruteForce(tree, rects, queries = 300)
    }

    @Test
    fun `queries still match after moves and deletes`() {
        val tree = RTree<Int>()
        val rects = HashMap<Int, WorldRect>()
        repeat(50_000) { key ->
            val rect = randomRect()
            rects[key] = rect
            tree.insert(key, rect)
        }

        repeat(20_000) {
            val key = random.nextInt(50_000)
            if (random.nextBoolean()) {
                val moved = randomRect()
                rects[key] = moved
                tree.insert(key, moved)
            } else {
                rects.remove(key)
                tree.remove(key)
            }
        }

        assertEquals(rects.size, tree.size)
        assertMatchesBruteForce(tree, rects, queries = 300)
        for ((key, rect) in rects.entries.take(100)) assertEquals(rect, tree.bounds(key))
    }

    @Test
    fun `a bulk-loaded tree answers like an incrementally built one, also after edits`() {
        val rects = HashMap<Int, WorldRect>()
        repeat(50_000) { rects[it] = randomRect() }
        val tree = RTree<Int>()
        tree.load(rects.map { it.key to it.value })

        assertEquals(50_000, tree.size)
        assertMatchesBruteForce(tree, rects, queries = 200)

        repeat(20_000) {
            val key = random.nextInt(60_000)
            if (random.nextBoolean()) {
                val moved = randomRect()
                rects[key] = moved
                tree.insert(key, moved)
            } else {
                rects.remove(key)
                tree.remove(key)
            }
        }
        assertEquals(rects.size, tree.size)
        assertMatchesBruteForce(tree, rects, queries = 200)
    }

    @Test
    fun `bulk loads of every small size work`() {
        for (n in 0..40) {
            val rects = (0 until n).associateWith { randomRect() }
            val tree = RTree<Int>()
            tree.load(rects.map { it.key to it.value })
            assertMatchesBruteForce(tree, rects, queries = 5)
            rects.keys.forEach { assertTrue(tree.remove(it)) }
            assertEquals(0, tree.size)
        }
    }

    @Test
    fun `removing every entry leaves an empty tree that accepts new entries`() {
        val tree = RTree<Int>()
        val rects = (0 until 1_000).associateWith { randomRect() }
        rects.forEach { (key, rect) -> tree.insert(key, rect) }

        rects.keys.forEach { assertTrue(tree.remove(it)) }
        assertFalse(tree.remove(0))
        assertEquals(0, tree.size)
        assertTrue(tree.query(WorldRect(-1e7, -1e7, 1e7, 1e7)).isEmpty())

        tree.insert(7, WorldRect(0.0, 0.0, 1.0, 1.0))
        assertEquals(listOf(7), tree.query(WorldRect(0.5, 0.5, 0.6, 0.6)))
    }

    @Test
    fun `touching edges count as intersecting`() {
        val tree = RTree<String>()
        tree.insert("a", WorldRect(0.0, 0.0, 10.0, 10.0))

        assertEquals(listOf("a"), tree.query(WorldRect(10.0, 10.0, 20.0, 20.0)))
        assertTrue(tree.query(WorldRect(10.000001, 0.0, 20.0, 20.0)).isEmpty())
    }
}
