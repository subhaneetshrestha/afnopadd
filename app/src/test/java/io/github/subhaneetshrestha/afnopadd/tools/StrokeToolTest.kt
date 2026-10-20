package io.github.subhaneetshrestha.afnopadd.tools

import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasMath
import io.github.subhaneetshrestha.afnopadd.canvas.FrameLevels
import io.github.subhaneetshrestha.afnopadd.canvas.FrameTransform
import io.github.subhaneetshrestha.afnopadd.canvas.PointerKind
import io.github.subhaneetshrestha.afnopadd.canvas.StrokeFrame
import io.github.subhaneetshrestha.afnopadd.canvas.Viewport
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import io.github.subhaneetshrestha.afnopadd.document.TestElements
import io.github.subhaneetshrestha.afnopadd.document.Ulid
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInPresets
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInTools
import io.github.subhaneetshrestha.afnopadd.tools.stroke.StrokeTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StrokeToolTest {

    private val existing = TestElements.stroke(z = FractionalIndex("a0"))
    private val sheet = SheetImpl(
        meta = SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Strokes", 0L, 0L),
        initialElements = listOf(existing),
        writer = {},
        ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(2)),
        clock = { 5L },
    )
    private val styles = ToolStyles(BuiltInPresets.defaultStyles)
    private val context = ToolContext(sheet = sheet, camera = CameraController(), styles = styles, clock = { 5L }, random = Random(9))
    private val unitFrame = StrokeFrame(anchor = WorldPoint(0.0, 0.0), level = 0, transform = FrameTransform(1.0, 0.0, 0.0))

    private fun controller(toolId: String): ToolController =
        StrokeTool(BuiltInPresets.strokeTools.single { it.id == toolId }).createController(context)

    @Test
    fun `each built-in stroke tool maps to its brush family, size and opacity`() {
        styles.update(BuiltInTools.PEN) { it.copy(widthPx = 4f) }
        styles.update(BuiltInTools.PENCIL) { it.copy(widthPx = 4f) }
        styles.update(BuiltInTools.BRUSH) { it.copy(widthPx = 4f) }
        styles.update(BuiltInTools.HIGHLIGHTER) { it.copy(widthPx = 4f) }

        val pen = controller(BuiltInTools.PEN).wetStrokeBrush(unitFrame)!!
        val pencil = controller(BuiltInTools.PENCIL).wetStrokeBrush(unitFrame)!!
        val brush = controller(BuiltInTools.BRUSH).wetStrokeBrush(unitFrame)!!
        val highlighter = controller(BuiltInTools.HIGHLIGHTER).wetStrokeBrush(unitFrame)!!

        assertEquals(Triple("ink:pressure-pen", 4f, 1f), Triple(pen.familyId, pen.size, pen.opacity))
        assertEquals("ink:pressure-pen", pencil.familyId)
        assertEquals(4f * 0.6f, pencil.size, 1e-6f)
        assertEquals(0.6f, pencil.opacity, 1e-6f)
        assertEquals(Triple("ink:marker", 8f, 1f), Triple(brush.familyId, brush.size, brush.opacity))
        assertEquals("ink:highlighter", highlighter.familyId)
        assertEquals(0.4f, highlighter.opacity, 1e-6f)
    }

    @Test
    fun `a 3 px width chosen at 10x renders 3 px there and 0_3 px at 1x`() {
        styles.update(BuiltInTools.PEN) { it.copy(widthPx = 3f) }
        val viewport = Viewport(800, 600)
        val at10x = Camera(WorldPoint(0.0, 0.0), zoom = 10.0)
        val level = FrameLevels.levelFor(at10x.zoom)
        val frame = StrokeFrame(WorldPoint(1.0, 1.0), level, CanvasMath.frameToScreen(at10x, viewport, WorldPoint(1.0, 1.0), level))

        val brush = controller(BuiltInTools.PEN).wetStrokeBrush(frame)!!

        assertEquals(3.0, brush.size * frame.transform.scale, 1e-6)
        val at1x = CanvasMath.frameToScreen(Camera(WorldPoint(0.0, 0.0), 1.0), viewport, WorldPoint(1.0, 1.0), level)
        assertEquals(0.3, brush.size * at1x.scale, 1e-6)
    }

    @Test
    fun `each tool remembers its own width and colour`() {
        styles.update(BuiltInTools.PEN) { it.copy(widthPx = 7f, colorArgb = 0xFF0000FF) }

        assertEquals(ToolStyle(0xFF0000FF, 7f), styles.styleFor(BuiltInTools.PEN))
        assertEquals(BuiltInPresets.defaultStyles.getValue(BuiltInTools.PENCIL), styles.styleFor(BuiltInTools.PENCIL))
    }

    @Test
    fun `widths are clamped to the supported range`() {
        styles.update(BuiltInTools.PEN) { it.copy(widthPx = 500f) }
        assertEquals(ToolStyles.MAX_WIDTH_PX, styles.styleFor(BuiltInTools.PEN).widthPx, 0f)

        styles.update(BuiltInTools.PEN) { it.copy(widthPx = 0f) }
        assertEquals(ToolStyles.MIN_WIDTH_PX, styles.styleFor(BuiltInTools.PEN).widthPx, 0f)
    }

    @Test
    fun `the highlighter ignores the alpha of the chosen colour`() {
        styles.update(BuiltInTools.HIGHLIGHTER) { it.copy(colorArgb = 0x40FF0000) }

        val brush = controller(BuiltInTools.HIGHLIGHTER).wetStrokeBrush(unitFrame)!!

        assertEquals(0xFFFF0000, brush.colorArgb)
        assertEquals(0.4f, brush.opacity, 1e-6f)
    }

    @Test
    fun `a finished stroke becomes a stroke element on top of the sheet`() {
        val tool = controller(BuiltInTools.PENCIL)
        val frame = StrokeFrame(anchor = WorldPoint(10.0, 20.0), level = 1, transform = FrameTransform(2.5, 0.0, 0.0))
        val brush = BrushSpec("ink:pressure-pen", 2f, 0xFF112233, 0.6f, 0.025f)
        val points = StrokePoints.of(
            x = floatArrayOf(0f, 10f, 20f),
            y = floatArrayOf(0f, 5f, -5f),
            pressure = floatArrayOf(0.5f, 0.6f, 0.7f),
            tiltRad = floatArrayOf(0f, 0f, 0f),
            orientationRad = floatArrayOf(0f, 0f, 0f),
            timeMillis = floatArrayOf(0f, 8f, 16f),
        )

        (tool as io.github.subhaneetshrestha.afnopadd.tools.stroke.StrokeController).commit(frame, points, brush, PointerKind.STYLUS)

        val element = sheet.snapshot.value.ordered.last() as StrokeElement
        assertEquals(frame.anchor, element.anchor)
        assertEquals(1, element.frameLevel)
        assertEquals(brush, element.brush)
        assertTrue(element.z > existing.z)
        // Level 1: one local unit is 0.1 world units; half the brush (1 local unit) pads by 0.1.
        assertEquals(10.0 - 0.1, element.bounds.left, 1e-9)
        assertEquals(20.0 + 0.5 + 0.1, element.bounds.bottom, 1e-9)
    }

    @Test
    fun `an empty stroke is ignored`() {
        val tool = controller(BuiltInTools.PEN) as io.github.subhaneetshrestha.afnopadd.tools.stroke.StrokeController

        tool.commit(unitFrame, StrokePoints.EMPTY, BrushSpec("ink:pressure-pen", 1f, 0xFF000000, 1f, 0.025f), PointerKind.FINGER)

        assertEquals(1, sheet.snapshot.value.elements.size)
        assertFalse(sheet.canUndo.value)
    }
}
