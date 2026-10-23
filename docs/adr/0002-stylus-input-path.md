# ADR 0002: Stylus input path for wet strokes

Date: 2026-10-20. Status: accepted, with two follow-ups.

## Context

`docs/features/canvas-engine/spec.md` requires Jetpack Ink for wet strokes, two-pointer gestures to always drive the camera, and a per-pointer-kind policy deciding whether a single finger draws or pans. Ink offers two ways in: the `InProgressStrokes` composable, which handles touch itself and starts a stroke for every pointer it sees, or `InProgressStrokesView`, which the app drives explicitly through either `MotionEvent`s or `StrokeInput`s. Task 9's spike asked whether Compose's pointer API exposes stylus tilt.

## Decision

- Gestures stay in Compose (`CanvasGestures.kt`): the handler built in Task 8 decides pinch, pan and routing, and forwards single-pointer events to a `WetStrokeRouter`.
- The router drives `InProgressStrokesView` through its `StrokeInput` API, not `MotionEvent`s. Stroke inputs are in the stroke's zoom frame (anchor at the first sample, level `floor(log10(zoom))`), and the view receives the frame-to-screen transform as `strokeToViewTransform`, so Ink renders in the right place without ever seeing a MotionEvent.
- Finished strokes are converted to `StrokePoints` and handed to the tool as `FinishedStroke`, then removed from the Ink view.

## Consequences

- Pinch-to-zoom cancels a stroke in progress cleanly, and finger policy is a one-line function, because the app owns every pointer decision.
- Compose `PointerInputChange` exposes pressure but not tilt or orientation (spike result). Strokes therefore carry pressure only. **Follow-up:** add a `pointerInteropFilter` alongside the gesture modifier to read `AXIS_TILT` and `AXIS_ORIENTATION` from the raw `MotionEvent` and attach them to the stroke input.
- Motion prediction (`androidx.input:input-motionprediction`) needs `MotionEvent`s to feed its predictor. It is not wired yet. **Follow-up:** record raw events through the same interop filter, call `MotionEventPredictor.predict()`, and pass the result as the `prediction` batch of `addToStroke`.
- Both follow-ups are tracked as Task 9b in `docs/features/canvas-engine/tasks.md` and must land before the stroke-feel review at Checkpoint C.
