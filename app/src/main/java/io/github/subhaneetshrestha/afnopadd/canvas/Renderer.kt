package io.github.subhaneetshrestha.afnopadd.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.document.WorldRect

/** One piece of a tiled render; the bitmap is only valid during the callback. */
class RenderTile(val leftPx: Int, val topPx: Int, val bitmap: Bitmap)

/**
 * Draws a sheet region away from the screen: thumbnails, image export, PDF pages. Output always
 * uses a software canvas, so there is no hardware texture limit; very large regions go through
 * [renderTiled] to bound memory.
 */
interface Renderer {

    /** The region [bounds] at [scalePxPerUnit] pixels per world unit, as one bitmap. */
    fun renderBitmap(
        snapshot: ElementSnapshot,
        bounds: WorldRect,
        scalePxPerUnit: Double,
        background: Background = Background.PLAIN,
    ): Bitmap

    /** Draws onto any canvas whose origin is the top-left of [bounds] (PDF pages use this). */
    fun renderTo(
        canvas: Canvas,
        snapshot: ElementSnapshot,
        bounds: WorldRect,
        scalePxPerUnit: Double,
        background: Background = Background.PLAIN,
    )

    /** Renders the region tile by tile; each tile bitmap is recycled after [onTile] returns. */
    fun renderTiled(
        snapshot: ElementSnapshot,
        bounds: WorldRect,
        scalePxPerUnit: Double,
        background: Background = Background.PLAIN,
        tileSizePx: Int = 2_048,
        onTile: (RenderTile) -> Unit,
    )
}
