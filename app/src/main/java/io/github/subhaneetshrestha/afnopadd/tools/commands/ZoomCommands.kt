package io.github.subhaneetshrestha.afnopadd.tools.commands

import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasMath
import io.github.subhaneetshrestha.afnopadd.canvas.Viewport
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.tools.Command
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToLong

/**
 * The zoom commands (SPEC-drawing-tools decision 9): in and out by 1.5x around the viewport
 * centre, fit to all content, and back to 1x. Each computes a target camera and hands it to
 * [animateTo], which the editor backs with a 150 ms [io.github.subhaneetshrestha.afnopadd.canvas.CameraAnimator].
 * [contentBounds] is null for an empty sheet, which fit treats as "back to the origin at 1x".
 */
class ZoomCommands(
    private val camera: CameraController,
    private val viewport: () -> Viewport,
    private val contentBounds: () -> WorldRect?,
    private val animateTo: (Camera) -> Unit,
    /** Whether a zoom animation is still running; a new step then starts from its target. */
    private val animating: () -> Boolean = { false },
) {
    private val always = MutableStateFlow(true)

    val all: List<Command> = listOf(
        Command(ZOOM_IN, R.string.command_zoom_in, R.drawable.ic_zoom_in, always) { step(STEP) },
        Command(ZOOM_OUT, R.string.command_zoom_out, R.drawable.ic_zoom_out, always) { step(1 / STEP) },
        Command(ZOOM_FIT, R.string.command_zoom_fit, R.drawable.ic_zoom_fit, always) {
            animateTo(contentBounds()?.let { CanvasMath.fitCamera(it, viewport(), FIT_PADDING_PX) } ?: Camera.DEFAULT)
        },
        Command(ZOOM_100, R.string.command_zoom_100, R.drawable.ic_zoom_100, always) { animateTo(camera.camera.value.copy(zoom = 1.0)) },
    )

    /**
     * Zooming about the viewport centre keeps the camera's centre, so only the zoom changes. A step
     * taken while the previous one still animates builds on where that one is heading, not on the
     * camera halfway there, so two quick taps make exactly 2.25x.
     */
    private fun step(factor: Double) {
        val current = camera.camera.value
        val from = lastTarget?.takeIf { it.center == current.center && current.zoom != it.zoom && animating() } ?: current
        animateTo(from.copy(zoom = (from.zoom * factor).coerceIn(Camera.MIN_ZOOM, Camera.MAX_ZOOM)).also { lastTarget = it })
    }

    private var lastTarget: Camera? = null

    companion object {
        const val ZOOM_IN = "cmd:zoom-in"
        const val ZOOM_OUT = "cmd:zoom-out"
        const val ZOOM_FIT = "cmd:zoom-fit"
        const val ZOOM_100 = "cmd:zoom-100"
        const val STEP = 1.5
        const val FIT_PADDING_PX = 48.0

        /** The rectangle covering all of [bounds], or null when there are none. */
        fun boundsOf(bounds: Collection<WorldRect>): WorldRect? = if (bounds.isEmpty()) null else WorldRect(
            left = bounds.minOf { it.left },
            top = bounds.minOf { it.top },
            right = bounds.maxOf { it.right },
            bottom = bounds.maxOf { it.bottom },
        )

        /**
         * A compact zoom label: a percentage up to 10x ("150%", "0.1%" below 1 percent), then a
         * factor ("25×", "1.5K×") so the readout stays short up to the 100,000x limit.
         */
        fun readout(zoom: Double): String {
            val percent = zoom * 100
            return when {
                zoom <= 10.0 && percent < 1.0 -> "${trim((percent * 10).roundToLong() / 10.0)}%"
                zoom <= 10.0 -> "${percent.roundToLong()}%"
                zoom < 1_000.0 -> "${zoom.roundToLong()}×"
                else -> {
                    val thousands = zoom / 1_000
                    if (thousands < 10) "${trim((thousands * 10).roundToLong() / 10.0)}K×" else "${thousands.roundToLong()}K×"
                }
            }
        }

        private fun trim(value: Double) = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }
}
