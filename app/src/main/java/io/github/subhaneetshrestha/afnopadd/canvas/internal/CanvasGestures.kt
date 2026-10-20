package io.github.subhaneetshrestha.afnopadd.canvas.internal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasInputSink
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasMath
import io.github.subhaneetshrestha.afnopadd.canvas.CanvasPointerEvent
import io.github.subhaneetshrestha.afnopadd.canvas.PointerKind
import io.github.subhaneetshrestha.afnopadd.canvas.PointerPhase
import io.github.subhaneetshrestha.afnopadd.canvas.PointerRole
import io.github.subhaneetshrestha.afnopadd.canvas.ScreenPoint
import io.github.subhaneetshrestha.afnopadd.canvas.Viewport
import kotlinx.coroutines.CancellationException

/**
 * The canvas gesture state machine. Two pressed pointers always belong to the camera (pinch zoom
 * about their centroid plus pan). One pointer is either forwarded to the [CanvasInputSink] or, when
 * the policy says [PointerRole.NAVIGATE], pans the camera; a double tap in navigate mode zooms 2x.
 * A stroke in progress is cancelled the moment a second pointer lands or the system cancels the
 * gesture (Compose turns `ACTION_CANCEL` into a synthetic, pre-consumed release event).
 */
internal fun Modifier.canvasGestures(
    cameraController: CameraController,
    inputSink: CanvasInputSink,
    pointerRole: (PointerKind) -> PointerRole,
    viewport: () -> Viewport,
): Modifier = pointerInput(cameraController, inputSink, pointerRole) {
    val doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis
    val doubleTapSlop = viewConfiguration.touchSlop * 2
    val touchSlop = viewConfiguration.touchSlop
    var lastTap: Tap? = null

    fun emit(change: PointerInputChange, phase: PointerPhase, position: Offset = change.position) {
        val screen = ScreenPoint(position.x.toDouble(), position.y.toDouble())
        inputSink.onPointer(
            CanvasPointerEvent(
                pointerId = change.id.value,
                kind = change.type.toPointerKind(),
                phase = phase,
                screen = screen,
                world = CanvasMath.screenToWorld(cameraController.camera.value, viewport(), screen),
                pressure = change.pressure.coerceIn(0f, 1f),
                timeMillis = change.uptimeMillis,
            ),
        )
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var mode = if (pointerRole(down.type.toPointerKind()) == PointerRole.DRAW) Mode.DRAWING else Mode.NAVIGATING
        val drawingPointer = down.id
        var lastDrawingPosition = down.position
        var navigateMoved = false
        if (mode == Mode.DRAWING) emit(down, PointerPhase.DOWN)
        down.consume()

        try {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }

                if (pressed.size >= 2) {
                    if (mode == Mode.DRAWING) emit(down, PointerPhase.CANCEL, lastDrawingPosition)
                    mode = Mode.PINCHING
                    val a = pressed[0]
                    val b = pressed[1]
                    val previousCentroid = (a.previousPosition + b.previousPosition) / 2f
                    val centroid = (a.position + b.position) / 2f
                    val previousDistance = (a.previousPosition - b.previousPosition).getDistance()
                    val distance = (a.position - b.position).getDistance()
                    val delta = centroid - previousCentroid
                    cameraController.panBy(delta.x.toDouble(), delta.y.toDouble())
                    if (previousDistance > 0f && distance > 0f) {
                        cameraController.zoomBy(
                            factor = (distance / previousDistance).toDouble(),
                            pivot = ScreenPoint(centroid.x.toDouble(), centroid.y.toDouble()),
                            viewport = viewport(),
                        )
                    }
                } else if (pressed.size == 1) {
                    val change = pressed[0]
                    when (mode) {
                        Mode.DRAWING -> if (change.id == drawingPointer) {
                            lastDrawingPosition = change.position
                            if (change.position != change.previousPosition) emit(change, PointerPhase.MOVE)
                        }
                        Mode.NAVIGATING -> {
                            val delta = change.position - change.previousPosition
                            if ((change.position - down.position).getDistance() > touchSlop) navigateMoved = true
                            cameraController.panBy(delta.x.toDouble(), delta.y.toDouble())
                        }
                        Mode.PINCHING, Mode.FINISHED -> Unit
                    }
                }

                if (mode == Mode.DRAWING) {
                    event.changes.firstOrNull { it.id == drawingPointer && !it.pressed && it.previousPressed }?.let { release ->
                        mode = Mode.FINISHED
                        // A release that arrives already consumed is Compose's synthetic ACTION_CANCEL
                        // (the same rule its own gesture detectors use); a real lift is unconsumed.
                        emit(release, if (release.isConsumed) PointerPhase.CANCEL else PointerPhase.UP)
                    }
                }
                event.changes.forEach { it.consume() }

                if (event.changes.none { it.pressed }) {
                    lastTap = if (mode == Mode.NAVIGATING && !navigateMoved) {
                        val release = event.changes.first()
                        val tap = Tap(release.position, release.uptimeMillis)
                        val previous = lastTap
                        if (previous != null &&
                            tap.timeMillis - previous.timeMillis <= doubleTapTimeoutMillis &&
                            (tap.position - previous.position).getDistance() <= doubleTapSlop
                        ) {
                            cameraController.zoomBy(
                                factor = 2.0,
                                pivot = ScreenPoint(tap.position.x.toDouble(), tap.position.y.toDouble()),
                                viewport = viewport(),
                            )
                            null
                        } else {
                            tap
                        }
                    } else {
                        null
                    }
                    break
                }
            }
        } catch (e: CancellationException) {
            if (mode == Mode.DRAWING) emit(down, PointerPhase.CANCEL, lastDrawingPosition)
            lastTap = null
            throw e
        }
    }
}

private enum class Mode { DRAWING, NAVIGATING, PINCHING, FINISHED }

private class Tap(val position: Offset, val timeMillis: Long)

private fun PointerType.toPointerKind(): PointerKind = when (this) {
    PointerType.Touch -> PointerKind.FINGER
    PointerType.Stylus -> PointerKind.STYLUS
    PointerType.Eraser -> PointerKind.ERASER
    PointerType.Mouse -> PointerKind.MOUSE
    else -> PointerKind.UNKNOWN
}
