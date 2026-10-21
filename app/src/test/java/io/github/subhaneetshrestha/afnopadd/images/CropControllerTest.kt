package io.github.subhaneetshrestha.afnopadd.images

import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasPointerEvent
import io.github.subhaneetshrestha.afnopadd.canvas.HitTestFixtures
import io.github.subhaneetshrestha.afnopadd.canvas.PointerKind
import io.github.subhaneetshrestha.afnopadd.canvas.PointerPhase
import io.github.subhaneetshrestha.afnopadd.canvas.ScreenPoint
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.LocalRect
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import io.github.subhaneetshrestha.afnopadd.images.crop.CropController
import io.github.subhaneetshrestha.afnopadd.tools.ToolContext
import io.github.subhaneetshrestha.afnopadd.tools.ToolStyles
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CropControllerTest {

    private val image = HitTestFixtures.image(1, WorldRect(0.0, 0.0, 100.0, 50.0))
    private var transactions = 0
    private val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Crop", 0L, 0L), listOf(image), writer = { transactions++ })
    private val context = ToolContext(sheet, CameraController(Camera(WorldPoint(0.0, 0.0), 1.0)), ToolStyles(BuiltInPresets.defaultStyles), density = 1f)
    private val controller = CropController(context, image.id)

    private fun event(phase: PointerPhase, x: Double, y: Double, time: Long) =
        CanvasPointerEvent(1L, PointerKind.FINGER, phase, ScreenPoint(x, y), WorldPoint(x, y), 1f, time)

    private fun drag(from: Pair<Double, Double>, to: Pair<Double, Double>, finish: PointerPhase = PointerPhase.UP) {
        controller.onPointer(event(PointerPhase.DOWN, from.first, from.second, 0L))
        controller.onPointer(event(PointerPhase.MOVE, (from.first + to.first) / 2, (from.second + to.second) / 2, 16L))
        controller.onPointer(event(finish, to.first, to.second, 32L))
    }

    private fun stored() = sheet.snapshot.value.elements.getValue(image.id) as ImageElement

    @Test
    fun `dragging a corner stores a crop and the visible bounds in one transaction`() {
        drag(0.0 to 0.0, 20.0 to 10.0)

        assertEquals(LocalRect(20f, 10f, 100f, 50f), stored().crop)
        assertEquals(WorldRect(20.0, 10.0, 100.0, 50.0), stored().bounds)
        assertEquals(1, transactions)
        assertEquals(2, stored().version)
        sheet.undo()
        assertEquals(image, stored())
    }

    @Test
    fun `the pending crop follows the drag and nothing is stored until the pointer lifts`() {
        controller.onPointer(event(PointerPhase.DOWN, 100.0, 50.0, 0L))
        controller.onPointer(event(PointerPhase.MOVE, 80.0, 40.0, 16L))

        assertEquals(LocalRect(0f, 0f, 80f, 40f), controller.pendingCrop.value)
        assertEquals(0, transactions)
    }

    @Test
    fun `dragging inside the crop moves it without resizing, kept within the image`() {
        sheet.transact("Crop") { update(image.id) { (it as ImageElement).copy(crop = LocalRect(10f, 10f, 50f, 40f)) } }
        transactions = 0

        drag(30.0 to 25.0, 130.0 to 25.0)

        assertEquals(LocalRect(60f, 10f, 100f, 40f), stored().crop)
        assertEquals(1, transactions)
    }

    @Test
    fun `a corner cannot shrink the crop below the minimum`() {
        drag(0.0 to 0.0, 99.0 to 49.0)

        val crop = stored().crop!!
        assertEquals(CropController.MIN_SIZE_DP.toFloat(), crop.right - crop.left, 1e-3f)
        assertEquals(CropController.MIN_SIZE_DP.toFloat(), crop.bottom - crop.top, 1e-3f)
    }

    @Test
    fun `a cancelled drag or a press away from the crop changes nothing`() {
        drag(0.0 to 0.0, 20.0 to 10.0, finish = PointerPhase.CANCEL)
        drag(300.0 to 300.0, 320.0 to 320.0)

        assertNull(stored().crop)
        assertNull(controller.pendingCrop.value)
        assertEquals(0, transactions)
    }
}
