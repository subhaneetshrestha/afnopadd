package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class CameraControllerTest {

    private val viewport = Viewport(widthPx = 800, heightPx = 600)

    @Test
    fun `panning moves the centre against the drag by drag over zoom`() {
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 2.0))

        controller.panBy(dxPx = 100.0, dyPx = -30.0)

        assertEquals(WorldPoint(-50.0, 15.0), controller.camera.value.center)
        assertEquals(2.0, controller.camera.value.zoom, 0.0)
    }

    @Test
    fun `zooming around a pivot keeps the pivot world point fixed`() {
        val random = Random(5)
        repeat(1_000) {
            val controller = CameraController(
                Camera(WorldPoint(random.nextDouble(-1e3, 1e3), random.nextDouble(-1e3, 1e3)), zoom = random.nextDouble(0.01, 1_000.0)),
            )
            val pivot = ScreenPoint(random.nextDouble(0.0, 800.0), random.nextDouble(0.0, 600.0))
            val before = CanvasMath.screenToWorld(controller.camera.value, viewport, pivot)

            controller.zoomBy(factor = random.nextDouble(0.25, 4.0), pivot = pivot, viewport = viewport)

            val after = CanvasMath.screenToWorld(controller.camera.value, viewport, pivot)
            assertTrue(abs(after.x - before.x) < 1e-9)
            assertTrue(abs(after.y - before.y) < 1e-9)
        }
    }

    @Test
    fun `zooming around a pivot at far coordinates stays within a micro unit`() {
        val controller = CameraController(Camera(WorldPoint(987_654.321, -123_456.789), zoom = 50_000.0))
        val pivot = ScreenPoint(123.0, 456.0)
        val before = CanvasMath.screenToWorld(controller.camera.value, viewport, pivot)

        controller.zoomBy(factor = 1.5, pivot = pivot, viewport = viewport)

        val after = CanvasMath.screenToWorld(controller.camera.value, viewport, pivot)
        assertTrue(abs(after.x - before.x) < 1e-6)
        assertTrue(abs(after.y - before.y) < 1e-6)
    }

    @Test
    fun `zoom is clamped at both ends and the pivot still holds`() {
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 90_000.0))
        val pivot = ScreenPoint(100.0, 100.0)
        val before = CanvasMath.screenToWorld(controller.camera.value, viewport, pivot)

        controller.zoomBy(factor = 10.0, pivot = pivot, viewport = viewport)
        assertEquals(Camera.MAX_ZOOM, controller.camera.value.zoom, 0.0)
        val after = CanvasMath.screenToWorld(controller.camera.value, viewport, pivot)
        assertTrue(abs(after.x - before.x) < 1e-9 && abs(after.y - before.y) < 1e-9)

        controller.zoomBy(factor = 1e-12, pivot = pivot, viewport = viewport)
        assertEquals(Camera.MIN_ZOOM, controller.camera.value.zoom, 0.0)
    }

    @Test
    fun `set clamps the zoom`() {
        val controller = CameraController()

        controller.set(Camera(WorldPoint(1.0, 1.0), zoom = 1e9))

        assertEquals(Camera.MAX_ZOOM, controller.camera.value.zoom, 0.0)
        assertEquals(WorldPoint(1.0, 1.0), controller.camera.value.center)
    }

    @Test
    fun `fit frames the bounds inside the padded viewport`() {
        val controller = CameraController()

        controller.fit(WorldRect(0.0, 0.0, 100.0, 50.0), viewport, paddingPx = 20.0)

        assertEquals(7.6, controller.camera.value.zoom, 1e-12)
        assertEquals(WorldPoint(50.0, 25.0), controller.camera.value.center)
    }

    @Test
    fun `fit of degenerate bounds centres on them at the maximum zoom`() {
        val controller = CameraController()

        controller.fit(WorldRect(3.0, 4.0, 3.0, 4.0), viewport, paddingPx = 0.0)

        assertEquals(Camera.MAX_ZOOM, controller.camera.value.zoom, 0.0)
        assertEquals(WorldPoint(3.0, 4.0), controller.camera.value.center)
    }
}
