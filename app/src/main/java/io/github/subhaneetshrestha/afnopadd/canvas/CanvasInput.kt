package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.WorldPoint

/** What a single pointer does on the canvas; decided per pointer kind by the finger-mode policy in `drawing-tools`. */
enum class PointerRole { DRAW, NAVIGATE }

enum class PointerKind { FINGER, STYLUS, ERASER, MOUSE, UNKNOWN }

enum class PointerPhase { DOWN, MOVE, UP, CANCEL }

/** One pointer sample, already converted to world coordinates for the camera in effect when it happened. */
data class CanvasPointerEvent(
    val pointerId: Long,
    val kind: PointerKind,
    val phase: PointerPhase,
    val screen: ScreenPoint,
    val world: WorldPoint,
    val pressure: Float,
    val timeMillis: Long,
)

/** Implemented by the active tool: receives the single-pointer events the canvas does not use for navigation. */
fun interface CanvasInputSink {
    fun onPointer(event: CanvasPointerEvent)
}

/** Sheet background drawn in screen space so it never aliases, whatever the zoom. */
enum class Background { PLAIN, GRID, DOTS }
