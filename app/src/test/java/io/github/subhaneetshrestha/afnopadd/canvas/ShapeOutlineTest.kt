package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot

class ShapeOutlineTest {

    private fun length(s: ShapeOutline.Segment) = hypot((s.end.x - s.start.x).toDouble(), (s.end.y - s.start.y).toDouble())

    @Test
    fun `an arrow is its shaft plus two head strokes that scale with the line width`() {
        val thin = ShapeOutline.segments(ShapeKind.ARROW, LocalPoint(0f, 0f), LocalPoint(100f, 0f), strokeWidth = 2f)
        val thick = ShapeOutline.segments(ShapeKind.ARROW, LocalPoint(0f, 0f), LocalPoint(100f, 0f), strokeWidth = 4f)

        assertEquals(3, thin.size)
        assertEquals(100.0, length(thin[0]), 1e-4)
        // Both head strokes start at the tip and are as long as each other.
        assertTrue(thin.drop(1).all { it.start == LocalPoint(100f, 0f) })
        assertEquals(length(thin[1]), length(thin[2]), 1e-4)
        assertEquals(2 * length(thin[1]), length(thick[1]), 1e-3)
        // They point back along the shaft, mirrored about it.
        val a = atan2(thin[1].end.y.toDouble(), (thin[1].end.x - 100f).toDouble())
        val b = atan2(thin[2].end.y.toDouble(), (thin[2].end.x - 100f).toDouble())
        assertEquals(-a, b, 1e-4)
    }

    @Test
    fun `an arrowhead never outgrows a short shaft`() {
        val short = ShapeOutline.segments(ShapeKind.ARROW, LocalPoint(0f, 0f), LocalPoint(6f, 0f), strokeWidth = 4f)

        assertTrue("head ${length(short[1])} longer than half the shaft", length(short[1]) <= 3.0 + 1e-6)
    }

    @Test
    fun `rectangles have four sides and lines one`() {
        assertEquals(4, ShapeOutline.segments(ShapeKind.RECTANGLE, LocalPoint(0f, 0f), LocalPoint(10f, 5f), 1f).size)
        assertEquals(1, ShapeOutline.segments(ShapeKind.LINE, LocalPoint(0f, 0f), LocalPoint(10f, 5f), 1f).size)
    }

    @Test
    fun `bounds cover the drawn stroke including arrowheads`() {
        val rect = ShapeOutline.localBounds(ShapeKind.RECTANGLE, LocalPoint(10f, 20f), LocalPoint(0f, 0f), strokeWidth = 2f)
        assertEquals(listOf(-1f, -1f, 11f, 21f), listOf(rect.left, rect.top, rect.right, rect.bottom))

        val ellipse = ShapeOutline.localBounds(ShapeKind.ELLIPSE, LocalPoint(0f, 0f), LocalPoint(40f, 20f), strokeWidth = 4f)
        assertEquals(listOf(-2f, -2f, 42f, 22f), listOf(ellipse.left, ellipse.top, ellipse.right, ellipse.bottom))

        val arrow = ShapeOutline.localBounds(ShapeKind.ARROW, LocalPoint(0f, 0f), LocalPoint(100f, 0f), strokeWidth = 2f)
        assertTrue("the arrowhead's spread is outside the bounds", arrow.top < -1f && arrow.bottom > 1f)
    }
}
