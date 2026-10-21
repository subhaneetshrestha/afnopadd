package io.github.subhaneetshrestha.afnopadd.canvas.internal

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.withMatrix
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasMath
import io.github.subhaneetshrestha.afnopadd.canvas.FrameTransform
import io.github.subhaneetshrestha.afnopadd.canvas.Viewport
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.ShapeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import kotlin.math.max

/**
 * Draws committed elements onto the screen canvas: strokes through Ink with each element's exact
 * frame transform, shapes as paths, images through the [ImagePainter], and anything else (notes,
 * unknown kinds) as a placeholder outline. Used from the UI thread only.
 *
 * A stroke smaller than [SKETCH_MAX_PX] on screen is drawn as a short cached polyline instead of
 * its Ink mesh. At that size the two look the same, and it keeps a zoomed-out sheet from pushing
 * thousands of meshes through the [StrokeCache]: drawing them all in the same order each frame
 * made the least-recently-used cache evict every mesh just before it was needed (measured at 4.9 s
 * per frame for 5,000 strokes). The tile pyramid in Task 24 replaces this for overview zooms.
 */
internal class DryLayer(
    private val renderer: CanvasStrokeRenderer = CanvasStrokeRenderer.create(),
    val cache: StrokeCache = StrokeCache(),
    /** Draws images; without one they are placeholder outlines. */
    private val imagePainter: ImagePainter? = null,
) {
    private class Sketch(val points: StrokePoints, val brushKey: Any, val path: Path, val color: Int)

    private val sketches = object : LinkedHashMap<ElementId, Sketch>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ElementId, Sketch>): Boolean = size > MAX_SKETCHES
    }

    private val sketchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF9E9E9E.toInt()
        strokeWidth = 1f
    }

    private val shapePainter = ShapePainter()

    /** Reused for every element: the canvas and Ink's renderer read it during the call only. */
    private val matrix = Matrix()

    val sketchCount: Int get() = sketches.size

    /**
     * Starts a frame: meshes and image bitmaps drawn from now on are kept until the next call, even
     * over budget. Call once per frame before every [draw] of that frame; without frames, the caches
     * are plain LRU.
     */
    fun beginFrame() {
        cache.beginFrame()
        imagePainter?.beginFrame()
    }

    fun draw(canvas: Canvas, elements: List<Element>, hidden: Set<ElementId>, camera: Camera, viewport: Viewport) {
        for (element in elements) {
            if (element.id in hidden) continue
            if (element is StrokeElement) {
                val transform = CanvasMath.frameToScreen(camera, viewport, element.anchor, element.frameLevel)
                when (val variant = DeepZoom.variantFor(transform, viewport)) {
                    DeepZoom.Base -> {
                        setMatrix(transform)
                        if (max(element.bounds.width, element.bounds.height) * camera.zoom < SKETCH_MAX_PX) {
                            val sketch = sketchFor(element)
                            sketchPaint.color = sketch.color
                            sketchPaint.strokeWidth = element.brush.size
                            canvas.withMatrix(matrix) { drawPath(sketch.path, sketchPaint) }
                        } else {
                            drawStroke(canvas, cache.strokeFor(element))
                        }
                    }
                    DeepZoom.Fine -> {
                        setMatrix(transform)
                        drawStroke(canvas, cache.fineFor(element))
                    }
                    is DeepZoom.Clipped -> {
                        setMatrix(DeepZoom.drawTransform(transform, variant))
                        cache.clippedFor(element, variant).forEach { drawStroke(canvas, it) }
                    }
                }
            } else if (element is ShapeElement) {
                val transform = CanvasMath.frameToScreen(camera, viewport, element.anchor, element.frameLevel)
                val origin = deepOrigin(transform, viewport)
                shapePainter.draw(canvas, element, transform, origin?.first ?: 0.0, origin?.second ?: 0.0)
            } else if (element is ImageElement && imagePainter != null) {
                val transform = CanvasMath.frameToScreen(camera, viewport, element.anchor, element.frameLevel)
                val origin = deepOrigin(transform, viewport)
                imagePainter.draw(canvas, element, transform, origin?.first ?: 0.0, origin?.second ?: 0.0)
            } else {
                val topLeft = CanvasMath.worldToScreen(camera, viewport, WorldPoint(element.bounds.left, element.bounds.top))
                val bottomRight = CanvasMath.worldToScreen(camera, viewport, WorldPoint(element.bounds.right, element.bounds.bottom))
                canvas.drawRect(topLeft.x.toFloat(), topLeft.y.toFloat(), bottomRight.x.toFloat(), bottomRight.y.toFloat(), outlinePaint)
            }
        }
    }

    /** Ink positions the stroke with the canvas transform; the matrix argument only tells it the
     * stroke-to-screen scale for anti-aliasing, so both are the same [matrix]. */
    private fun drawStroke(canvas: Canvas, stroke: androidx.ink.strokes.Stroke) {
        canvas.withMatrix(matrix) { renderer.draw(this, stroke, matrix) }
    }

    private fun setMatrix(transform: FrameTransform) {
        matrix.setScale(transform.scale.toFloat(), transform.scale.toFloat())
        matrix.postTranslate(transform.translateX.toFloat(), transform.translateY.toFloat())
    }

    /**
     * Beyond [DeepZoom.CLIP_ABOVE], the view's centre in the element's frame units, as an exact
     * float, for the painters to rebase shapes and images around; null at ordinary zooms.
     */
    private fun deepOrigin(transform: FrameTransform, viewport: Viewport): Pair<Double, Double>? {
        if (transform.scale <= DeepZoom.CLIP_ABOVE) return null
        val x = ((viewport.centerX - transform.translateX) / transform.scale).toFloat().toDouble()
        val y = ((viewport.centerY - transform.translateY) / transform.scale).toFloat().toDouble()
        return x to y
    }

    /** Lets go of image bitmaps and loads in flight. */
    fun release() {
        imagePainter?.release()
    }

    private fun sketchFor(element: StrokeElement): Sketch {
        val cached = sketches[element.id]
        if (cached != null && cached.points === element.points && cached.brushKey == element.brush) return cached
        val points = element.points
        return Sketch(points, element.brush, sketchPath(points), InkBridge.colorIntArgb(element.brush)).also { sketches[element.id] = it }
    }

    companion object {
        /** Strokes whose bounds span fewer screen pixels than this are drawn as sketches. */
        const val SKETCH_MAX_PX = 32.0
        private const val SKETCH_POINTS = 16
        private const val MAX_SKETCHES = 20_000

        /** Every n-th sample, always keeping the last, so a sketch has at most [SKETCH_POINTS] points. */
        fun sketchPath(points: StrokePoints): Path {
            val path = Path()
            if (points.count == 0) return path
            val step = max(1, (points.count + SKETCH_POINTS - 1) / SKETCH_POINTS)
            path.moveTo(points.x(0), points.y(0))
            var i = step
            while (i < points.count - 1) {
                path.lineTo(points.x(i), points.y(i))
                i += step
            }
            path.lineTo(points.x(points.count - 1), points.y(points.count - 1))
            return path
        }
    }
}
