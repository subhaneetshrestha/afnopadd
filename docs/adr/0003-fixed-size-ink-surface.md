# ADR 0003: Keep Ink's wet-stroke surface at a fixed size, and reuse it

Date: 2026-10-20. Status: accepted (Task 16b).

## Context

From Task 16 on, editor tests intermittently died with a native crash on the app's RenderThread:
`SIGSEGV` in `libhwui.so`, `DrawFrameTask::postAndWait`, fault address `0x63e8` (also seen:
`0x6348`, `0x20`). Investigation on the API 36 emulator:

- On a healthy, cold-booted emulator `EditorFlowTest` still crashed in 4 of 10 runs, always
  about 450 ms after the eraser tool was selected and before any erasing.
- A pen-to-pencil switch after each of 15 strokes never crashed (3 runs); a pen-to-eraser switch
  crashed in 2 of 3 runs. Both replace the input router; only the eraser switch resizes the canvas,
  because the eraser has no stroke options and the options bar disappears.
- A bare `SheetCanvas` whose height toggles after each stroke, with no tool code at all, crashed
  natively in 1 of 3 runs and in another failed with a Java exception from Ink itself:
  `IllegalStateException: Required value was null` at
  `androidx.ink.authoring.internal.CanvasInProgressStrokesRenderHelperV33$Viewport.afterDrawInModifiedRegion`.

So Jetpack Ink 1.0.0's `InProgressStrokesView` (its API 33+ front-buffered render helper) can
draw into a viewport that a resize has already torn down. The earlier crashes on leaving the
editor and in other tests were the same race; an overloaded host made it far more frequent.

## Decision

- The editor never resizes the canvas while in use: `SheetCanvas` fills the editor and the
  options bar and toolbar float over it in a `Surface` (which also stops touches from reaching
  the canvas through the bars). Tool switches therefore cannot resize the Ink view.
- `EditorFlowTest.switchingToolsNeverResizesTheCanvas` asserts the canvas bounds stay identical
  across tool switches, so a layout change cannot quietly bring the crash back.

## Consequences

- With the fix, ten consecutive `EditorFlowTest` runs on the emulator had no native crash
  (results recorded in Task 16b in `tasks/todo.md`).
- Window-level resizes (rotation without recreation, split screen, freeform windows, the soft
  keyboard if a future text tool resizes the window) can still hit the Ink race. Follow-ups:
  check Ink 1.1 when it is stable and retest the bare-resize repro; until then, prefer
  `adjustNothing`/overlay layouts in the editor and keep the activity recreated on configuration
  changes.
- A second Ink 1.0.0 defect surfaced in the same investigation: `CanvasInProgressStrokesRenderHelperV33`
  starts a `HandlerThread` per `InProgressStrokesView` and never quits it. The canvas therefore
  takes its Ink view from `SharedInkView` (one per activity, reused across editor visits, dropped
  on `ON_DESTROY`) instead of creating one per visit; `InkThreadLeakTest` guards it.
- The race and the thread leak should be reported upstream to the Jetpack Ink issue tracker with the bare-resize repro
  (owner's call: it is an outward-facing action).
