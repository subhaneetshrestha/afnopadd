# ADR 0003: Keep Ink's wet-stroke surface at a fixed size, reuse it, and never detach it

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
  (results recorded in Task 16b in `docs/features/canvas-engine/tasks.md`).
- Window-level resizes (rotation without recreation, split screen, freeform windows, the soft
  keyboard if a future text tool resizes the window) can still hit the Ink race. Follow-ups:
  check Ink 1.1 when it is stable and retest the bare-resize repro; until then, prefer
  `adjustNothing`/overlay layouts in the editor and keep the activity recreated on configuration
  changes.
- A second Ink 1.0.0 defect surfaced in the same investigation: `CanvasInProgressStrokesRenderHelperV33`
  starts a `HandlerThread` per `InProgressStrokesView` and never quits it. The canvas therefore
  takes its Ink view from the activity (at first `SharedInkView`, now `InkHost`, below) instead of
  creating one per visit; `InkThreadLeakTest` guards it.
- Update 2026-10-21: the remaining crash has a reliable recipe. Removing the canvas (and with it
  Ink's view) within about half a second of drawing a stroke crashes Ink 1.0.0, either as
  `IllegalStateException: Unbalanced saves and restores` in
  `CanvasInProgressStrokesRenderHelperV33$Viewport.handleDraw` or as the libhwui SIGSEGV
  (3 of 4 runs). It does not depend on whether Ink finished the stroke or how long its hand-over
  debounce is, both measured. Ink hands a finished stroke over about 370–550 ms after pen-up.
  - Data is safe regardless: the router now builds the stroke from its own samples and gives it to
    the tool at pen-up, never asks Ink to finish it, and cancels Ink's in-progress copy once the
    dry layer has drawn it. Leaving or rotating right after a stroke can no longer lose it.
  - Tried and rejected: `requestHandoff()` and `handoffDebounceTimeMs` (both `@RestrictTo` the Ink
    library, and measured to make no difference to the hand-over delay); `flush()` (asynchronous,
    and blocked `onStop` until the activity hung in PAUSED).
- Update 2026-10-21, fix: `InkHost` attaches one `InProgressStrokesView` per activity on top of
  the activity's content (`addContentView`, window-sized, transparent, not clickable, hidden from
  accessibility) and never detaches it while the activity lives. A `SheetCanvas` borrows it and,
  when it leaves, only cancels and removes its strokes. Because the view covers the window, the
  router offsets each stroke's transform by the canvas's position relative to the view. A canvas
  with no activity, or a second canvas on screen at once, falls back to a view of its own.
  - Evidence: `InkHostTest.removingTheCanvasRightAfterAStrokeDoesNotCrash` (six strokes, each
    followed at once by removing the canvas) crashed with the old code (`Unbalanced saves and
    restores`) and passes with the fix; putting the detach back brings back the libhwui SIGSEGV
    (`0x63e8`). `wetInkIsDrawnUnderTheFinger` checks alignment on a real screenshot with touches
    sent through the window, so the overlay must let them through. `EditorFlowTest` now leaves the
    editor straight after drawing.
  - Still exposed: rotating within about half a second of drawing destroys the activity's window,
    and the Ink view with it; the stroke is safe, the crash is not ruled out. A wet stroke dragged
    over the toolbars draws over them until the pen lifts. Each activity recreation still costs Ink
    one thread. Confirm on a real device.
- The race and the thread leak should be reported upstream to the Jetpack Ink issue tracker with the bare-resize repro
  (owner's call: it is an outward-facing action).
