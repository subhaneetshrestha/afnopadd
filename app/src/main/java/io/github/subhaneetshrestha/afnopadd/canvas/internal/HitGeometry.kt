package io.github.subhaneetshrestha.afnopadd.canvas.internal

import kotlin.math.hypot

/** Plain 2-D distance helpers for hit-testing; segments may be degenerate (a single point). */
internal object HitGeometry {

    fun pointToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** Shortest distance between segments AB and CD; zero when they cross. */
    fun segmentToSegment(
        ax: Double, ay: Double, bx: Double, by: Double,
        cx: Double, cy: Double, dx: Double, dy: Double,
    ): Double {
        if (segmentsCross(ax, ay, bx, by, cx, cy, dx, dy)) return 0.0
        return minOf(
            minOf(pointToSegment(ax, ay, cx, cy, dx, dy), pointToSegment(bx, by, cx, cy, dx, dy)),
            minOf(pointToSegment(cx, cy, ax, ay, bx, by), pointToSegment(dx, dy, ax, ay, bx, by)),
        )
    }

    /** Shortest distance between segment AB and an axis-aligned rectangle; zero when they overlap. */
    fun segmentToRect(
        ax: Double, ay: Double, bx: Double, by: Double,
        left: Double, top: Double, right: Double, bottom: Double,
    ): Double {
        if (ax in left..right && ay in top..bottom) return 0.0
        if (bx in left..right && by in top..bottom) return 0.0
        return minOf(
            minOf(segmentToSegment(ax, ay, bx, by, left, top, right, top), segmentToSegment(ax, ay, bx, by, right, top, right, bottom)),
            minOf(segmentToSegment(ax, ay, bx, by, right, bottom, left, bottom), segmentToSegment(ax, ay, bx, by, left, bottom, left, top)),
        )
    }

    private fun segmentsCross(
        ax: Double, ay: Double, bx: Double, by: Double,
        cx: Double, cy: Double, dx: Double, dy: Double,
    ): Boolean {
        val d1 = cross(cx, cy, dx, dy, ax, ay)
        val d2 = cross(cx, cy, dx, dy, bx, by)
        val d3 = cross(ax, ay, bx, by, cx, cy)
        val d4 = cross(ax, ay, bx, by, dx, dy)
        // Collinear and touching cases come out as distance zero from the point-to-segment checks.
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    /** Which side of line OA the point P lies on (sign of the 2-D cross product). */
    private fun cross(ox: Double, oy: Double, ax: Double, ay: Double, px: Double, py: Double): Double =
        (ax - ox) * (py - oy) - (ay - oy) * (px - ox)
}
