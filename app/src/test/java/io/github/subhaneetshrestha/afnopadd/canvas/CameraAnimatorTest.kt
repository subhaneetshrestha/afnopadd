package io.github.subhaneetshrestha.afnopadd.canvas

import androidx.compose.runtime.MonotonicFrameClock
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraAnimatorTest {

    /** A display that ticks every 16 ms and notes the camera each frame starts with. */
    private class FakeFrameClock(private val controller: CameraController) : MonotonicFrameClock {
        var frames = 0
        val seen = mutableListOf<Camera>()
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            seen += controller.camera.value
            return onFrame(frames++ * 16_000_000L)
        }
    }

    @Test
    fun `an animation reaches its target in about 150 ms, zooming geometrically`() = runTest {
        val controller = CameraController(Camera(WorldPoint(0.0, 0.0), 1.0))
        val clock = FakeFrameClock(controller)

        withContext(clock) { CameraAnimator(controller).animateTo(Camera(WorldPoint(100.0, 0.0), 4.0)) }

        assertEquals(Camera(WorldPoint(100.0, 0.0), 4.0), controller.camera.value)
        // 150 ms at 16 ms a frame: about ten frames, with the camera in between on each.
        assertTrue("frames ${clock.frames}", clock.frames in 10..12)
        val between = clock.seen.filter { it.zoom > 1.0 && it.zoom < 4.0 }
        assertTrue("intermediate cameras ${between.size}", between.size >= 5)
        assertTrue(clock.seen.zipWithNext().all { (a, b) -> b.zoom >= a.zoom })
    }

    @Test
    fun `a zero-length animation jumps to the target`() = runTest {
        val controller = CameraController()
        withContext(FakeFrameClock(controller)) { CameraAnimator(controller, durationMillis = 0).animateTo(Camera(WorldPoint(5.0, 5.0), 2.0)) }
        assertEquals(Camera(WorldPoint(5.0, 5.0), 2.0), controller.camera.value)
    }
}
