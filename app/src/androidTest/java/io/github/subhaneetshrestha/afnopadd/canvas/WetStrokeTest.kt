package io.github.subhaneetshrestha.afnopadd.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Wet strokes drawn through Jetpack Ink come back as finished strokes in frame units anchored at their first point. */
@RunWith(AndroidJUnit4::class)
class WetStrokeTest {

    @get:Rule
    val rule = createComposeRule()

    private class RecordingSink : CanvasInputSink {
        val events = mutableListOf<CanvasPointerEvent>()
        val finished = mutableListOf<FinishedStroke>()
        override fun onPointer(event: CanvasPointerEvent) {
            events += event
        }
        override fun onWetStrokeFinished(stroke: FinishedStroke) {
            finished += stroke
        }
    }

    private val sink = RecordingSink()
    private val sheet: Sheet = SheetImpl(
        meta = SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Wet", 0L, 0L),
        initialElements = emptyList(),
        writer = {},
    )

    private fun penBrush(frame: StrokeFrame) = BrushSpec(
        familyId = "ink:pressure-pen",
        size = (SIZE_PX / frame.transform.scale).toFloat(),
        colorArgb = 0xFF000000,
        opacity = 1f,
        epsilon = 0.025f,
    )

    private fun showCanvas(controller: CameraController, brush: (StrokeFrame) -> BrushSpec? = ::penBrush): Viewport {
        var viewport = Viewport(0, 0)
        rule.setContent {
            val density = LocalDensity.current
            Box(Modifier.size(with(density) { 600.toDp() }, with(density) { 400.toDp() })) {
                SheetCanvas(
                    sheet = sheet,
                    cameraController = controller,
                    inputSink = sink,
                    modifier = Modifier.fillMaxSize().testTag(CANVAS_TAG),
                    wetStrokeBrush = brush,
                    onViewportChanged = { viewport = it },
                )
            }
        }
        rule.waitForIdle()
        return viewport
    }

    @Test
    fun aDrawnStrokeFinishesInFrameUnitsAnchoredAtItsFirstPoint() {
        val controller = CameraController(Camera(WorldPoint(10.0, 20.0), zoom = 25.0))
        val viewport = showCanvas(controller)
        val camera = controller.camera.value

        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(Offset(100f, 100f))
            moveTo(Offset(150f, 120f))
            moveTo(Offset(200f, 140f))
            up()
        }
        rule.waitUntil(timeoutMillis = 5_000) { sink.finished.isNotEmpty() }

        val finished = sink.finished.single()
        assertEquals(1, finished.frame.level)
        val expectedAnchor = CanvasMath.screenToWorld(camera, viewport, ScreenPoint(100.0, 100.0))
        assertTrue(abs(finished.frame.anchor.x - expectedAnchor.x) < 1e-6)
        assertTrue(abs(finished.frame.anchor.y - expectedAnchor.y) < 1e-6)

        val points = finished.points
        assertTrue("expected at least two samples, got ${points.count}", points.count >= 2)
        assertEquals(0f, points.x(0), 1e-3f)
        assertEquals(0f, points.y(0), 1e-3f)
        val expectedLast = FrameLevels.worldToLocal(
            CanvasMath.screenToWorld(camera, viewport, ScreenPoint(200.0, 140.0)),
            finished.frame.anchor,
            finished.frame.level,
        )
        assertEquals(expectedLast.x, points.x(points.count - 1), 1e-2f)
        assertEquals(expectedLast.y, points.y(points.count - 1), 1e-2f)
        assertEquals(points.count, finished.stroke.inputs.size)
        assertTrue(points.pressure(0) in 0f..1f)
        assertEquals(PointerKind.FINGER, finished.kind)
        assertEquals((SIZE_PX / finished.frame.transform.scale).toFloat(), finished.brush.size, 1e-6f)
        assertEquals(finished.brush.size, finished.stroke.brush.size, 1e-6f)
    }

    @Test
    fun aCancelledStrokeNeverFinishes() {
        showCanvas(CameraController())

        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(Offset(100f, 100f))
            moveTo(Offset(150f, 120f))
            cancel()
        }
        rule.waitForIdle()
        Thread.sleep(500)

        assertTrue(sink.events.any { it.phase == PointerPhase.CANCEL })
        assertTrue(sink.finished.isEmpty())
    }

    @Test
    fun aSecondPointerCancelsTheStrokeInProgress() {
        showCanvas(CameraController())

        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(0, Offset(100f, 100f))
            moveTo(0, Offset(150f, 120f))
            down(1, Offset(300f, 300f))
            moveBy(0, Offset(10f, 0f))
            moveBy(1, Offset(10f, 0f))
            up(0)
            up(1)
        }
        rule.waitForIdle()
        Thread.sleep(500)

        assertTrue(sink.finished.isEmpty())
    }

    @Test
    fun noBrushMeansNoWetStrokeButPointerEventsStillFlow() {
        showCanvas(CameraController(), brush = { null })

        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(Offset(100f, 100f))
            moveTo(Offset(150f, 120f))
            up()
        }
        rule.waitForIdle()
        Thread.sleep(500)

        assertTrue(sink.finished.isEmpty())
        assertEquals(listOf(PointerPhase.DOWN, PointerPhase.UP), listOf(sink.events.first().phase, sink.events.last().phase))
    }

    private companion object {
        const val CANVAS_TAG = "sheet-canvas"
        const val SIZE_PX = 6.0
    }
}
