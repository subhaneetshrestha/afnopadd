package io.github.subhaneetshrestha.afnopadd.canvas.internal

import io.github.subhaneetshrestha.afnopadd.canvas.FrameTransform
import io.github.subhaneetshrestha.afnopadd.canvas.Viewport
import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * How a stroke is drawn when the camera is far deeper than the frame it was drawn in
 * (canvas-engine spec decision 4). The relative zoom is the frame transform's scale: screen pixels
 * per unit of the stroke's own frame.
 *
 * - Up to [FINE_ABOVE]: the stroke's own mesh.
 * - Up to [CLIP_ABOVE]: a mesh tessellated with a [FINE_EPSILON_DIVISOR] times finer epsilon, so a
 *   curve does not show facets.
 * - Beyond: a [Clipped] variant. Only the runs of points whose ink can reach the view are kept,
 *   moved to an origin on a grid near the view and multiplied by a power of ten, so they are
 *   tessellated at their usual epsilon in moderate numbers and drawn with a moderate scale and
 *   translation. Floats then keep sub-pixel precision at 100,000x, where the plain transform's
 *   translation alone runs to tens of millions of pixels and rounds by whole pixels from frame to
 *   frame. The grid cell is at least the view's size, so a variant serves a pan until the view
 *   leaves its cell.
 */
internal object DeepZoom {
    const val FINE_ABOVE = 100.0
    const val CLIP_ABOVE = 10_000.0
    const val FINE_EPSILON_DIVISOR = 100f

    sealed interface Variant
    data object Base : Variant
    data object Fine : Variant

    /** Points are drawn as `(p - origin) * 10^decade`, in a window [grid] wide cells around the view. */
    data class Clipped(val decade: Int, val grid: Double, val originX: Double, val originY: Double) : Variant {
        val factor: Double get() = 10.0.pow(decade)
        val clipLeft: Double get() = originX - 2 * grid
        val clipTop: Double get() = originY - 2 * grid
        val clipRight: Double get() = originX + 3 * grid
        val clipBottom: Double get() = originY + 3 * grid
    }

    fun variantFor(transform: FrameTransform, viewport: Viewport): Variant {
        val scale = transform.scale
        return when {
            scale <= FINE_ABOVE -> Base
            scale <= CLIP_ABOVE -> Fine
            else -> {
                val decade = floor(log10(scale)).toInt()
                // A power-of-ten cell at least as large as the view, in the stroke's units.
                val grid = 10.0.pow(ceil(log10(max(viewport.widthPx, viewport.heightPx) / scale)))
                val centreX = (viewport.centerX - transform.translateX) / scale
                val centreY = (viewport.centerY - transform.translateY) / scale
                Clipped(decade, grid, floor(centreX / grid) * grid, floor(centreY / grid) * grid)
            }
        }
    }

    /** The transform that draws [variant]'s rebased points where [transform] draws the originals. */
    fun drawTransform(transform: FrameTransform, variant: Clipped): FrameTransform = FrameTransform(
        scale = transform.scale / variant.factor,
        translateX = transform.translateX + variant.originX * transform.scale,
        translateY = transform.translateY + variant.originY * transform.scale,
    )

    fun rebased(point: LocalPoint, variant: Clipped): LocalPoint = LocalPoint(
        ((point.x - variant.originX) * variant.factor).toFloat(),
        ((point.y - variant.originY) * variant.factor).toFloat(),
    )

    /**
     * Index runs of the points to keep for a window: a point is kept when it, or a segment it ends,
     * comes within [margin] of the window, along with [NEIGHBOURS] points either side so the ink
     * near the window is shaped as in the whole stroke. Separate passes through the window are
     * separate runs, never joined across the gap.
     */
    fun keptRuns(points: StrokePoints, left: Double, top: Double, right: Double, bottom: Double, margin: Double): List<IntRange> {
        val l = left - margin
        val t = top - margin
        val r = right + margin
        val b = bottom + margin
        fun segmentNear(i: Int, j: Int): Boolean {
            val x0 = minOf(points.x(i), points.x(j))
            val x1 = maxOf(points.x(i), points.x(j))
            val y0 = minOf(points.y(i), points.y(j))
            val y1 = maxOf(points.y(i), points.y(j))
            return x1 >= l && x0 <= r && y1 >= t && y0 <= b
        }
        val count = points.count
        val keep = BooleanArray(count)
        for (i in 0 until count) {
            if (points.x(i) in l..r && points.y(i) in t..b) keep[i] = true
            if (i + 1 < count && segmentNear(i, i + 1)) {
                keep[i] = true
                keep[i + 1] = true
            }
        }
        val runs = ArrayList<IntRange>()
        var i = 0
        while (i < count) {
            if (!keep[i]) {
                i++
                continue
            }
            var end = i
            while (end + 1 < count && keep[end + 1]) end++
            runs += (i - NEIGHBOURS).coerceAtLeast(0)..(end + NEIGHBOURS).coerceAtMost(count - 1)
            i = end + 1
        }
        // Neighbours can make runs touch; touching runs are one pass.
        return runs.fold(ArrayList()) { merged, run ->
            val last = merged.lastOrNull()
            if (last != null && run.first <= last.last + 1) merged[merged.lastIndex] = last.first..maxOf(last.last, run.last) else merged += run
            merged
        }
    }

    const val NEIGHBOURS = 2
}
