package io.github.subhaneetshrestha.afnopadd.tools.select

import io.github.subhaneetshrestha.afnopadd.canvas.HitTestFixtures
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.ShapeElement
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.hypot

class TransformsTest {

    private val stroke = HitTestFixtures.line(1, WorldPoint(0.0, 0.0), WorldPoint(100.0, 0.0), widthWorld = 2.0)
    private val rectangle = HitTestFixtures.shape(2, ShapeKind.RECTANGLE, WorldRect(100.0, 0.0, 140.0, 20.0))
    private val line = HitTestFixtures.shape(3, ShapeKind.LINE, WorldRect(0.0, 0.0, 100.0, 100.0))
    private val image = HitTestFixtures.image(4, WorldRect(0.0, 0.0, 40.0, 20.0))

    private fun distance(a: WorldPoint, b: WorldPoint) = hypot(a.x - b.x, a.y - b.y)

    private fun assertClose(expected: WorldPoint, actual: WorldPoint, tolerance: Double = 1e-6) {
        assertEquals("x of $actual", expected.x, actual.x, tolerance)
        assertEquals("y of $actual", expected.y, actual.y, tolerance)
    }

    private fun assertClose(expected: WorldRect, actual: WorldRect, tolerance: Double = 1e-4) {
        assertEquals("left of $actual", expected.left, actual.left, tolerance)
        assertEquals("top of $actual", expected.top, actual.top, tolerance)
        assertEquals("right of $actual", expected.right, actual.right, tolerance)
        assertEquals("bottom of $actual", expected.bottom, actual.bottom, tolerance)
    }

    @Test
    fun `scaling by 2x doubles the distances between anchors and every element's extent`() {
        val pivot = WorldPoint(50.0, 50.0)
        val elements = listOf<Element>(stroke, rectangle, image)

        val scaled = elements.map { Transforms.scaled(it, pivot, 2.0) }

        for (i in elements.indices) for (j in elements.indices) {
            assertEquals(2 * distance(elements[i].anchor, elements[j].anchor), distance(scaled[i].anchor, scaled[j].anchor), 1e-9)
        }
        val s = scaled[0] as StrokeElement
        assertEquals(200f, s.points.x(2), 1e-4f)
        assertEquals(4f, s.brush.size, 1e-6f)
        assertEquals(2 * stroke.brush.epsilon, s.brush.epsilon, 1e-9f)
        assertClose(WorldRect(-52.0, -52.0, 152.0, -48.0), s.bounds)
        val r = scaled[1] as ShapeElement
        assertEquals(LocalPoint(80f, 40f), r.geometry.end)
        assertEquals(4f, r.style.strokeWidth, 1e-6f)
        assertEquals(2 * rectangle.bounds.width, r.bounds.width, 1e-9)
        val img = scaled[2] as ImageElement
        assertEquals(80f, img.size.width, 1e-6f)
        assertClose(WorldRect(-50.0, -50.0, 30.0, -10.0), img.bounds)
    }

    @Test
    fun `the inverse scale restores every element`() {
        val pivot = WorldPoint(13.0, -7.0)
        for (element in listOf<Element>(stroke, rectangle, image, line)) {
            val restored = Transforms.scaled(Transforms.scaled(element, pivot, 2.0), pivot, 0.5)
            assertClose(element.anchor, restored.anchor)
            assertClose(element.bounds, restored.bounds)
        }
        val back = Transforms.scaled(Transforms.scaled(stroke, pivot, 2.0), pivot, 0.5) as StrokeElement
        assertEquals(100f, back.points.x(2), 1e-4f)
        assertEquals(stroke.brush.size, back.brush.size, 1e-6f)
    }

    @Test
    fun `rotating a stroke 90 degrees turns its points clockwise and its pen orientation with them`() {
        val turned = Transforms.rotated(stroke, WorldPoint(0.0, 0.0), 90.0) as StrokeElement

        assertClose(WorldPoint(0.0, 0.0), turned.anchor)
        for (i in 0 until turned.points.count) {
            assertEquals(0f, turned.points.x(i), 1e-4f)
            assertEquals(stroke.points.x(i), turned.points.y(i), 1e-4f)
            assertEquals((PI / 2).toFloat(), turned.points.orientationRad(i), 1e-6f)
        }
        assertClose(WorldRect(-1.0, -1.0, 1.0, 101.0), turned.bounds)
    }

    @Test
    fun `rotating a line turns its endpoints and keeps no rotation of its own`() {
        val turned = Transforms.rotated(line, WorldPoint(0.0, 0.0), 90.0) as ShapeElement

        assertEquals(0f, turned.rotationDeg, 0f)
        assertEquals(-100f, turned.geometry.end.x, 1e-4f)
        assertEquals(100f, turned.geometry.end.y, 1e-4f)
        assertClose(WorldRect(-101.0, -1.0, 1.0, 101.0), turned.bounds)
    }

    @Test
    fun `rotating a rectangle 90 degrees sets its rotation and moves its centre around the pivot`() {
        val turned = Transforms.rotated(rectangle, WorldPoint(0.0, 0.0), 90.0) as ShapeElement

        assertEquals(90f, turned.rotationDeg, 1e-6f)
        assertEquals(rectangle.geometry, turned.geometry)
        // The centre (120, 10) turns to (-10, 120); the anchor is the box's corner before turning.
        assertClose(WorldPoint(-30.0, 110.0), turned.anchor)
        // A 40 x 20 box upright is 20 x 40, plus its mitred corners.
        assertTrue(turned.bounds.left <= -21.0 && turned.bounds.right >= 1.0 && turned.bounds.top <= 99.0 && turned.bounds.bottom >= 141.0)
        assertTrue(turned.bounds.width < 24.0)
    }

    @Test
    fun `rotating an image 90 degrees sets its rotation about its centre`() {
        val turned = Transforms.rotated(image, WorldPoint(0.0, 0.0), 90.0) as ImageElement

        assertEquals(90f, turned.rotationDeg, 1e-6f)
        assertEquals(image.size, turned.size)
        // Centre (20, 10) turns to (-10, 20).
        assertClose(WorldPoint(-30.0, 10.0), turned.anchor)
        assertClose(WorldRect(-20.0, 0.0, 0.0, 40.0), turned.bounds)
    }

    @Test
    fun `four quarter turns come back to the start`() {
        val pivot = WorldPoint(33.0, 44.0)
        for (element in listOf<Element>(stroke, rectangle, image, line)) {
            var current = element
            repeat(4) { current = Transforms.rotated(current, pivot, 90.0) }
            assertClose(element.anchor, current.anchor, 1e-4)
            assertClose(element.bounds, current.bounds, 1e-3)
        }
    }
}
