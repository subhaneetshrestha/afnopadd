package io.github.subhaneetshrestha.afnopadd.tools

import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasMath
import io.github.subhaneetshrestha.afnopadd.canvas.ScreenPoint
import io.github.subhaneetshrestha.afnopadd.canvas.Viewport
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.tools.commands.ZoomCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomCommandsTest {

    private val viewport = Viewport(800, 600)
    private val camera = CameraController(Camera(WorldPoint(100.0, -50.0), zoom = 2.0))
    private var content: WorldRect? = null
    private val animatedTo = mutableListOf<Camera>()
    private val commands = CommandRegistry().apply {
        ZoomCommands(camera, { viewport }, { content }, animateTo = { animatedTo += it; camera.set(it) }).all.forEach(::register)
    }

    @Test
    fun `zoom in and out step by one and a half around the viewport centre`() {
        val centreWorld = CanvasMath.screenToWorld(camera.camera.value, viewport, ScreenPoint(400.0, 300.0))

        commands.invoke(ZoomCommands.ZOOM_IN)
        assertEquals(3.0, camera.camera.value.zoom, 1e-9)
        assertEquals(centreWorld, CanvasMath.screenToWorld(camera.camera.value, viewport, ScreenPoint(400.0, 300.0)))

        commands.invoke(ZoomCommands.ZOOM_OUT)
        commands.invoke(ZoomCommands.ZOOM_OUT)
        assertEquals(2.0 / 1.5, camera.camera.value.zoom, 1e-9)
        assertEquals(WorldPoint(100.0, -50.0), camera.camera.value.center)
        assertEquals("every step animates", 3, animatedTo.size)
    }

    @Test
    fun `a step taken while the previous one animates builds on its target`() {
        // An animation that never gets anywhere: the camera stays put while it "runs".
        val targets = mutableListOf<Camera>()
        val animating = CommandRegistry().apply {
            ZoomCommands(camera, { viewport }, { content }, animateTo = { targets += it }, animating = { true }).all.forEach(::register)
        }

        animating.invoke(ZoomCommands.ZOOM_IN)
        animating.invoke(ZoomCommands.ZOOM_IN)

        assertEquals(listOf(3.0, 4.5), targets.map { it.zoom })
    }

    @Test
    fun `zoom steps stop at the zoom limits`() {
        camera.set(Camera(WorldPoint(0.0, 0.0), Camera.MAX_ZOOM))
        commands.invoke(ZoomCommands.ZOOM_IN)
        assertEquals(Camera.MAX_ZOOM, camera.camera.value.zoom, 0.0)
    }

    @Test
    fun `one hundred percent returns to 1x and keeps the centre`() {
        commands.invoke(ZoomCommands.ZOOM_100)
        assertEquals(Camera(WorldPoint(100.0, -50.0), 1.0), camera.camera.value)
    }

    @Test
    fun `fit frames all content inside the padding`() {
        content = WorldRect(-1_000.0, 0.0, 1_000.0, 100.0)

        commands.invoke(ZoomCommands.ZOOM_FIT)

        val fitted = camera.camera.value
        assertEquals(WorldPoint(0.0, 50.0), fitted.center)
        val left = CanvasMath.worldToScreen(fitted, viewport, WorldPoint(-1_000.0, 0.0))
        val right = CanvasMath.worldToScreen(fitted, viewport, WorldPoint(1_000.0, 100.0))
        assertEquals(ZoomCommands.FIT_PADDING_PX, left.x, 1e-6)
        assertEquals(800.0 - ZoomCommands.FIT_PADDING_PX, right.x, 1e-6)
        assertTrue(left.y > 0 && right.y < 600)
    }

    @Test
    fun `fit on an empty sheet returns to the origin at 1x`() {
        commands.invoke(ZoomCommands.ZOOM_FIT)
        assertEquals(Camera.DEFAULT, camera.camera.value)
    }

    @Test
    fun `content bounds cover every element, and are null for none`() {
        assertEquals(null, ZoomCommands.boundsOf(emptyList()))
        assertEquals(
            WorldRect(-5.0, 0.0, 30.0, 40.0),
            ZoomCommands.boundsOf(listOf(WorldRect(0.0, 0.0, 30.0, 10.0), WorldRect(-5.0, 20.0, 1.0, 40.0))),
        )
    }

    @Test
    fun `the readout shows percent up to ten times, then a compact factor`() {
        assertEquals("100%", ZoomCommands.readout(1.0))
        assertEquals("150%", ZoomCommands.readout(1.5))
        assertEquals("1%", ZoomCommands.readout(0.012))
        assertEquals("0.1%", ZoomCommands.readout(0.001))
        assertEquals("1000%", ZoomCommands.readout(10.0))
        assertEquals("25×", ZoomCommands.readout(25.4))
        assertEquals("1.5K×", ZoomCommands.readout(1_520.0))
        assertEquals("100K×", ZoomCommands.readout(100_000.0))
    }
}
