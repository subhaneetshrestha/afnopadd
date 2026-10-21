package io.github.subhaneetshrestha.afnopadd.canvas.internal.tiles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.graphics.createBitmap
import io.github.subhaneetshrestha.afnopadd.canvas.ElementIndex
import io.github.subhaneetshrestha.afnopadd.canvas.internal.RegionPainter
import io.github.subhaneetshrestha.afnopadd.document.AssetId
import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Renders tiles on one background thread (SPEC-canvas-engine decision 5): the tile's elements are
 * drawn by a [RegionPainter] onto a transparent software bitmap, which is then copied into a
 * hardware bitmap so drawing it costs the UI thread next to nothing. Results come back on the main
 * thread. The thread keeps its own spatial index, synced to whichever snapshot a tile is drawn from.
 */
internal class TileRasterizer(fileFor: (AssetId) -> File?) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "afnopadd-tiles").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val main = Handler(Looper.getMainLooper())
    private val painter = RegionPainter(fileFor, imageCacheBytes = 24 * 1024 * 1024, sketchSmallStrokes = true, maxImagePx = 2_048)
    private val index = ElementIndex()

    /** Renders queued but not started; the layer keeps this small so a fast pan does not build a backlog. */
    private val queued = AtomicInteger(0)
    val queuedCount: Int get() = queued.get()

    /**
     * Queues [key] drawn from [snapshot]; [onDone] gets the hardware bitmap, or null if the tile was
     * no longer wanted by the time its turn came ([stillWanted] is asked on the tile thread) or failed.
     */
    fun render(snapshot: ElementSnapshot, key: TileKey, stillWanted: () -> Boolean, onDone: (Bitmap?) -> Unit) {
        if (executor.isShutdown) {
            onDone(null)
            return
        }
        queued.incrementAndGet()
        try {
            executor.execute {
                queued.decrementAndGet()
                val tile = if (stillWanted()) draw(snapshot, key) else null
                main.post { onDone(tile) }
            }
        } catch (e: RejectedExecutionException) {
            // Shut down between the check and the call: nothing to render any more.
            queued.decrementAndGet()
            onDone(null)
        }
    }

    /** Stops the thread after the render in progress; later results are dropped. */
    fun shutdown() {
        executor.execute { painter.clear() }
        executor.shutdown()
    }

    private fun draw(snapshot: ElementSnapshot, key: TileKey): Bitmap? = try {
        val bounds = TileGrid.bounds(key)
        index.sync(snapshot)
        val elements = index.visible(snapshot, bounds)
        if (elements.isEmpty()) {
            EMPTY
        } else {
            val software = createBitmap(TileGrid.TILE_PX, TileGrid.TILE_PX)
            painter.draw(Canvas(software), elements, bounds, TileGrid.scale(key.level))
            software.copy(Bitmap.Config.HARDWARE, false).also { software.recycle() }
        }
    } catch (e: RuntimeException) {
        // A tile is an optimisation: the layer draws vectors where one is missing.
        Log.w(TAG, "Could not render tile $key", e)
        null
    } catch (e: OutOfMemoryError) {
        // Uncaught here it would kill the process from the tile thread; vectors stand in instead.
        painter.clear()
        Log.w(TAG, "Out of memory rendering tile $key", e)
        null
    }

    companion object {
        private const val TAG = "TileRasterizer"

        /** Stands for a tile with nothing in it, so empty space costs no memory and no drawing. */
        val EMPTY: Bitmap = createBitmap(1, 1)
    }
}
