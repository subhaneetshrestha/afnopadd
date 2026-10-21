package io.github.subhaneetshrestha.afnopadd.canvas.tiles

import io.github.subhaneetshrestha.afnopadd.canvas.internal.tiles.TileGrid
import io.github.subhaneetshrestha.afnopadd.canvas.internal.tiles.TileKey
import io.github.subhaneetshrestha.afnopadd.canvas.internal.tiles.TileStore
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileIndexTest {

    @Test
    fun `the tile level is the power of two at or above the zoom, so a tile is only ever shrunk and stays sharp`() {
        assertEquals(0, TileGrid.levelFor(1.0))
        assertEquals(1, TileGrid.levelFor(1.01))
        assertEquals(1, TileGrid.levelFor(2.0))
        assertEquals(-4, TileGrid.levelFor(1.0 / 16))
        assertEquals(-4, TileGrid.levelFor(0.054))
        for (zoom in generateSequence(0.001) { it * 1.17 }.takeWhile { it < 1_000.0 }) {
            val stretch = zoom / TileGrid.scale(TileGrid.levelFor(zoom))
            assertTrue("zoom $zoom stretches $stretch", stretch > 0.5 - 1e-9 && stretch <= 1.0 + 1e-9)
        }
    }

    @Test
    fun `tiles cover a world rectangle edge to edge`() {
        // Level -2: 4 world units per pixel, so a 256 px tile is 1,024 world units.
        val keys = TileGrid.keysCovering(WorldRect(-10.0, 0.0, 2_000.0, 1_100.0), level = -2)

        assertEquals(setOf(-1L, 0L, 1L), keys.map { it.x }.toSet())
        assertEquals(setOf(0L, 1L), keys.map { it.y }.toSet())
        assertEquals(WorldRect(1_024.0, 1_024.0, 2_048.0, 2_048.0), TileGrid.bounds(TileKey(-2, 1, 1)))
    }

    @Test
    fun `tiles are listed nearest the centre first, so the middle of the screen fills in first`() {
        val keys = TileGrid.keysCovering(WorldRect(0.0, 0.0, 5 * 256.0, 5 * 256.0), level = 0)

        assertEquals(TileKey(0, 2, 2), keys.first())
    }

    private class FakeTile(val name: String, var released: Boolean = false)

    private fun store(budget: Long = 4 * TILE_BYTES) = TileStore<FakeTile>(budgetBytes = budget, bytesOf = { TILE_BYTES }, release = { it.released = true })

    @Test
    fun `a transaction invalidates only the tiles overlapping its dirty rectangle, at every level`() {
        val store = store(budget = 100 * TILE_BYTES)
        val near = TileKey(0, 0, 0)
        val far = TileKey(0, 10, 10)
        val coarse = TileKey(-3, 0, 0) // 2,048 world units across, so it also covers the dirty spot
        for (key in listOf(near, far, coarse)) store.put(key, store.request(key)!!, FakeTile("$key"))

        val dropped = store.invalidate(WorldRect(10.0, 10.0, 20.0, 20.0))

        assertEquals(setOf(near, coarse), dropped)
        assertNull(store.get(near))
        assertNull(store.get(coarse))
        assertTrue(store.get(far) != null)
    }

    @Test
    fun `a tile rendered before its area changed is thrown away when it arrives`() {
        val store = store()
        val key = TileKey(0, 0, 0)
        val token = store.request(key)!!
        assertNull("already pending", store.request(key))

        store.invalidate(WorldRect(1.0, 1.0, 2.0, 2.0))
        val stale = FakeTile("stale")

        assertFalse(store.put(key, token, stale))
        assertTrue(stale.released)
        assertNull(store.get(key))
        // It can be asked for again.
        assertTrue(store.request(key) != null)
    }

    @Test
    fun `tiles stay within the budget, dropping the least recently used that are not on screen this frame`() {
        val store = store(budget = 3 * TILE_BYTES)
        val tiles = (0L until 4L).map { TileKey(0, it, 0) to FakeTile("t$it") }

        store.beginFrame()
        tiles.take(3).forEach { (key, tile) -> store.put(key, store.request(key)!!, tile) }
        store.beginFrame()
        store.get(tiles[0].first) // drawn again this frame
        store.put(tiles[3].first, store.request(tiles[3].first)!!, tiles[3].second)

        assertEquals(3 * TILE_BYTES, store.heldBytes)
        assertTrue("least recently used and off screen", tiles[1].second.released)
        assertTrue(store.get(tiles[0].first) != null)
    }

    @Test
    fun `the budget grows to what the screen needs, so a large screen keeps its tiles`() {
        val store = store(budget = 2 * TILE_BYTES)
        store.screenBytes = 4 * TILE_BYTES
        val tiles = (0L until 5L).map { TileKey(0, it, 0) to FakeTile("t$it") }

        store.beginFrame()
        tiles.take(4).forEach { (key, tile) -> store.put(key, store.request(key)!!, tile) }
        store.beginFrame()
        store.put(tiles[4].first, store.request(tiles[4].first)!!, tiles[4].second)

        assertEquals(4 * TILE_BYTES, store.heldBytes)
        assertTrue(tiles[0].second.released)
        assertFalse(tiles[1].second.released)
    }

    @Test
    fun `clearing releases every tile and forgets pending requests`() {
        val store = store()
        val key = TileKey(0, 0, 0)
        val tile = FakeTile("t")
        store.put(key, store.request(key)!!, tile)
        val pending = store.request(TileKey(0, 1, 0))!!

        store.clear()

        assertTrue(tile.released)
        assertEquals(0L, store.heldBytes)
        assertFalse(store.put(TileKey(0, 1, 0), pending, FakeTile("late")))
    }

    @Test
    fun `tiles are used for crowded or hairline views only`() {
        assertFalse(TileGrid.shouldTile(visibleElements = TileGrid.MAX_VECTOR_ELEMENTS, hairlineStrokes = 0, visibleStrokes = TileGrid.MAX_VECTOR_ELEMENTS))
        assertTrue(TileGrid.shouldTile(visibleElements = TileGrid.MAX_VECTOR_ELEMENTS + 1, hairlineStrokes = 0, visibleStrokes = TileGrid.MAX_VECTOR_ELEMENTS + 1))
        assertTrue("most strokes thinner than half a pixel", TileGrid.shouldTile(visibleElements = 150, hairlineStrokes = 80, visibleStrokes = 150))
        assertFalse(TileGrid.shouldTile(visibleElements = 150, hairlineStrokes = 50, visibleStrokes = 150))
        assertFalse(TileGrid.shouldTile(visibleElements = 0, hairlineStrokes = 0, visibleStrokes = 0))
    }

    @Test
    fun `a view with any stroke magnified past 100x is never tiled, however crowded`() {
        // Only the vector path draws the finer and rebased deep-zoom meshes.
        assertFalse(TileGrid.shouldTile(visibleElements = 5_000, hairlineStrokes = 0, visibleStrokes = 5_000, deepStrokes = 1))
        assertTrue(TileGrid.shouldTile(visibleElements = 5_000, hairlineStrokes = 0, visibleStrokes = 5_000, deepStrokes = 0))
    }

    @Test
    fun `the budget is a tenth of the memory class between 48 and 128 MB`() {
        assertEquals(48L shl 20, TileGrid.budgetBytes(memoryClassMb = 192))
        assertEquals(51L shl 20, TileGrid.budgetBytes(memoryClassMb = 512))
        assertEquals(128L shl 20, TileGrid.budgetBytes(memoryClassMb = 4_096))
    }

    private companion object {
        const val TILE_BYTES = 256L * 256 * 4
    }
}
