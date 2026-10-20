package io.github.subhaneetshrestha.afnopadd.tools

import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasPointerEvent
import io.github.subhaneetshrestha.afnopadd.canvas.HitTestFixtures
import io.github.subhaneetshrestha.afnopadd.canvas.PointerKind
import io.github.subhaneetshrestha.afnopadd.canvas.PointerPhase
import io.github.subhaneetshrestha.afnopadd.canvas.ScreenPoint
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import io.github.subhaneetshrestha.afnopadd.tools.eraser.EraserTool
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EraserToolTest {

    // Three vertical strokes at x = 0, 40, 80 (ink 2 units wide), an image between x = 110 and 130,
    // a rectangle outline from x = 200 to 300, and a horizontal stroke well below them all.
    private val strokes = listOf(0.0, 40.0, 80.0).mapIndexed { i, x ->
        HitTestFixtures.line(i + 1, WorldPoint(x, 0.0), WorldPoint(x, 100.0), z = "a${i + 1}")
    }
    private val image = HitTestFixtures.image(4, WorldRect(110.0, 0.0, 130.0, 100.0), z = "a4")
    private val rectangle = HitTestFixtures.shape(5, ShapeKind.RECTANGLE, WorldRect(200.0, 0.0, 300.0, 100.0), z = "a5")
    private val untouched = HitTestFixtures.line(6, WorldPoint(0.0, 200.0), WorldPoint(100.0, 200.0), z = "a6")
    private var transactions = 0
    private val sheet = SheetImpl(
        meta = SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Erase", 0L, 0L),
        initialElements = strokes + image + rectangle + untouched,
        writer = { transactions++ },
    )
    private val camera = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 1.0))
    private val controller = EraserTool().createController(ToolContext(sheet, camera, ToolStyles(BuiltInPresets.defaultStyles)))

    private fun event(phase: PointerPhase, x: Double, y: Double, time: Long = 0L) = CanvasPointerEvent(
        pointerId = 1L,
        kind = PointerKind.FINGER,
        phase = phase,
        screen = ScreenPoint(x, y),
        world = WorldPoint(x, y),
        pressure = 1f,
        timeMillis = time,
    )

    private fun drag(vararg points: Pair<Double, Double>, finish: PointerPhase = PointerPhase.UP) {
        points.forEachIndexed { i, (x, y) ->
            val phase = when (i) {
                0 -> PointerPhase.DOWN
                points.lastIndex -> finish
                else -> PointerPhase.MOVE
            }
            controller.onPointer(event(phase, x, y, time = i * 8L))
        }
    }

    @Test
    fun `dragging across three strokes and an image erases the strokes in one transaction`() {
        drag(-20.0 to 50.0, 40.0 to 50.0, 140.0 to 50.0)

        val remaining = sheet.snapshot.value.elements.keys
        assertEquals(1, transactions)
        strokes.forEach { assertFalse(it.id in remaining) }
        assertTrue(image.id in remaining)
        assertTrue(untouched.id in remaining)

        sheet.undo()
        assertEquals(6, sheet.snapshot.value.elements.size)
    }

    @Test
    fun `touched elements are hidden while dragging and only removed on release`() {
        controller.onPointer(event(PointerPhase.DOWN, -20.0, 50.0))
        controller.onPointer(event(PointerPhase.MOVE, 10.0, 50.0, time = 8L))

        assertEquals(setOf(strokes[0].id), controller.hiddenElements.value)
        assertEquals(6, sheet.snapshot.value.elements.size)
        assertEquals(0, transactions)

        controller.onPointer(event(PointerPhase.UP, 10.0, 50.0, time = 16L))
        assertEquals(emptySet<Any>(), controller.hiddenElements.value)
        assertEquals(5, sheet.snapshot.value.elements.size)
    }

    @Test
    fun `a cancelled drag erases nothing and shows everything again`() {
        drag(-20.0 to 50.0, 60.0 to 50.0, finish = PointerPhase.CANCEL)

        assertEquals(0, transactions)
        assertEquals(6, sheet.snapshot.value.elements.size)
        assertTrue(controller.hiddenElements.value.isEmpty())
    }

    @Test
    fun `shapes are erased by touching their outline only`() {
        drag(240.0 to 40.0, 260.0 to 60.0)
        assertEquals(0, transactions)

        drag(240.0 to 50.0, 240.0 to 110.0)
        assertFalse(rectangle.id in sheet.snapshot.value.elements)
    }

    @Test
    fun `the eraser radius is fixed in screen pixels`() {
        // The horizontal stroke's ink spans y in [199, 201]; at 1x the 12 px radius reaches 12 world units.
        drag(40.0 to 216.0, 60.0 to 216.0)
        assertEquals(0, transactions)

        // At 0.25x the same 12 px radius reaches 48 world units.
        camera.set(Camera(WorldPoint(0.0, 0.0), zoom = 0.25))
        drag(40.0 to 240.0, 60.0 to 240.0)
        assertFalse(untouched.id in sheet.snapshot.value.elements)
    }

    @Test
    fun `a tap erases what is under it`() {
        drag(40.0 to 50.0, 40.0 to 50.0)

        assertEquals(listOf(strokes[1].id).toSet(), (strokes.map { it.id }.toSet() - sheet.snapshot.value.elements.keys))
    }
}
