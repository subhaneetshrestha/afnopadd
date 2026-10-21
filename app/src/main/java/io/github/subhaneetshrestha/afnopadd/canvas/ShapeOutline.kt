package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.LocalRect
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The drawn geometry of a shape in its frame units, shared by the painter, hit-testing and the
 * shape tools so all three agree on where a shape's ink is. Rectangles span the two points as
 * opposite corners and ellipses fill that box; lines and arrows run from start to end, and an
 * arrow adds two head strokes at the end whose size follows the line width.
 */
object ShapeOutline {

    data class Segment(val start: LocalPoint, val end: LocalPoint)

    /** Straight segments for every kind but the ellipse, which painters draw as an oval and hit-testing approximates. */
    fun segments(kind: ShapeKind, start: LocalPoint, end: LocalPoint, strokeWidth: Float): List<Segment> = when (kind) {
        ShapeKind.LINE -> listOf(Segment(start, end))
        ShapeKind.ARROW -> listOf(Segment(start, end)) + arrowHead(start, end, strokeWidth)
        ShapeKind.RECTANGLE -> {
            val corners = listOf(start, LocalPoint(end.x, start.y), end, LocalPoint(start.x, end.y))
            corners.indices.map { Segment(corners[it], corners[(it + 1) % corners.size]) }
        }
        ShapeKind.ELLIPSE -> ellipsePolygon(start, end)
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
    fun localBounds(kind: ShapeKind, start: LocalPoint, end: LocalPoint, strokeWidth: Float): LocalRect {
        val points = when (kind) {
            ShapeKind.ARROW -> segments(kind, start, end, strokeWidth).flatMap { listOf(it.start, it.end) }
            else -> listOf(start, end)
        }
        val pad = strokeWidth / 2
        return LocalRect(
            left = points.minOf { it.x } - pad,
            top = points.minOf { it.y } - pad,
            right = points.maxOf { it.x } + pad,
            bottom = points.maxOf { it.y } + pad,
        )
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
}
