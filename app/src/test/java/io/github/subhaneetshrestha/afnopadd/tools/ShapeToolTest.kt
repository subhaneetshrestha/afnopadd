package io.github.subhaneetshrestha.afnopadd.tools

import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasPointerEvent
import io.github.subhaneetshrestha.afnopadd.canvas.PointerKind
import io.github.subhaneetshrestha.afnopadd.canvas.PointerPhase
import io.github.subhaneetshrestha.afnopadd.canvas.ScreenPoint
import io.github.subhaneetshrestha.afnopadd.document.ShapeElement
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInPresets
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInTools
import io.github.subhaneetshrestha.afnopadd.tools.shape.ShapeTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

class ShapeToolTest {

    private var transactions = 0
    private val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Shapes", 0L, 0L), emptyList(), writer = { transactions++ })
    private val camera = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 1.0))
    private val styles = ToolStyles(BuiltInPresets.defaultStyles)
    private val context = ToolContext(sheet, camera, styles)

    private fun controller(toolId: String) = ShapeTools.all.single { it.id == toolId }.createController(context)

    /** A drag in world units; the screen point only matters for the size threshold, so it mirrors the world at 1x. */
    private fun drag(toolId: String, from: WorldPoint, vararg to: WorldPoint, constrain: Boolean = false, finish: PointerPhase = PointerPhase.UP): ToolController {
        val tool = controller(toolId)
        val zoom = camera.camera.value.zoom
        fun event(phase: PointerPhase, p: WorldPoint, time: Long) =
            CanvasPointerEvent(1L, PointerKind.STYLUS, phase, ScreenPoint(p.x * zoom, p.y * zoom), p, 1f, time, constrain = constrain)
        tool.onPointer(event(PointerPhase.DOWN, from, 0L))
        to.forEachIndexed { i, p -> tool.onPointer(event(if (i == to.lastIndex) finish else PointerPhase.MOVE, p, (i + 1) * 8L)) }
        return tool
    }

    private fun shapes() = sheet.snapshot.value.elements.values.filterIsInstance<ShapeElement>()

    @Test
    fun `each shape tool commits one element per drag with the current colour and width`() {
        styles.update(BuiltInTools.RECTANGLE) { it.copy(colorArgb = 0xFF2244CC, widthPx = 6f) }

        for ((id, kind) in listOf(BuiltInTools.LINE to ShapeKind.LINE, BuiltInTools.RECTANGLE to ShapeKind.RECTANGLE, BuiltInTools.ELLIPSE to ShapeKind.ELLIPSE, BuiltInTools.ARROW to ShapeKind.ARROW)) {
            drag(id, WorldPoint(10.0, 20.0), WorldPoint(40.0, 50.0), WorldPoint(110.0, 70.0))
            assertEquals("tool $id", kind, shapes().last { it.shape == kind }.shape)
        }

        assertEquals(4, transactions)
        val rect = shapes().single { it.shape == ShapeKind.RECTANGLE }
        assertEquals(WorldPoint(10.0, 20.0), rect.anchor)
        assertEquals(0, rect.frameLevel)
        assertEquals(100f, rect.geometry.end.x, 1e-4f)
        assertEquals(50f, rect.geometry.end.y, 1e-4f)
        assertEquals(0xFF2244CC, rect.style.colorArgb)
        assertEquals(6f, rect.style.strokeWidth, 1e-6f)
        // Bounds cover the corners plus half the line width.
        assertEquals(7.0, rect.bounds.left, 1e-6)
        assertEquals(73.0, rect.bounds.bottom, 1e-6)
    }

    @Test
    fun `geometry and width are stored in the zoom frame the shape was drawn in`() {
        camera.set(Camera(WorldPoint(0.0, 0.0), zoom = 10.0))
        styles.update(BuiltInTools.LINE) { it.copy(widthPx = 4f) }

        drag(BuiltInTools.LINE, WorldPoint(1.0, 1.0), WorldPoint(3.0, 2.0))

        val line = shapes().single()
        assertEquals(1, line.frameLevel)
        // Level 1: one local unit is 0.1 world units, so 2 world units are 20 local units...
        assertEquals(20f, line.geometry.end.x, 1e-4f)
        assertEquals(10f, line.geometry.end.y, 1e-4f)
        // ...and 4 px at 10x is 0.4 world units, 4 local units.
        assertEquals(4f, line.style.strokeWidth, 1e-5f)
    }

    @Test
    fun `the preview follows the drag and clears when it ends`() {
        val tool = controller(BuiltInTools.ELLIPSE)
        fun event(phase: PointerPhase, x: Double, y: Double) =
            CanvasPointerEvent(1L, PointerKind.FINGER, phase, ScreenPoint(x, y), WorldPoint(x, y), 1f, 0L)

        tool.onPointer(event(PointerPhase.DOWN, 0.0, 0.0))
        tool.onPointer(event(PointerPhase.MOVE, 30.0, 20.0))
        val preview = tool.previewElements.value.single() as ShapeElement
        assertEquals(30f, preview.geometry.end.x, 1e-4f)
        assertEquals(0, transactions)

        tool.onPointer(event(PointerPhase.UP, 30.0, 20.0))
        assertTrue(tool.previewElements.value.isEmpty())
        assertEquals(1, transactions)
    }

    @Test
    fun `a cancelled drag commits nothing and clears the preview`() {
        val tool = drag(BuiltInTools.RECTANGLE, WorldPoint(0.0, 0.0), WorldPoint(50.0, 50.0), WorldPoint(60.0, 60.0), finish = PointerPhase.CANCEL)

        assertEquals(0, transactions)
        assertTrue(tool.previewElements.value.isEmpty())
    }

    @Test
    fun `a tap or a tiny drag makes no shape`() {
        drag(BuiltInTools.RECTANGLE, WorldPoint(0.0, 0.0), WorldPoint(1.0, 1.0))

        assertEquals(0, transactions)
    }

    @Test
    fun `with the constrain modifier rectangles and ellipses are exact squares and circles`() {
        drag(BuiltInTools.RECTANGLE, WorldPoint(0.0, 0.0), WorldPoint(80.0, 30.0), constrain = true)
        drag(BuiltInTools.ELLIPSE, WorldPoint(0.0, 0.0), WorldPoint(-20.0, 70.0), constrain = true)

        val rect = shapes().single { it.shape == ShapeKind.RECTANGLE }.geometry
        assertEquals(80f, rect.end.x, 1e-4f)
        assertEquals(80f, rect.end.y, 1e-4f)
        val ellipse = shapes().single { it.shape == ShapeKind.ELLIPSE }.geometry
        // The drag's direction is kept: up-left stays negative x, down stays positive y.
        assertEquals(-70f, ellipse.end.x, 1e-4f)
        assertEquals(70f, ellipse.end.y, 1e-4f)
    }

    @Test
    fun `with the constrain modifier lines and arrows snap to 45 degree steps and keep their length`() {
        drag(BuiltInTools.LINE, WorldPoint(0.0, 0.0), WorldPoint(100.0, 30.0), constrain = true)
        drag(BuiltInTools.ARROW, WorldPoint(0.0, 0.0), WorldPoint(60.0, 55.0), constrain = true)

        for (shape in shapes()) {
            val end = shape.geometry.end
            val degrees = Math.toDegrees(atan2(end.y.toDouble(), end.x.toDouble()))
            val remainder = abs(degrees % 45.0)
            assertTrue("${shape.shape} at $degrees degrees", remainder < 1e-3 || abs(remainder - 45.0) < 1e-3)
        }
        val line = shapes().single { it.shape == ShapeKind.LINE }.geometry.end
        assertEquals(hypot(100.0, 30.0), hypot(line.x.toDouble(), line.y.toDouble()), 1e-3)
        assertEquals(0f, line.y, 1e-4f)
    }

    @Test
    fun `shape tools do not ink`() {
        assertNull(controller(BuiltInTools.LINE).wetStrokeBrush(io.github.subhaneetshrestha.afnopadd.canvas.StrokeFrame(WorldPoint(0.0, 0.0), 0, io.github.subhaneetshrestha.afnopadd.canvas.FrameTransform(1.0, 0.0, 0.0))))
    }
}
