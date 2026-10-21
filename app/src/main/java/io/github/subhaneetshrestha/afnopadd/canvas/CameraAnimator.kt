package io.github.subhaneetshrestha.afnopadd.canvas

import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.cos

/**
 * Moves the camera to a target over [durationMillis], one step per display frame, with
 * [CanvasMath.interpolate] (geometric in zoom) and ease-in-out timing. Runs in a coroutine whose
 * context has a frame clock, such as a composition's scope; cancelling the coroutine stops the
 * camera where it is.
 */
class CameraAnimator(private val controller: CameraController, private val durationMillis: Long = DEFAULT_DURATION_MILLIS) {

    suspend fun animateTo(target: Camera) {
        val from = controller.camera.value
        if (durationMillis <= 0L || from == target) {
            controller.set(target)
            return
        }
        val start = withFrameNanos { it }
        while (true) {
            val elapsedMillis = (withFrameNanos { it } - start) / 1_000_000.0
            val fraction = (elapsedMillis / durationMillis).coerceAtMost(1.0)
            controller.set(if (fraction >= 1.0) target else CanvasMath.interpolate(from, target, easeInOut(fraction)))
            if (fraction >= 1.0) return
        }
    }

    private fun easeInOut(t: Double) = (1 - cos(PI * t)) / 2

    companion object {
        /** SPEC-drawing-tools decision 9. */
        const val DEFAULT_DURATION_MILLIS = 150L
    }
}
