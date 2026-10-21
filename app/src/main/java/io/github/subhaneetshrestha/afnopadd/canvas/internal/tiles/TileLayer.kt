package io.github.subhaneetshrestha.afnopadd.canvas.internal.tiles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import androidx.core.graphics.withClip
import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasMath
import io.github.subhaneetshrestha.afnopadd.canvas.FrameLevels
import io.github.subhaneetshrestha.afnopadd.canvas.Viewport
import io.github.subhaneetshrestha.afnopadd.canvas.internal.DryLayer
import io.github.subhaneetshrestha.afnopadd.document.AssetId
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import java.io.File
import kotlin.math.roundToInt

/**
 * The tile pyramid on screen (SPEC-canvas-engine decision 5). When [shouldTile] says a view is too
 * crowded or too fine for vectors, [draw] paints it from 256 px tiles of the nearest power-of-two
 * level. A tile that is not ready yet, or was invalidated by a transaction, or lies under an element
 * hidden by a gesture in progress (the eraser's), is drawn as vectors clipped to its square, so the
 * view is always complete and current; missing tiles are rendered in the background, the middle of
 * the screen first, and [onTileReady] asks for a redraw when one arrives.
 *
 * Tiles stay within [budgetBytes] and are all released by [trim] (the app going to the background)
 * and [release]. Used from the UI thread only.
 */
internal class TileLayer(
    budgetBytes: Long,
    fileFor: (AssetId) -> File?,
    private val onTileReady: () -> Unit,
) {
    private val rasterizer = TileRasterizer(fileFor)
    private val store = TileStore<Bitmap>(
        budgetBytes = budgetBytes,
        bytesOf = { if (it === TileRasterizer.EMPTY) 0L else it.allocationByteCount.toLong() },
        release = { if (it !== TileRasterizer.EMPTY) it.recycle() },
    )
    private var synced: ElementSnapshot? = null

    /** The tiles on screen; read on the tile thread to skip renders a pan has made pointless. */
    @Volatile
    private var wanted: Set<TileKey> = emptySet()

    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = Rect()
    private val clip = Path()

    /** Bytes of tiles held, for tests and the budget guard. */
    val heldBytes: Long get() = store.heldBytes
    val heldCount: Int get() = store.heldCount

    /** How many tiles the last [draw] had to patch with vectors. */
    var lastPatchedTiles = 0
        private set

    /** Follows the sheet: tiles overlapping what a transaction changed are dropped (all of them if the change is not known). */
    fun sync(snapshot: ElementSnapshot) {
        val previous = synced
        synced = snapshot
        if (previous == null || previous === snapshot) return
        val changed = snapshot.changedSince(previous)
        if (changed == null) {
            store.clear()
            return
        }
        for (id in changed) {
            previous.elements[id]?.bounds?.let(store::invalidate)
            snapshot.elements[id]?.bounds?.let(store::invalidate)
        }
    }

    /** Whether [visible] at [camera] is better drawn from tiles: too many elements, or mostly hairline strokes. */
    fun shouldTile(visible: List<Element>, camera: Camera): Boolean {
        if (visible.size > TileGrid.MAX_VECTOR_ELEMENTS) return true
        var strokes = 0
        var hairlines = 0
        for (element in visible) {
            if (element !is StrokeElement) continue
            strokes++
            val px = element.brush.size * camera.zoom * FrameLevels.worldUnitsPerLocalUnit(element.frameLevel)
            if (px < TileGrid.HAIRLINE_PX) hairlines++
        }
        return TileGrid.shouldTile(visible.size, hairlines, strokes)
    }

    fun draw(
        canvas: Canvas,
        snapshot: ElementSnapshot,
        visible: List<Element>,
        hidden: Set<ElementId>,
        camera: Camera,
        viewport: Viewport,
        visibleRect: WorldRect,
        vectors: DryLayer,
    ) {
        store.beginFrame()
        val level = TileGrid.levelFor(camera.zoom)
        val keys = TileGrid.keysCovering(visibleRect, level)
        wanted = keys.toHashSet()
        val hiddenBounds = hidden.mapNotNull { snapshot.elements[it]?.bounds }
        val patches = ArrayList<WorldRect>()
        for (key in keys) {
            val bounds = TileGrid.bounds(key)
            val tile = store.get(key)
            if (tile != null && hiddenBounds.none { it.overlaps(bounds) }) {
                if (tile !== TileRasterizer.EMPTY) {
                    screenRect(bounds, camera, viewport)
                    canvas.drawBitmap(tile, null, dst, tilePaint)
                }
            } else {
                patches += bounds
                if (tile == null) requestTile(snapshot, key)
            }
        }
        lastPatchedTiles = patches.size
        if (patches.isEmpty()) return
        val elements = visible.filter { element -> patches.any { it.overlaps(element.bounds) } }
        clip.rewind()
        for (patch in patches) {
            screenRect(patch, camera, viewport)
            clip.addRect(dst.left.toFloat(), dst.top.toFloat(), dst.right.toFloat(), dst.bottom.toFloat(), Path.Direction.CW)
        }
        canvas.withClip(clip) { vectors.draw(this, elements, hidden, camera, viewport) }
    }

    /** Drops every tile; the next frames render them again. */
    fun trim() = store.clear()

    fun release() {
        store.clear()
        rasterizer.shutdown()
    }

    private fun requestTile(snapshot: ElementSnapshot, key: TileKey) {
        if (rasterizer.queuedCount >= MAX_QUEUED) return
        val token = store.request(key) ?: return
        rasterizer.render(snapshot, key, stillWanted = { key in wanted }) { tile ->
            if (tile == null) {
                store.abandon(key, token)
            } else if (store.put(key, token, tile)) {
                onTileReady()
            }
        }
    }

    /** [bounds] on screen in whole pixels; neighbouring tiles round their shared edge the same way, so no seams open. */
    private fun screenRect(bounds: WorldRect, camera: Camera, viewport: Viewport) {
        val topLeft = CanvasMath.worldToScreen(camera, viewport, WorldPoint(bounds.left, bounds.top))
        val bottomRight = CanvasMath.worldToScreen(camera, viewport, WorldPoint(bounds.right, bounds.bottom))
        dst.set(topLeft.x.roundToInt(), topLeft.y.roundToInt(), bottomRight.x.roundToInt(), bottomRight.y.roundToInt())
    }

    private fun WorldRect.overlaps(other: WorldRect) = left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private companion object {
        /** Renders queued at once; a pan replaces what it scrolls past instead of finishing a backlog. */
        const val MAX_QUEUED = 8
    }
}
