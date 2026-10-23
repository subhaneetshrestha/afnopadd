package io.github.subhaneetshrestha.afnopadd.canvas.internal.tiles

import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

/** A tile of the pyramid: [level] sets its scale (2^level screen pixels per world unit), [x] and [y] its column and row. */
internal data class TileKey(val level: Int, val x: Long, val y: Long)

/**
 * The geometry of the tile pyramid (canvas-engine spec decision 5): square [TILE_PX] tiles per
 * power-of-two level, the level at or above the camera's zoom (so a tile is drawn scaled by 0.5x
 * to 1x), and when tiles are worth it at all.
 */
internal object TileGrid {
    const val TILE_PX = 256

    /**
     * More visible elements than this are drawn from tiles. The spec's 2,000 was far too many to draw
     * as meshes within a frame: a mesh stroke costs the UI thread about 65 us on the emulator, so 330
     * of them took 22 ms (PerfTest, docs/perf/PERF.md).
     */
    const val MAX_VECTOR_ELEMENTS = 200

    /** Strokes thinner than this on screen count as hairlines. */
    const val HAIRLINE_PX = 0.5

    /**
     * The level at or just above the zoom, so tiles are only ever drawn shrunk (0.5x to 1x) and
     * stay as sharp as vectors; enlarged tiles would blur, which shows now that dense pages at
     * working zoom use tiles too.
     */
    fun levelFor(zoom: Double): Int = ceil(log2(zoom) - 1e-9).toInt()

    fun scale(level: Int): Double = 2.0.pow(level)

    fun worldSize(level: Int): Double = TILE_PX / scale(level)

    fun bounds(key: TileKey): WorldRect {
        val size = worldSize(key.level)
        return WorldRect(key.x * size, key.y * size, (key.x + 1) * size, (key.y + 1) * size)
    }

    /** The tiles of [level] that cover [rect], nearest its centre first. */
    fun keysCovering(rect: WorldRect, level: Int): List<TileKey> {
        val size = worldSize(level)
        val firstX = floor(rect.left / size).toLong()
        val firstY = floor(rect.top / size).toLong()
        val lastX = maxOf(firstX, ceil(rect.right / size).toLong() - 1)
        val lastY = maxOf(firstY, ceil(rect.bottom / size).toLong() - 1)
        val cx = (rect.left + rect.right) / 2 / size - 0.5
        val cy = (rect.top + rect.bottom) / 2 / size - 0.5
        val keys = ArrayList<TileKey>(((lastX - firstX + 1) * (lastY - firstY + 1)).toInt())
        for (y in firstY..lastY) for (x in firstX..lastX) keys += TileKey(level, x, y)
        keys.sortBy { (it.x - cx) * (it.x - cx) + (it.y - cy) * (it.y - cy) }
        return keys
    }

    /**
     * Tiles pay off when there are too many elements to draw as vectors, or most strokes are
     * hairlines; never when any stroke is magnified past [io.github.subhaneetshrestha.afnopadd.canvas.internal.DeepZoom.FINE_ABOVE],
     * since only the vector path draws the finer and rebased deep-zoom variants, and a tile would
     * show facets and float jitter its vector patch does not.
     */
    fun shouldTile(visibleElements: Int, hairlineStrokes: Int, visibleStrokes: Int, deepStrokes: Int = 0): Boolean =
        deepStrokes == 0 && (visibleElements > MAX_VECTOR_ELEMENTS || (visibleStrokes > 0 && hairlineStrokes * 2 > visibleStrokes))

    /** A tenth of the app's memory class, between 48 and 128 MB (canvas-engine spec decision 3, owner). */
    fun budgetBytes(memoryClassMb: Int): Long = (memoryClassMb / 10L).coerceIn(48L, 128L) shl 20
}

/**
 * The tiles held and the ones being rendered, on the UI thread. A render is started with [request],
 * which returns a token, and delivered with [put]; an [invalidate] in between voids the token, so a
 * tile drawn from an older sheet is released on arrival instead of shown. Tiles stay within
 * [budgetBytes], least recently drawn first, never one drawn in the current frame.
 */
internal class TileStore<T>(
    private val budgetBytes: Long,
    private val bytesOf: (T) -> Long,
    private val release: (T) -> Unit,
) {
    private class Held<T>(val tile: T, val bytes: Long, var lastFrame: Long)

    private val held = LinkedHashMap<TileKey, Held<T>>(64, 0.75f, true)
    private val pending = HashMap<TileKey, Long>()
    private var nextToken = 1L
    private var frame = 0L

    /**
     * The budget actually kept: never less than [screenBytes] (set by the layer to about one and a
     * half screens of tiles), since tiles on screen are never evicted and a large tablet at 0.5x
     * needs more than the memory-class budget.
     */
    var screenBytes = 0L

    private val effectiveBudget: Long get() = maxOf(budgetBytes, screenBytes)

    var heldBytes = 0L
        private set

    val heldCount: Int get() = held.size

    fun beginFrame() {
        frame++
    }

    fun get(key: TileKey): T? = held[key]?.also { it.lastFrame = frame }?.tile

    fun isPending(key: TileKey): Boolean = key in pending

    /** A token for rendering [key], or null if it is already held or being rendered. */
    fun request(key: TileKey): Long? {
        if (key in held || key in pending) return null
        return nextToken++.also { pending[key] = it }
    }

    /** Takes a rendered tile; false (and the tile released) if its request was voided meanwhile. */
    fun put(key: TileKey, token: Long, tile: T): Boolean {
        if (pending[key] != token) {
            release(tile)
            return false
        }
        pending.remove(key)
        val bytes = bytesOf(tile)
        held[key] = Held(tile, bytes, frame)
        heldBytes += bytes
        trim()
        return true
    }

    /** Forgets a request that produced nothing (no longer wanted, or failed), so it can be made again. */
    fun abandon(key: TileKey, token: Long) {
        if (pending[key] == token) pending.remove(key)
    }

    /** Drops the tiles, held or being rendered, that overlap [rect] at any level; returns the held ones dropped. */
    fun invalidate(rect: WorldRect): Set<TileKey> {
        pending.keys.removeAll { overlaps(it, rect) }
        val dropped = held.keys.filter { overlaps(it, rect) }.toSet()
        dropped.forEach(::drop)
        return dropped
    }

    fun clear() {
        pending.clear()
        held.keys.toList().forEach(::drop)
    }

    private fun overlaps(key: TileKey, rect: WorldRect): Boolean {
        val b = TileGrid.bounds(key)
        return b.left < rect.right && rect.left < b.right && b.top < rect.bottom && rect.top < b.bottom
    }

    private fun drop(key: TileKey) {
        val entry = held.remove(key) ?: return
        heldBytes -= entry.bytes
        release(entry.tile)
    }

    private fun trim() {
        val budget = effectiveBudget
        if (heldBytes <= budget) return
        val victims = ArrayList<TileKey>()
        var bytes = heldBytes
        for ((key, entry) in held) {
            if (bytes <= budget) break
            if (entry.lastFrame == frame) continue
            victims += key
            bytes -= entry.bytes
        }
        victims.forEach(::drop)
    }
}
