package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class CanvasMathTest {

    private val viewport = Viewport(widthPx = 800, heightPx = 600)

    @Test
    fun `the viewport centre maps to the camera centre`() {
        val camera = Camera(center = WorldPoint(123.5, -77.25), zoom = 3.0)

        val screen = CanvasMath.worldToScreen(camera, viewport, camera.center)

        assertEquals(400.0, screen.x, 1e-9)
        assertEquals(300.0, screen.y, 1e-9)
    }

    @Test
    fun `world to screen scales by zoom around the centre`() {
        val camera = Camera(center = WorldPoint(0.0, 0.0), zoom = 2.0)

        val screen = CanvasMath.worldToScreen(camera, viewport, WorldPoint(10.0, -5.0))

        assertEquals(420.0, screen.x, 1e-9)
        assertEquals(290.0, screen.y, 1e-9)
    }

    @Test
    fun `screen to world inverts world to screen`() {
        val camera = Camera(center = WorldPoint(-40.0, 12.0), zoom = 0.5)

        val world = CanvasMath.screenToWorld(camera, viewport, ScreenPoint(0.0, 0.0))

        assertEquals(-840.0, world.x, 1e-9)
        assertEquals(-588.0, world.y, 1e-9)
    }

    @Test
    fun `round trips stay within a micro unit at extreme zoom and far coordinates`() {
        val random = Random(7)
        repeat(10_000) {
            val camera = Camera(
                center = WorldPoint(random.nextDouble(-1e6, 1e6), random.nextDouble(-1e6, 1e6)),
                zoom = Camera.MAX_ZOOM,
            )
            val screen = ScreenPoint(random.nextDouble(0.0, 800.0), random.nextDouble(0.0, 600.0))
            val world = CanvasMath.screenToWorld(camera, viewport, screen)

            val back = CanvasMath.worldToScreen(camera, viewport, world)
            val worldAgain = CanvasMath.screenToWorld(camera, viewport, back)

            assertTrue(abs(back.x - screen.x) < 1e-3 && abs(back.y - screen.y) < 1e-3)
            assertTrue(abs(worldAgain.x - world.x) < 1e-6 && abs(worldAgain.y - world.y) < 1e-6)
        }
    }

    @Test
    fun `a frame transform agrees with world to screen for local offsets`() {
        val camera = Camera(center = WorldPoint(5.0, 5.0), zoom = 40.0)
        val anchor = WorldPoint(5.25, 4.75)
        val frameLevel = 2 // one local unit is 0.01 world units

        val transform = CanvasMath.frameToScreen(camera, viewport, anchor, frameLevel)
        val viaFrame = transform.apply(LocalPoint(100f, -50f))
        val viaWorld = CanvasMath.worldToScreen(camera, viewport, WorldPoint(anchor.x + 1.0, anchor.y - 0.5))

        assertEquals(viaWorld.x, viaFrame.x, 1e-6)
        assertEquals(viaWorld.y, viaFrame.y, 1e-6)
        assertEquals(0.4, transform.scale, 1e-12)
    }

    @Test
    fun `frame translations stay viewport sized for anchors inside the view`() {
        val random = Random(3)
        repeat(1_000) {
            val center = WorldPoint(random.nextDouble(-1e6, 1e6), random.nextDouble(-1e6, 1e6))
            val camera = Camera(center, zoom = Camera.MAX_ZOOM)
            val anchor = CanvasMath.screenToWorld(
                camera,
                viewport,
                ScreenPoint(random.nextDouble(0.0, 800.0), random.nextDouble(0.0, 600.0)),
            )

            val transform = CanvasMath.frameToScreen(camera, viewport, anchor, frameLevel = 5)

            assertTrue(abs(transform.translateX) <= 800.0 + 1e-6)
            assertTrue(abs(transform.translateY) <= 600.0 + 1e-6)
            val asFloat = transform.asFloat()
            assertTrue(abs(asFloat.translateX - transform.translateX) < 1e-3)
        }
    }

    @Test
    fun `interpolation moves the centre linearly and the zoom geometrically`() {
        val from = Camera(WorldPoint(0.0, 0.0), zoom = 1.0)
        val to = Camera(WorldPoint(100.0, -100.0), zoom = 100.0)

        val halfway = CanvasMath.interpolate(from, to, fraction = 0.5)

        assertEquals(50.0, halfway.center.x, 1e-9)
        assertEquals(-50.0, halfway.center.y, 1e-9)
        assertEquals(sqrt(100.0), halfway.zoom, 1e-9)
        assertEquals(from, CanvasMath.interpolate(from, to, 0.0))
        assertEquals(to, CanvasMath.interpolate(from, to, 1.0))
    }
}
