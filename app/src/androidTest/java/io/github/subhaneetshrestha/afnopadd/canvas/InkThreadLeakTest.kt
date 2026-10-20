package io.github.subhaneetshrestha.afnopadd.canvas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ink 1.0.0 starts a render-helper thread for every `InProgressStrokesView` and never stops it, so
 * opening the canvas again and again must not create a new view (and thread) each time, and the
 * reused view must still draw.
 */
@RunWith(AndroidJUnit4::class)
class InkThreadLeakTest {

    @get:Rule
    val rule = createComposeRule()

    private fun inkThreads(): Int = Thread.getAllStackTraces().keys.count { it.name.startsWith("CanvasInProgress") && it.isAlive }

    @Test
    fun reopeningTheCanvasDoesNotStartMoreInkThreadsAndStillDraws() {
        val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Threads", 0L, 0L), emptyList(), writer = {})
        var finished = 0
        val sink = object : CanvasInputSink {
            override fun onPointer(event: CanvasPointerEvent) = Unit
            override fun onWetStrokeFinished(stroke: FinishedStroke) {
                finished++
            }
        }
        var showing by mutableStateOf(true)
        rule.setContent {
            if (showing) {
                SheetCanvas(
                    sheet, CameraController(), sink, Modifier.fillMaxSize().testTag("canvas"),
                    wetStrokeBrush = { BrushSpec("ink:pressure-pen", 4f, 0xFF000000, 1f, 0.025f) },
                )
            }
        }
        rule.waitForIdle()
        val afterFirst = inkThreads()

        repeat(10) {
            rule.runOnIdle { showing = false }
            rule.waitForIdle()
            rule.runOnIdle { showing = true }
            rule.waitForIdle()
        }
        val afterTen = inkThreads()

        rule.onNodeWithTag("canvas").performTouchInput {
            down(Offset(100f, 300f))
            moveTo(Offset(300f, 400f))
            up()
        }
        rule.waitUntil(timeoutMillis = 5_000) { finished == 1 }

        assertTrue("Ink render threads grew from $afterFirst to $afterTen over ten reopenings", afterTen <= afterFirst)
    }
}
