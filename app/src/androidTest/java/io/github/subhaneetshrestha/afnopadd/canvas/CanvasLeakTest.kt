package io.github.subhaneetshrestha.afnopadd.canvas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ref.WeakReference

/** Switching tools must not leave the previous tool reachable from the canvas. */
@RunWith(AndroidJUnit4::class)
class CanvasLeakTest {

    @get:Rule
    val rule = createComposeRule()

    private class Sink : CanvasInputSink {
        // Ballast so a leaked sink would be obvious in a heap dump too.
        @Suppress("unused")
        val payload = ByteArray(1024 * 1024)

        override fun onPointer(event: CanvasPointerEvent) = Unit
    }

    @Test
    fun aReplacedInputSinkCanBeCollected() {
        val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Leak", 0L, 0L), emptyList(), writer = {})
        val camera = CameraController()
        var sink: CanvasInputSink by mutableStateOf(Sink())
        val first = WeakReference(sink)
        rule.setContent { SheetCanvas(sheet, camera, sink, Modifier.fillMaxSize()) }
        rule.waitForIdle()

        // Five tool switches, as a user going back and forth would.
        repeat(5) {
            rule.runOnIdle { sink = Sink() }
            rule.waitForIdle()
        }

        repeat(5) {
            Runtime.getRuntime().gc()
            System.runFinalization()
            Thread.sleep(50)
        }
        assertNull("the first sink is still reachable after the canvas moved on", first.get())
    }
}
