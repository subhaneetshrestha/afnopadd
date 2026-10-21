package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.LocalRect
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The drawn geometry of a shape in its frame units, shared by the painter, hit-testing and the
 * shape tools so all three agree on where a shape's ink is. Rectangles span the two points as
 * opposite corners and ellipses fill that box; lines and arrows run from start to end, and an
 * arrow adds two head strokes at the end whose size follows the line width.
 */
object ShapeOutline {

    data class Segment(val start: LocalPoint, val end: LocalPoint)

    /**
     * Straight segments for every kind but the ellipse, which painters draw as an oval and
     * hit-testing approximates. [rotationDeg] turns rectangles and ellipses clockwise about their
     * box centre (lines and arrows store rotated endpoints instead).
     */
    fun segments(kind: ShapeKind, start: LocalPoint, end: LocalPoint, strokeWidth: Float, rotationDeg: Float = 0f): List<Segment> = when (kind) {
        ShapeKind.LINE -> listOf(Segment(start, end))
        ShapeKind.ARROW -> listOf(Segment(start, end)) + arrowHead(start, end, strokeWidth)
        ShapeKind.RECTANGLE -> {
            val corners = listOf(start, LocalPoint(end.x, start.y), end, LocalPoint(start.x, end.y)).map { turn(it, start, end, rotationDeg) }
            corners.indices.map { Segment(corners[it], corners[(it + 1) % corners.size]) }
        }
        ShapeKind.ELLIPSE -> ellipsePolygon(start, end).map { Segment(turn(it.start, start, end, rotationDeg), turn(it.end, start, end, rotationDeg)) }
    }

    /** [point] turned clockwise by [degrees] about the centre of the box from [start] to [end]. */
    private fun turn(point: LocalPoint, start: LocalPoint, end: LocalPoint, degrees: Float): LocalPoint {
        if (degrees == 0f) return point
        val cx = (start.x + end.x) / 2.0
        val cy = (start.y + end.y) / 2.0
        val radians = Math.toRadians(degrees.toDouble())
        val dx = point.x - cx
        val dy = point.y - cy
        return LocalPoint((cx + dx * cos(radians) - dy * sin(radians)).toFloat(), (cy + dx * sin(radians) + dy * cos(radians)).toFloat())
    }

    /** The two head strokes of an arrow, from the tip back along the shaft at ±[HEAD_HALF_ANGLE_RAD]. */
    fun arrowHead(start: LocalPoint, end: LocalPoint, strokeWidth: Float): List<Segment> {
        val dx = (end.x - start.x).toDouble()
        val dy = (end.y - start.y).toDouble()
        val shaft = hypot(dx, dy)
        if (shaft == 0.0) return emptyList()
        // Proportional to the width so a thick arrow is not tipped by a sliver; never longer than half the shaft.
        val length = min(HEAD_LENGTH_PER_WIDTH * strokeWidth.toDouble(), shaft / 2)
        val ux = -dx / shaft
        val uy = -dy / shaft
        return listOf(-HEAD_HALF_ANGLE_RAD, HEAD_HALF_ANGLE_RAD).map { angle ->
            val rx = ux * cos(angle) - uy * sin(angle)
            val ry = ux * sin(angle) + uy * cos(angle)
            Segment(end, LocalPoint((end.x + rx * length).toFloat(), (end.y + ry * length).toFloat()))
        }
    }

    /** Where the shape's ink can reach, including half the stroke width on every side. */
    fun localBounds(kind: ShapeKind, start: LocalPoint, end: LocalPoint, strokeWidth: Float, rotationDeg: Float = 0f): LocalRect {
        if (rotationDeg != 0f && kind == ShapeKind.ELLIPSE) return rotatedEllipseBounds(start, end, strokeWidth, rotationDeg)
        val points = when {
            kind == ShapeKind.ARROW -> segments(kind, start, end, strokeWidth).flatMap { listOf(it.start, it.end) }
            kind == ShapeKind.RECTANGLE && rotationDeg != 0f -> segments(kind, start, end, strokeWidth, rotationDeg).map { it.start }
            else -> listOf(start, end)
        }
        // A turned rectangle's mitred corners reach out along the diagonal, up to width/2 * sqrt(2).
        val pad = if (kind == ShapeKind.RECTANGLE && rotationDeg != 0f) strokeWidth / 2 * SQRT2 else strokeWidth / 2
        return LocalRect(
            left = points.minOf { it.x } - pad,
            top = points.minOf { it.y } - pad,
            right = points.maxOf { it.x } + pad,
            bottom = points.maxOf { it.y } + pad,
        )
    }

    /** The exact box of an ellipse with semi-axes a and b turned by θ: half-extents √(a²cos²θ + b²sin²θ) and √(a²sin²θ + b²cos²θ). */
    private fun rotatedEllipseBounds(start: LocalPoint, end: LocalPoint, strokeWidth: Float, rotationDeg: Float): LocalRect {
        val cx = (start.x + end.x) / 2.0
        val cy = (start.y + end.y) / 2.0
        val a = abs(end.x - start.x) / 2.0
        val b = abs(end.y - start.y) / 2.0
        val radians = Math.toRadians(rotationDeg.toDouble())
        val c = cos(radians)
        val s = sin(radians)
        val halfWidth = sqrt(a * a * c * c + b * b * s * s) + strokeWidth / 2.0
        val halfHeight = sqrt(a * a * s * s + b * b * c * c) + strokeWidth / 2.0
        return LocalRect((cx - halfWidth).toFloat(), (cy - halfHeight).toFloat(), (cx + halfWidth).toFloat(), (cy + halfHeight).toFloat())
    }

    /** The ellipse inscribed in the box as a closed polygon; chord error is under 0.2 % of the radius. */
    private fun ellipsePolygon(start: LocalPoint, end: LocalPoint): List<Segment> {
        val cx = (start.x + end.x) / 2.0
        val cy = (start.y + end.y) / 2.0
        val rx = abs(end.x - start.x) / 2.0
        val ry = abs(end.y - start.y) / 2.0
        val ring = (0 until ELLIPSE_SEGMENTS).map {
            val angle = 2 * Math.PI * it / ELLIPSE_SEGMENTS
            LocalPoint((cx + rx * cos(angle)).toFloat(), (cy + ry * sin(angle)).toFloat())
        }
        return ring.indices.map { Segment(ring[it], ring[(it + 1) % ring.size]) }
    }

    /** Head length as a multiple of the line width. */
    const val HEAD_LENGTH_PER_WIDTH = 4.0
    private const val HEAD_HALF_ANGLE_RAD = 0.5 // about 29 degrees
    private const val ELLIPSE_SEGMENTS = 64
    private const val SQRT2 = 1.4142135f
}
