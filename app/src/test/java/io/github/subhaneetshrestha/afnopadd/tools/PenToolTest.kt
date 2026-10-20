package io.github.subhaneetshrestha.afnopadd.tools

import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.FrameTransform
import io.github.subhaneetshrestha.afnopadd.canvas.PointerKind
import io.github.subhaneetshrestha.afnopadd.canvas.StrokeFrame
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
import io.github.subhaneetshrestha.afnopadd.tools.pen.PenController
import io.github.subhaneetshrestha.afnopadd.tools.pen.PenTool
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PenToolTest {

    private val existing = TestElements.stroke(z = FractionalIndex("a0"))
    private val sheet = SheetImpl(
        meta = SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Pen", 0L, 0L),
        initialElements = listOf(existing),
        writer = {},
        ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(2)),
        clock = { 5L },
    )
    private val style = MutableStateFlow(CurrentStyle(colorArgb = 0xFF112233, widthPx = 6f))
    private val context = ToolContext(
        sheet = sheet,
        camera = CameraController(),
        style = style,
        clock = { 5L },
        random = Random(9),
    )
    private val controller = PenTool().createController(context) as PenController
    private val frame = StrokeFrame(anchor = WorldPoint(10.0, 20.0), level = 1, transform = FrameTransform(2.5, 0.0, 0.0))
    private val brush = BrushSpec(familyId = "ink:pressure-pen", size = 2f, colorArgb = 0xFF112233, opacity = 1f, epsilon = 0.025f)
    private val points = StrokePoints.of(
        x = floatArrayOf(0f, 10f, 20f),
        y = floatArrayOf(0f, 5f, -5f),
        pressure = floatArrayOf(0.5f, 0.6f, 0.7f),
        tiltRad = floatArrayOf(0f, 0f, 0f),
        orientationRad = floatArrayOf(0f, 0f, 0f),
        timeMillis = floatArrayOf(0f, 8f, 16f),
    )

    @Test
    fun `a finished stroke becomes a stroke element on top of the sheet`() {
        controller.commit(frame, points, brush, PointerKind.STYLUS)

        val element = sheet.snapshot.value.ordered.last() as StrokeElement
        assertEquals(frame.anchor, element.anchor)
        assertEquals(1, element.frameLevel)
        assertEquals(points, element.points)
        assertEquals(brush, element.brush)
        assertTrue(element.z > existing.z)
        assertEquals(1, element.version)
        assertEquals(5L, element.createdAt)
        assertEquals(5L, element.updatedAt)
        assertTrue(sheet.canUndo.value)
    }

    @Test
    fun `bounds cover the points plus half the brush size in world units`() {
        controller.commit(frame, points, brush, PointerKind.FINGER)

        val bounds = (sheet.snapshot.value.ordered.last() as StrokeElement).bounds
        // Level 1: one local unit is 0.1 world units; half the brush (1 local unit) pads by 0.1.
        assertEquals(10.0 - 0.1, bounds.left, 1e-9)
        assertEquals(20.0 - 0.5 - 0.1, bounds.top, 1e-9)
        assertEquals(10.0 + 2.0 + 0.1, bounds.right, 1e-9)
        assertEquals(20.0 + 0.5 + 0.1, bounds.bottom, 1e-9)
    }

    @Test
    fun `the wet brush uses the current style scaled into the frame`() {
        val wet = controller.wetStrokeBrush(frame)

        assertEquals("ink:pressure-pen", wet.familyId)
        assertEquals(6f / 2.5f, wet.size, 1e-6f)
        assertEquals(0xFF112233, wet.colorArgb)
        assertEquals(1f, wet.opacity, 0f)
        assertEquals(0.025f, wet.epsilon, 0f)
    }

    @Test
    fun `an empty stroke is ignored`() {
        controller.commit(frame, StrokePoints.EMPTY, brush, PointerKind.FINGER)

        assertEquals(1, sheet.snapshot.value.elements.size)
        assertFalse(sheet.canUndo.value)
    }
}
