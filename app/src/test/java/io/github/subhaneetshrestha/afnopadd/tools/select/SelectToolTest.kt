package io.github.subhaneetshrestha.afnopadd.tools.select

import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasPointerEvent
import io.github.subhaneetshrestha.afnopadd.canvas.HitTestFixtures
import io.github.subhaneetshrestha.afnopadd.canvas.PointerKind
import io.github.subhaneetshrestha.afnopadd.canvas.PointerPhase
import io.github.subhaneetshrestha.afnopadd.canvas.ScreenPoint
import io.github.subhaneetshrestha.afnopadd.canvas.SelectionHandles
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import io.github.subhaneetshrestha.afnopadd.tools.ToolCapability
import io.github.subhaneetshrestha.afnopadd.tools.ToolContext
import io.github.subhaneetshrestha.afnopadd.tools.ToolStyles
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInPresets
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectToolTest {

    // A vertical stroke at x = 0, an image from x = 40 to 60, a rectangle outline from x = 100 to 200,
    // and a second stroke at x = 50 drawn over the image (higher z).
    private val stroke = HitTestFixtures.line(1, WorldPoint(0.0, 0.0), WorldPoint(0.0, 100.0), z = "a1")
    private val image = HitTestFixtures.image(2, WorldRect(40.0, 0.0, 60.0, 100.0), z = "a2")
    private val rectangle = HitTestFixtures.shape(3, ShapeKind.RECTANGLE, WorldRect(100.0, 0.0, 200.0, 100.0), z = "a3")
    private val overImage = HitTestFixtures.line(4, WorldPoint(50.0, 40.0), WorldPoint(50.0, 60.0), z = "a4")
    private var transactions = 0
    private val sheet = SheetImpl(
        meta = SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Select", 0L, 0L),
        initialElements = listOf(stroke, image, rectangle, overImage),
        writer = { transactions++ },
    )
    private val camera = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 1.0))
    private val context = ToolContext(sheet, camera, ToolStyles(BuiltInPresets.defaultStyles), clock = { 42L })
    private val selection = context.selection
    private val controller = SelectTool().createController(context)

    /** World and screen coincide at 1x around the origin, which keeps the scripts readable. */
    private fun event(phase: PointerPhase, x: Double, y: Double, time: Long) =
        CanvasPointerEvent(1L, PointerKind.FINGER, phase, ScreenPoint(x, y), WorldPoint(x, y), 1f, time)

    private fun tap(x: Double, y: Double, holdMillis: Long = 80L) {
        controller.onPointer(event(PointerPhase.DOWN, x, y, 0L))
        controller.onPointer(event(PointerPhase.UP, x, y, holdMillis))
    }

    private fun drag(from: Pair<Double, Double>, to: Pair<Double, Double>, finish: PointerPhase = PointerPhase.UP) {
        controller.onPointer(event(PointerPhase.DOWN, from.first, from.second, 0L))
        for (i in 1..4) {
            val f = i / 4.0
            val phase = if (i == 4) finish else PointerPhase.MOVE
            controller.onPointer(event(phase, from.first + (to.first - from.first) * f, from.second + (to.second - from.second) * f, i * 16L))
        }
    }

    private fun anchors() = sheet.snapshot.value.elements.mapValues { it.value.anchor }

    @Test
    fun `the select tool has a stable id and the select capability`() {
        assertEquals(BuiltInTools.SELECT, SelectTool().id)
        assertEquals(setOf(ToolCapability.SELECT), SelectTool().capabilities)
    }

    @Test
    fun `a tap selects the topmost element under it, and a tap on nothing clears`() {
        tap(0.0, 50.0)
        assertEquals(setOf(stroke.id), selection.selected.value)

        tap(50.0, 50.0) // the stroke drawn over the image wins
        assertEquals(setOf(overImage.id), selection.selected.value)

        tap(45.0, 10.0) // only the image is here
        assertEquals(setOf(image.id), selection.selected.value)

        tap(150.0, 50.0) // inside the unfilled rectangle: nothing
        assertEquals(emptySet<Any>(), selection.selected.value)
        assertEquals(0, transactions)
    }

    @Test
    fun `a long press adds to the selection, and on a selected element removes it`() {
        tap(0.0, 50.0)
        tap(100.0, 50.0, holdMillis = 600L)
        assertEquals(setOf(stroke.id, rectangle.id), selection.selected.value)

        tap(0.0, 50.0, holdMillis = 600L)
        assertEquals(setOf(rectangle.id), selection.selected.value)
    }

    @Test
    fun `a drag on empty space selects what the marquee fully encloses`() {
        drag(-10.0 to -10.0, 70.0 to 110.0)

        // The stroke, the image and the short stroke lie inside; the rectangle only partly.
        assertEquals(setOf(stroke.id, image.id, overImage.id), selection.selected.value)
        assertEquals(null, controller.marquee.value)
        assertEquals(0, transactions)
    }

    @Test
    fun `the marquee is shown while dragging`() {
        controller.onPointer(event(PointerPhase.DOWN, 300.0, 300.0, 0L))
        controller.onPointer(event(PointerPhase.MOVE, 250.0, 260.0, 16L))

        assertEquals(WorldRect(250.0, 260.0, 300.0, 300.0), controller.marquee.value)
    }

    @Test
    fun `moving a stroke, a shape and an image changes all three anchors in one undoable transaction`() {
        selection.set(setOf(stroke.id, image.id, rectangle.id))
        val before = anchors()

        drag(100.0 to 50.0, 130.0 to 70.0) // grab the rectangle's left edge

        assertEquals(1, transactions)
        for (id in listOf(stroke.id, image.id, rectangle.id)) {
            assertEquals(WorldPoint(before.getValue(id).x + 30.0, before.getValue(id).y + 20.0), anchors().getValue(id))
            val moved = sheet.snapshot.value.elements.getValue(id)
            assertEquals(2, moved.version)
            assertEquals(42L, moved.updatedAt)
        }
        assertEquals(before.getValue(overImage.id), anchors().getValue(overImage.id))
        // The fixture's bounds include half its 2-unit line width.
        assertEquals(WorldRect(129.0, 19.0, 231.0, 121.0), sheet.snapshot.value.elements.getValue(rectangle.id).bounds)
        assertEquals(setOf(stroke.id, image.id, rectangle.id), selection.selected.value)

        sheet.undo()
        assertEquals(before, anchors())
    }

    @Test
    fun `dragging an unselected element selects it and moves only it`() {
        selection.set(setOf(image.id))

        drag(0.0 to 50.0, 20.0 to 50.0)

        assertEquals(setOf(stroke.id), selection.selected.value)
        assertEquals(WorldPoint(20.0, 0.0), anchors().getValue(stroke.id))
        assertEquals(image.anchor, anchors().getValue(image.id))
    }

    @Test
    fun `while moving, the originals are hidden and moved copies previewed, and nothing is committed`() {
        selection.set(setOf(stroke.id))
        controller.onPointer(event(PointerPhase.DOWN, 0.0, 50.0, 0L))
        controller.onPointer(event(PointerPhase.MOVE, 25.0, 50.0, 16L))

        assertEquals(setOf(stroke.id), controller.hiddenElements.value)
        assertEquals(listOf(WorldPoint(25.0, 0.0)), controller.previewElements.value.map { it.anchor })
        assertEquals(0, transactions)
    }

    @Test
    fun `a cancelled move commits nothing and clears the preview`() {
        selection.set(setOf(stroke.id))

        drag(0.0 to 50.0, 30.0 to 50.0, finish = PointerPhase.CANCEL)

        assertEquals(0, transactions)
        assertEquals(stroke.anchor, anchors().getValue(stroke.id))
        assertTrue(controller.previewElements.value.isEmpty())
        assertTrue(controller.hiddenElements.value.isEmpty())
        assertEquals(setOf(stroke.id), selection.selected.value)
    }

    @Test
    fun `a small wobble during a tap is still a tap`() {
        controller.onPointer(event(PointerPhase.DOWN, 0.0, 50.0, 0L))
        controller.onPointer(event(PointerPhase.MOVE, 3.0, 52.0, 16L))
        controller.onPointer(event(PointerPhase.UP, 3.0, 52.0, 32L))

        assertEquals(setOf(stroke.id), selection.selected.value)
        assertEquals(0, transactions)
    }

    @Test
    fun `handles sit a fixed number of screen pixels off the selection at every zoom`() {
        val bounds = WorldRect(0.0, 0.0, 100.0, 50.0)
        for (zoom in listOf(0.01, 1.0, 50.0, 20_000.0)) {
            val scale = SelectionHandles.position(SelectionHandles.Handle.SCALE, bounds, zoom, density = 2f)
            val rotate = SelectionHandles.position(SelectionHandles.Handle.ROTATE, bounds, zoom, density = 2f)
            assertEquals("zoom $zoom", SelectionHandles.PAD_DP * 2.0, (scale.x - bounds.right) * zoom, 1e-6)
            assertEquals("zoom $zoom", SelectionHandles.PAD_DP * 2.0, (scale.y - bounds.bottom) * zoom, 1e-6)
            assertEquals("zoom $zoom", (SelectionHandles.PAD_DP + SelectionHandles.ROTATE_OFFSET_DP) * 2.0, (bounds.top - rotate.y) * zoom, 1e-6)
            assertEquals(50.0, rotate.x, 1e-9)
        }
    }

    @Test
    fun `dragging the scale handle away from the centre to twice the distance doubles the selection`() {
        selection.set(setOf(rectangle.id))
        // Bounds (99, -1, 201, 101): centre (150, 50), scale handle 6 px beyond the corner at (207, 107).
        drag(207.0 to 107.0, 264.0 to 164.0)

        assertEquals(1, transactions)
        val scaled = sheet.snapshot.value.elements.getValue(rectangle.id) as io.github.subhaneetshrestha.afnopadd.document.ShapeElement
        assertEquals(200f, scaled.geometry.end.x, 1e-3f)
        assertEquals(4f, scaled.style.strokeWidth, 1e-4f)
        assertEquals(2, scaled.version)
        assertEquals(150.0, (scaled.bounds.left + scaled.bounds.right) / 2, 1e-6)
        sheet.undo()
        assertEquals(rectangle, sheet.snapshot.value.elements.getValue(rectangle.id))
    }

    @Test
    fun `dragging the rotate handle a quarter turn rotates the selection 90 degrees`() {
        selection.set(setOf(rectangle.id))
        // The rotate handle sits 34 px above the top-centre (150, -1), at (150, -35); the centre is (150, 50).
        drag(150.0 to -35.0, 235.0 to 50.0)

        assertEquals(1, transactions)
        val turned = sheet.snapshot.value.elements.getValue(rectangle.id) as io.github.subhaneetshrestha.afnopadd.document.ShapeElement
        assertEquals(90f, turned.rotationDeg, 1e-3f)
        assertEquals(setOf(rectangle.id), selection.selected.value)
    }

    @Test
    fun `a rotation near a multiple of 15 degrees snaps to it`() {
        selection.set(setOf(rectangle.id))
        // 88 degrees: within the snap of 90.
        val angle = Math.toRadians(88.0 - 90.0)
        drag(150.0 to -35.0, (150.0 + 85 * kotlin.math.cos(angle)) to (50.0 + 85 * kotlin.math.sin(angle)))

        val turned = sheet.snapshot.value.elements.getValue(rectangle.id) as io.github.subhaneetshrestha.afnopadd.document.ShapeElement
        assertEquals(90f, turned.rotationDeg, 1e-3f)
    }

    @Test
    fun `a transform is previewed while dragging and commits nothing until the pointer lifts`() {
        selection.set(setOf(rectangle.id))
        controller.onPointer(event(PointerPhase.DOWN, 207.0, 107.0, 0L))
        controller.onPointer(event(PointerPhase.MOVE, 264.0, 164.0, 16L))

        assertEquals(setOf(rectangle.id), controller.hiddenElements.value)
        assertEquals(1, controller.previewElements.value.size)
        assertEquals(0, transactions)
        controller.onPointer(event(PointerPhase.CANCEL, 264.0, 164.0, 32L))
        assertEquals(0, transactions)
        assertEquals(rectangle, sheet.snapshot.value.elements.getValue(rectangle.id))
    }

    @Test
    fun `leaving the select tool clears the selection`() {
        selection.set(setOf(stroke.id))

        controller.onDeactivated()

        assertTrue(selection.selected.value.isEmpty())
    }
}
