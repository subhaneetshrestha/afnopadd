package io.github.subhaneetshrestha.afnopadd.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import kotlin.math.hypot

/** Gestures on the canvas: two pointers always drive the camera, one pointer goes where the policy says. */
@RunWith(AndroidJUnit4::class)
class SheetCanvasGestureTest {

    @get:Rule
    val rule = createComposeRule()

    private class RecordingSink : CanvasInputSink {
        val events = mutableListOf<CanvasPointerEvent>()
        override fun onPointer(event: CanvasPointerEvent) {
            events += event
        }
    }

    private val sink = RecordingSink()
    private val sheet: Sheet = SheetImpl(
        meta = SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Gestures", 0L, 0L),
        initialElements = emptyList(),
        writer = {},
    )

    private fun showCanvas(
        controller: CameraController,
        role: PointerRole = PointerRole.DRAW,
        background: Background = Background.PLAIN,
    ): Viewport {
        var viewport = Viewport(0, 0)
        rule.setContent {
            val density = LocalDensity.current
            Box(Modifier.size(with(density) { 600.toDp() }, with(density) { 400.toDp() })) {
                SheetCanvas(
                    sheet = sheet,
                    cameraController = controller,
                    inputSink = sink,
                    modifier = Modifier.fillMaxSize().testTag(CANVAS_TAG),
                    background = background,
                    pointerRole = { role },
                    onViewportChanged = { viewport = it },
                )
            }
        }
        rule.waitForIdle()
        assertEquals(Viewport(600, 400), viewport)
        return viewport
    }

    @Test
    fun pinchZoomsInAndKeepsTheFocalPointStationary() {
        val controller = CameraController(Camera(WorldPoint(10.0, -10.0), zoom = 1.0))
        val viewport = showCanvas(controller)
        val focal = ScreenPoint(350.0, 180.0)
        val before = CanvasMath.screenToWorld(controller.camera.value, viewport, focal)

        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            pinch(
                start0 = Offset(focal.x.toFloat() - 50f, focal.y.toFloat()),
                end0 = Offset(focal.x.toFloat() - 100f, focal.y.toFloat()),
                start1 = Offset(focal.x.toFloat() + 50f, focal.y.toFloat()),
                end1 = Offset(focal.x.toFloat() + 100f, focal.y.toFloat()),
            )
        }
        rule.waitForIdle()

        val camera = controller.camera.value
        val after = CanvasMath.screenToWorld(camera, viewport, focal)
        assertEquals(2.0, camera.zoom, 0.05)
        val driftPx = hypot(after.x - before.x, after.y - before.y) * camera.zoom
        assertTrue("focal point drifted $driftPx px", driftPx < 1.0)
        assertTrue("a pinch must not finish a stroke", sink.events.none { it.phase == PointerPhase.UP })
    }

    @Test
    fun twoFingerDragPansWithoutZooming() {
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 2.0))
        showCanvas(controller)

        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(0, Offset(200f, 200f))
            down(1, Offset(300f, 200f))
            repeat(10) {
                moveBy(0, Offset(10f, 0f))
                moveBy(1, Offset(10f, 0f))
            }
            up(0)
            up(1)
        }
        rule.waitForIdle()

        val camera = controller.camera.value
        assertEquals(2.0, camera.zoom, 1e-6)
        assertEquals(-50.0, camera.center.x, 0.5)
        assertEquals(0.0, camera.center.y, 0.5)
    }

    @Test
    fun singlePointerEventsReachTheSinkWithWorldCoordinates() {
        val controller = CameraController(Camera(WorldPoint(5.0, 5.0), zoom = 4.0))
        val viewport = showCanvas(controller)

        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(Offset(100f, 100f))
            moveTo(Offset(150f, 120f))
            up()
        }
        rule.waitForIdle()

        val phases = sink.events.map { it.phase }
        assertEquals(PointerPhase.DOWN, phases.first())
        assertEquals(PointerPhase.UP, phases.last())
        assertTrue(PointerPhase.MOVE in phases)
        val expectedDown = CanvasMath.screenToWorld(controller.camera.value, viewport, ScreenPoint(100.0, 100.0))
        assertTrue(abs(sink.events.first().world.x - expectedDown.x) < 1e-6)
        assertTrue(abs(sink.events.first().world.y - expectedDown.y) < 1e-6)
        assertEquals(PointerKind.FINGER, sink.events.first().kind)
        assertTrue(sink.events.all { it.pressure in 0f..1f })
        assertEquals(controller.camera.value, Camera(WorldPoint(5.0, 5.0), zoom = 4.0))
    }

    @Test
    fun singlePointerPansInNavigateMode() {
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 1.0))
        showCanvas(controller, role = PointerRole.NAVIGATE)

        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(Offset(100f, 100f))
            repeat(10) { moveBy(Offset(0f, -10f)) }
            up()
        }
        rule.waitForIdle()

        assertTrue(sink.events.isEmpty())
        assertEquals(100.0, controller.camera.value.center.y, 0.5)
        assertEquals(0.0, controller.camera.value.center.x, 0.5)
    }

    @Test
    fun doubleTapZoomsTwiceTowardTheTapInNavigateMode() {
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 1.0))
        val viewport = showCanvas(controller, role = PointerRole.NAVIGATE)
        val tap = ScreenPoint(100.0, 300.0)
        val before = CanvasMath.screenToWorld(controller.camera.value, viewport, tap)

        rule.onNodeWithTag(CANVAS_TAG).performTouchInput { doubleClick(Offset(tap.x.toFloat(), tap.y.toFloat())) }
        rule.waitForIdle()

        val camera = controller.camera.value
        val after = CanvasMath.screenToWorld(camera, viewport, tap)
        assertEquals(2.0, camera.zoom, 1e-6)
        assertTrue(hypot(after.x - before.x, after.y - before.y) * camera.zoom < 1.0)
    }

    @Test
    fun gridLineCountStaysStableAcrossZoomBuckets() {
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 1.0))
        showCanvas(controller, background = Background.GRID)

        val counts = listOf(1.0, 10.0, 100.0).map { zoom ->
            rule.runOnUiThread { controller.set(Camera(WorldPoint(0.0, 0.0), zoom)) }
            rule.waitForIdle()
            val pixels = rule.onNodeWithTag(CANVAS_TAG).captureToImage().toPixelMap()
            val row = pixels.height / 2 + 7 // avoid landing exactly on a horizontal grid line
            var lines = 0
            var previousDark = false
            for (x in 0 until pixels.width) {
                val color = pixels[x, row]
                val dark = (color.red + color.green + color.blue) / 3f < 0.9f
                if (dark && !previousDark) lines++
                previousDark = dark
            }
            lines
        }

        assertTrue("grid line counts $counts", counts.all { it in 3..40 })
        assertTrue("grid line counts $counts should be within one of each other", counts.max() - counts.min() <= 1)
    }

    private companion object {
        const val CANVAS_TAG = "sheet-canvas"
    }
}
