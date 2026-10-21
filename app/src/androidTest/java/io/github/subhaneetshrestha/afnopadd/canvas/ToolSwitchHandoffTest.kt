package io.github.subhaneetshrestha.afnopadd.canvas

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.ink.authoring.InProgressStrokesView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A stroke belongs to the tool that started it, even when the user switches tools before Ink finishes it. */
@RunWith(AndroidJUnit4::class)
class ToolSwitchHandoffTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

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

    private val pen = RecordingSink()
    private val other = RecordingSink()
    private var sink: CanvasInputSink by mutableStateOf(pen)
    private val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Switch", 0L, 0L), emptyList(), writer = {})
    private val camera = CameraController()

    private var showing by mutableStateOf(true)

    private fun showCanvas() {
        rule.setContent {
            val density = LocalDensity.current
            if (showing) Box(Modifier.size(with(density) { 600.toDp() }, with(density) { 400.toDp() })) {
                SheetCanvas(
                    sheet, camera, sink, Modifier.fillMaxSize().testTag(CANVAS_TAG),
                    wetStrokeBrush = { BrushSpec("ink:pressure-pen", 4f, 0xFF000000, 1f, 0.025f) },
                )
            }
        }
        rule.waitForIdle()
    }

    private fun inkView(view: View = rule.activity.window.decorView): InProgressStrokesView? = when (view) {
        is InProgressStrokesView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { inkView(view.getChildAt(it)) }
        else -> null
    }

    @Test
    fun aStrokeFinishedAfterAToolSwitchStillReachesTheToolThatDrewIt() {
        showCanvas()
        // Hold Ink's finish callback back long enough for the tool switch to land first.
        rule.runOnUiThread { inkView()!!.handoffDebounceTimeMs = 1_000 }

        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(Offset(100f, 100f))
            moveTo(Offset(200f, 150f))
            up()
        }
        rule.runOnIdle { sink = other }
        rule.waitForIdle()
        rule.waitUntil(timeoutMillis = 5_000) { pen.finished.isNotEmpty() || other.finished.isNotEmpty() }

        assertEquals(1, pen.finished.size)
        assertTrue(other.finished.isEmpty())
    }

    /**
     * The tool gets the stroke at pen-up, from the router's own samples, so nothing depends on Ink
     * handing it over: leaving the editor or rotating right after lifting cannot lose it, even
     * though removing Ink's view within about half a second of drawing still crashes Ink 1.0.0
     * itself (ADR 0003). The canvas is removed only after Ink has settled, to test this app, not Ink.
     */
    @Test
    fun aLiftedStrokeReachesTheToolAtPenUpAndOnlyOnce() {
        showCanvas()

        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(Offset(100f, 100f))
            moveTo(Offset(200f, 150f))
            moveTo(Offset(200f, 150f)) // a repeated sample, as a real pen-up often sends
            up()
        }
        rule.runOnIdle { assertEquals("the stroke did not reach the tool at pen-up", 1, pen.finished.size) }
        Thread.sleep(1_000)
        rule.runOnIdle { showing = false }
        rule.waitForIdle()

        assertEquals("the stroke was delivered twice", 1, pen.finished.size)
    }

    @Test
    fun aGestureInProgressKeepsGoingToTheToolThatStartedIt() {
        showCanvas()

        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(Offset(100f, 100f))
            moveTo(Offset(150f, 120f))
        }
        rule.runOnIdle { sink = other }
        rule.waitForIdle()
        rule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            moveTo(Offset(200f, 150f))
            up()
        }
        rule.waitUntil(timeoutMillis = 5_000) { pen.finished.isNotEmpty() }

        assertEquals(PointerPhase.UP, pen.events.last().phase)
        assertTrue("the new tool saw ${other.events.map { it.phase }}", other.events.isEmpty())
        assertEquals(1, pen.finished.size)
    }

    private companion object {
        const val CANVAS_TAG = "sheet-canvas"
    }
}
