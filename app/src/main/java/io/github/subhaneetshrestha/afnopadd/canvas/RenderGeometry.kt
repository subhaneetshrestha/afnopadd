package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.ElementSnapshot
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class PixelSize(val width: Int, val height: Int)

/** A world region and the scale that maps it onto a bitmap of the given pixel size. */
data class RenderFit(val bounds: WorldRect, val scale: Double, val widthPx: Int, val heightPx: Int)

/** Pure geometry for off-screen rendering: what to render and at which size. */
object RenderGeometry {

    /** The union of every element's bounds, or null for an empty sheet. */
    fun contentBounds(snapshot: ElementSnapshot): WorldRect? {
        val elements = snapshot.elements.values
        if (elements.isEmpty()) return null
        return WorldRect(
            left = elements.minOf { it.bounds.left },
            top = elements.minOf { it.bounds.top },
            right = elements.maxOf { it.bounds.right },
            bottom = elements.maxOf { it.bounds.bottom },
        )
    }

    /** Bitmap size needed to show [bounds] at [scale] pixels per world unit. */
    fun pixelSize(bounds: WorldRect, scale: Double): PixelSize =
        PixelSize(ceil(bounds.width * scale - 1e-9).toInt().coerceAtLeast(1), ceil(bounds.height * scale - 1e-9).toInt().coerceAtLeast(1))

    /**
     * Pads [content] by [paddingFraction] of its longer side and scales it so the longer edge is
     * [maxEdgePx] pixels. A zero-size region becomes a square around its centre.
     */
    fun fit(content: WorldRect, maxEdgePx: Int, paddingFraction: Double = 0.05): RenderFit {
        val side = max(content.width, content.height).takeIf { it > 0.0 } ?: 1.0
        val pad = side * paddingFraction
        var bounds = WorldRect(content.left - pad, content.top - pad, content.right + pad, content.bottom + pad)
        if (bounds.width <= 0.0 || bounds.height <= 0.0) {
            val cx = (content.left + content.right) / 2
            val cy = (content.top + content.bottom) / 2
            bounds = WorldRect(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        }
        val scale = maxEdgePx / max(bounds.width, bounds.height)
        return RenderFit(
            bounds = bounds,
            scale = scale,
            widthPx = (bounds.width * scale).roundToInt().coerceIn(1, maxEdgePx),
            heightPx = (bounds.height * scale).roundToInt().coerceIn(1, maxEdgePx),
        )
    }
}
