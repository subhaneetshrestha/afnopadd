package io.github.subhaneetshrestha.afnopadd.canvas.internal

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import io.github.subhaneetshrestha.afnopadd.canvas.FrameTransform
import io.github.subhaneetshrestha.afnopadd.canvas.ShapeOutline
import io.github.subhaneetshrestha.afnopadd.document.ShapeElement
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws shapes as anti-aliased vector paths (SPEC-canvas-engine decision 8). The path is built in
 * the shape's frame units, then moved to the target's pixels and stroked at the scaled width, so it
 * is tessellated at its final size and stays crisp at any zoom. Not thread-safe; one per renderer.
 */
internal class ShapePainter {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val matrix = Matrix()
    private val oval = RectF()

    fun draw(canvas: Canvas, shape: ShapeElement, transform: FrameTransform) {
        val start = shape.geometry.start
        val end = shape.geometry.end
        path.reset()
        when (shape.shape) {
            ShapeKind.ELLIPSE -> {
                oval.set(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
                path.addOval(oval, Path.Direction.CW)
            }
            ShapeKind.RECTANGLE -> {
                path.moveTo(start.x, start.y)
                path.lineTo(end.x, start.y)
                path.lineTo(end.x, end.y)
                path.lineTo(start.x, end.y)
                path.close()
            }
            ShapeKind.LINE, ShapeKind.ARROW -> {
                ShapeOutline.segments(shape.shape, start, end, shape.style.strokeWidth).forEach { segment ->
                    path.moveTo(segment.start.x, segment.start.y)
                    path.lineTo(segment.end.x, segment.end.y)
                }
            }
        }
        val scale = transform.scale.toFloat()
        // Rectangles and ellipses turn about their box centre; lines and arrows store turned endpoints.
        if (shape.rotationDeg != 0f) {
            matrix.setRotate(shape.rotationDeg, (start.x + end.x) / 2, (start.y + end.y) / 2)
            matrix.postScale(scale, scale)
        } else {
            matrix.setScale(scale, scale)
        }
        matrix.postTranslate(transform.translateX.toFloat(), transform.translateY.toFloat())
        path.transform(matrix)
        paint.strokeJoin = if (shape.shape == ShapeKind.RECTANGLE) Paint.Join.MITER else Paint.Join.ROUND
        paint.strokeWidth = shape.style.strokeWidth * scale
        val alpha = ((shape.style.colorArgb ushr 24 and 0xFF) * shape.style.opacity.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        paint.color = (alpha shl 24) or (shape.style.colorArgb and 0x00FFFFFF).toInt()
        canvas.drawPath(path, paint)
    }
}
