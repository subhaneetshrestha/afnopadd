package io.github.subhaneetshrestha.afnopadd.tools

import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasPointerEvent
import io.github.subhaneetshrestha.afnopadd.canvas.FrameTransform
import io.github.subhaneetshrestha.afnopadd.canvas.PointerKind
import io.github.subhaneetshrestha.afnopadd.canvas.PointerPhase
import io.github.subhaneetshrestha.afnopadd.canvas.ScreenPoint
import io.github.subhaneetshrestha.afnopadd.canvas.StrokeFrame
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import io.github.subhaneetshrestha.afnopadd.tools.hand.HandTool
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInPresets
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hand tool's panning and pinching are the canvas's navigate gestures; the tool itself must never edit. */
class HandToolTest {

    private var transactions = 0
    private val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Hand", 0L, 0L), emptyList(), writer = { transactions++ })
    private val camera = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 1.0))
    private val context = ToolContext(sheet, camera, ToolStyles(BuiltInPresets.defaultStyles))

    @Test
    fun `the hand tool asks for navigation and has a stable id`() {
        val hand = HandTool()
        assertEquals(BuiltInTools.HAND, hand.id)
        assertEquals(setOf(ToolCapability.NAVIGATE), hand.capabilities)
    }

    @Test
    fun `whatever reaches the hand tool, it never inks and never changes the sheet`() {
        val controller = HandTool().createController(context)

        for (kind in PointerKind.entries) {
            listOf(PointerPhase.DOWN, PointerPhase.MOVE, PointerPhase.MOVE, PointerPhase.UP).forEachIndexed { i, phase ->
                controller.onPointer(CanvasPointerEvent(1L, kind, phase, ScreenPoint(i * 10.0, 0.0), WorldPoint(i * 10.0, 0.0), 1f, i * 8L))
            }
        }

        assertNull(controller.wetStrokeBrush(StrokeFrame(WorldPoint(0.0, 0.0), 0, FrameTransform(1.0, 0.0, 0.0))))
        assertEquals(0, transactions)
        assertTrue(sheet.snapshot.value.elements.isEmpty())
        assertEquals(Camera(WorldPoint(0.0, 0.0), zoom = 1.0), camera.camera.value)
    }
}
