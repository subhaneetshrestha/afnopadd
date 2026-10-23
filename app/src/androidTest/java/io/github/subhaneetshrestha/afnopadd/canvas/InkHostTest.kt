package io.github.subhaneetshrestha.afnopadd.canvas

import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.ink.authoring.InProgressStrokesView
import org.junit.Assert.assertEquals
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** Ink's wet-stroke view outlives the canvases that use it, and wet ink lands where the finger is. */
@RunWith(AndroidJUnit4::class)
class InkHostTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val sink = object : CanvasInputSink {
        var finished = 0
        override fun onPointer(event: CanvasPointerEvent) = Unit
        override fun onWetStrokeFinished(stroke: FinishedStroke) {
            finished++
        }
    }
    private val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Host", 0L, 0L), emptyList(), writer = {})
    private var showing by mutableStateOf(true)

    /** A canvas pushed [topPx] down the window, so its origin differs from the window's. */
    private fun showCanvas(topPx: Int = 0) {
        rule.setContent {
            val density = LocalDensity.current
            if (showing) Box(Modifier.padding(top = with(density) { topPx.toDp() }).size(with(density) { 600.toDp() }, with(density) { 400.toDp() })) {
                SheetCanvas(
                    sheet, CameraController(), sink, Modifier.fillMaxSize().testTag(CANVAS_TAG),
                    wetStrokeBrush = { BrushSpec("ink:marker", (12.0 / it.transform.scale).toFloat(), 0xFF000000, 1f, 0.025f) },
                )
            }
        }
        rule.waitForIdle()
    }

    /**
     * The ADR 0003 recipe: removing Ink's view within about half a second of drawing crashed Ink
     * 1.0.0's render thread in 3 of 4 runs. A crash kills the instrumentation process, failing this test.
     */
    @Test
    fun removingTheCanvasRightAfterAStrokeDoesNotCrash() {
        showCanvas()
        repeat(6) { round ->
            rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
                down(Offset(100f, 100f))
                moveTo(Offset(200f, 150f))
                moveTo(Offset(300f, 200f))
                up()
            }
            rule.runOnIdle { showing = false }
            rule.waitForIdle()
            Thread.sleep(800) // Ink hands a stroke over 370–550 ms after pen-up; the crash came then.
            rule.runOnIdle { showing = true }
            rule.waitForIdle()
            rule.runOnIdle { assertTrue("round $round", sink.finished == round + 1) }
        }
    }

    @Test
    fun theSameInkViewStaysAttachedWhenTheCanvasLeavesAndComesBack() {
        showCanvas()
        val first = rule.runOnIdle { inkViews().single() }

        rule.runOnIdle { showing = false }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(listOf(first), inkViews()) }
        assertTrue(first.isAttachedToWindow)

        rule.runOnIdle { showing = true }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(listOf(first), inkViews()) }
    }

    private fun inkViews(view: View = rule.activity.window.decorView): List<InProgressStrokesView> = when (view) {
        is InProgressStrokesView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { inkViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    /**
     * Wet ink is drawn by Ink's own surface, so only a screenshot of the display sees it. The touches
     * go through the window, as a finger's do, so they must pass the Ink view lying over the content.
     */
    @Test
    fun wetInkIsDrawnUnderTheFinger() {
        showCanvas(topPx = 300)
        val canvasOnScreen = rule.onNodeWithTag(CANVAS_TAG).fetchSemanticsNode().positionOnScreen
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        fun touch(action: Int, x: Float, eventTime: Long) {
            val event = MotionEvent.obtain(downTime, eventTime, action, canvasOnScreen.x + x, canvasOnScreen.y + 200f, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            instrumentation.sendPointerSync(event)
            event.recycle()
        }

        touch(MotionEvent.ACTION_DOWN, 100f, downTime)
        repeat(10) { touch(MotionEvent.ACTION_MOVE, 100f + (it + 1) * 30f, downTime + (it + 1) * 16L) }
        rule.waitForIdle()
        Thread.sleep(300)
        val screen = instrumentation.uiAutomation.takeScreenshot()
        touch(MotionEvent.ACTION_UP, 400f, downTime + 200L)
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5_000) { sink.finished == 1 }

        fun darkAt(x: Float, y: Float): Boolean {
            val c = screen.getPixel((canvasOnScreen.x + x).roundToInt(), (canvasOnScreen.y + y).roundToInt())
            return (Color.red(c) + Color.green(c) + Color.blue(c)) / 3 < 128
        }
        assertTrue("ink under the finger", darkAt(250f, 200f))
        assertTrue("no ink 40 px above it", !darkAt(250f, 160f))
        assertTrue("no ink 40 px below it", !darkAt(250f, 240f))
    }

    private companion object {
        const val CANVAS_TAG = "sheet-canvas"
    }
}
