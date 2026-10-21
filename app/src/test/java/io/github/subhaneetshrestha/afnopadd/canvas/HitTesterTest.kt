package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.canvas.HitTestFixtures.idOf
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HitTesterTest {

    private fun hitTesterFor(vararg elements: Element): Pair<SheetImpl, SheetHitTester> {
        val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Hits", 0L, 0L), elements.toList(), writer = {})
        return sheet to SheetHitTester(sheet)
    }

    @Test
    fun `a path crossing a stroke hits it and one passing beside it does not`() {
        // Ink covers y in [-1, 1] along x in [0, 100].
        val stroke = HitTestFixtures.line(1, WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0), widthWorld = 2.0)
        val (_, hits) = hitTesterFor(stroke)

        assertEquals(listOf(stroke.id), hits.elementsAlong(WorldPoint(50.0, -20.0), WorldPoint(50.0, 20.0), radius = 0.5))
        assertEquals(listOf(stroke.id), hits.elementsAlong(WorldPoint(50.0, 3.0), WorldPoint(60.0, 3.0), radius = 2.5))
        assertTrue(hits.elementsAlong(WorldPoint(50.0, 3.0), WorldPoint(60.0, 3.0), radius = 1.5).isEmpty())
        // Inside the bounding box but beyond the end cap.
        assertTrue(hits.elementsAlong(WorldPoint(104.0, -1.0), WorldPoint(104.0, 1.0), radius = 1.0).isEmpty())
    }

    @Test
    fun `strokes in deep frames are tested in their own units`() {
        // Drawn at 1000x: a 0.01-unit-long stroke, 0.002 world units wide.
        val tiny = HitTestFixtures.line(1, WorldPoint(5.0, 5.0), WorldPoint(5.01, 5.0), widthWorld = 0.002, level = 3)
        val (_, hits) = hitTesterFor(tiny)

        assertEquals(listOf(tiny.id), hits.elementsAlong(WorldPoint(5.005, 4.99), WorldPoint(5.005, 5.01), radius = 0.0001))
        assertTrue(hits.elementsAlong(WorldPoint(5.005, 5.003), WorldPoint(5.006, 5.003), radius = 0.001).isEmpty())
    }

    @Test
    fun `rectangles and ellipses are hit on their outline, not their empty inside`() {
        val rectangle = HitTestFixtures.shape(1, ShapeKind.RECTANGLE, WorldRect(0.0, 0.0, 100.0, 60.0))
        val ellipse = HitTestFixtures.shape(2, ShapeKind.ELLIPSE, WorldRect(200.0, 0.0, 300.0, 100.0))
        val (_, hits) = hitTesterFor(rectangle, ellipse)

        assertTrue(hits.elementsAlong(WorldPoint(40.0, 20.0), WorldPoint(60.0, 40.0), radius = 2.0).isEmpty())
        assertEquals(listOf(rectangle.id), hits.elementsAlong(WorldPoint(40.0, 20.0), WorldPoint(40.0, 70.0), radius = 2.0))
        // Corner of the ellipse's box is outside the curve.
        assertTrue(hits.elementsAlong(WorldPoint(203.0, 3.0), WorldPoint(205.0, 5.0), radius = 1.0).isEmpty())
        assertTrue(hits.elementsAlong(WorldPoint(240.0, 40.0), WorldPoint(260.0, 60.0), radius = 1.0).isEmpty())
        assertEquals(listOf(ellipse.id), hits.elementsAlong(WorldPoint(250.0, -5.0), WorldPoint(250.0, 5.0), radius = 1.0))
    }

    @Test
    fun `lines and arrows are hit along their segment`() {
        val line = HitTestFixtures.shape(1, ShapeKind.LINE, WorldRect(0.0, 0.0, 100.0, 100.0))
        val (_, hits) = hitTesterFor(line)

        assertEquals(listOf(line.id), hits.elementsAlong(WorldPoint(40.0, 60.0), WorldPoint(60.0, 40.0), radius = 1.0))
        assertTrue(hits.elementsAlong(WorldPoint(80.0, 10.0), WorldPoint(90.0, 20.0), radius = 1.0).isEmpty())
    }

    @Test
    fun `an arrow is hit on its head, not only its shaft`() {
        // Shaft along y = 0 to x = 100, 2 units wide; the head strokes spread about 3.8 units off it.
        val arrow = HitTestFixtures.shape(1, ShapeKind.ARROW, WorldRect(0.0, 0.0, 100.0, 0.0))
            .let { it.copy(bounds = WorldRect(-1.0, -5.0, 101.0, 5.0)) }
        val (_, hits) = hitTesterFor(arrow)

        // At x = 96 the upper head stroke passes y ≈ 2.2; the shaft's ink ends at y = 1.
        assertEquals(listOf(arrow.id), hits.elementsAlong(WorldPoint(96.0, 2.0), WorldPoint(96.0, 2.4), radius = 0.05))
        assertTrue(hits.elementsAlong(WorldPoint(50.0, 2.0), WorldPoint(50.0, 2.4), radius = 0.05).isEmpty())
    }

    @Test
    fun `elementAt returns the top-most hit within the screen tolerance`() {
        val below = HitTestFixtures.line(1, WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0), z = "a1")
        val above = HitTestFixtures.line(2, WorldPoint(50.0, -50.0), WorldPoint(50.0, 50.0), z = "a2")
        val (_, hits) = hitTesterFor(below, above)
        val camera = Camera(WorldPoint(0.0, 0.0), zoom = 10.0)

        assertEquals(above.id, hits.elementAt(WorldPoint(50.0, 0.0), tolerancePx = 4f, camera = camera))
        assertEquals(below.id, hits.elementAt(WorldPoint(20.0, 1.5), tolerancePx = 6f, camera = camera))
        // 6 px at 10x is 0.6 world units; the ink edge is 1.0 away from y = 2.0.
        assertNull(hits.elementAt(WorldPoint(20.0, 2.0), tolerancePx = 6f, camera = camera))
    }

    @Test
    fun `an image is hit anywhere inside it`() {
        val image = HitTestFixtures.image(1, WorldRect(0.0, 0.0, 100.0, 100.0))
        val (_, hits) = hitTesterFor(image)

        assertEquals(image.id, hits.elementAt(WorldPoint(50.0, 50.0), tolerancePx = 1f, camera = Camera(WorldPoint(0.0, 0.0), 1.0)))
    }

    @Test
    fun `elementsIn returns only elements entirely inside the rectangle, back to front`() {
        val inside = HitTestFixtures.line(1, WorldPoint(10.0, 10.0), WorldPoint(20.0, 20.0), z = "a2")
        val alsoInside = HitTestFixtures.line(2, WorldPoint(30.0, 30.0), WorldPoint(40.0, 40.0), z = "a1")
        val straddling = HitTestFixtures.line(3, WorldPoint(90.0, 90.0), WorldPoint(150.0, 150.0), z = "a3")
        val (_, hits) = hitTesterFor(inside, alsoInside, straddling)

        assertEquals(listOf(alsoInside.id, inside.id), hits.elementsIn(WorldRect(0.0, 0.0, 100.0, 100.0)))
    }

    @Test
    fun `the tester follows the sheet as elements are added and deleted`() {
        val first = HitTestFixtures.line(1, WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0))
        val (sheet, hits) = hitTesterFor(first)
        val second = HitTestFixtures.line(2, WorldPoint(0.0, 10.0), WorldPoint(100.0, 10.0), z = "a1")
        val across = Pair(WorldPoint(50.0, -5.0), WorldPoint(50.0, 15.0))
        assertEquals(listOf(first.id), hits.elementsAlong(across.first, across.second, 0.5))

        sheet.transact("add") { add(second) }
        assertEquals(setOf(first.id, second.id), hits.elementsAlong(across.first, across.second, 0.5).toSet())

        sheet.transact("delete") { delete(idOf(1)) }
        assertEquals(listOf(second.id), hits.elementsAlong(across.first, across.second, 0.5))
    }
}
