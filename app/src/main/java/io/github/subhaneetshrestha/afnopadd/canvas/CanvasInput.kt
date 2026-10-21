package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
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
    /** True while a second pointer is held during a gesture whose tool asked for it as a constrain modifier. */
    val constrain: Boolean = false,
)

/**
 * The zoom frame a stroke is drawn in: its anchor (the first input's world position), its level,
 * and the frame-to-screen transform at the moment the stroke started. Brush sizes and stroke
 * points are expressed in this frame's units.
 */
data class StrokeFrame(val anchor: WorldPoint, val level: Int, val transform: FrameTransform)

/** A stroke the user has just lifted: everything needed to make a `StrokeElement`, in the stroke's frame units. */
class FinishedStroke(
    val frame: StrokeFrame,
    val points: StrokePoints,
    val brush: BrushSpec,
    val kind: PointerKind,
)

/** Implemented by the active tool: receives single-pointer events the canvas does not use for navigation, and finished wet strokes. */
interface CanvasInputSink {
    fun onPointer(event: CanvasPointerEvent)

    /** Called at pen-up with the lifted stroke; the canvas draws whatever the tool commits from the next frame. */
    fun onWetStrokeFinished(stroke: FinishedStroke) = Unit
}

/** Sheet background drawn in screen space so it never aliases, whatever the zoom. */
enum class Background { PLAIN, GRID, DOTS }
