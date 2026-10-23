# Tasks: canvas-engine

Spec: [spec.md](spec.md). Roadmap, checkpoints and the definition of done: [docs/product/roadmap.md](../../product/roadmap.md). Task numbers are global across features; paths in a task are relative to `app/src/main/java/io/github/subhaneetshrestha/afnopadd/` unless they start with `app/`, `docs/` or `gradle/`.

| Task | Title | Status |
|---|---|---|
| [7](#task-7-camera-model-and-canvas-math) | Camera model and canvas math | Done 2026-10-20 |
| [8](#task-8-sheetcanvas-composable-with-pan-pinch-background) | SheetCanvas composable with pan, pinch, background | Done 2026-10-20 |
| [9](#task-9-ink-wet-strokes-inprogressstrokes-with-frame-transform) | Ink wet strokes (InProgressStrokes) with frame transform | Done 2026-10-20 |
| [9b](#task-9b-stylus-tilt-and-motion-prediction-through-motionevent-interop) | Stylus tilt and motion prediction through MotionEvent interop | Not started |
| [10](#task-10-dry-stroke-rendering-with-same-frame-handoff) | Dry stroke rendering with same-frame handoff | Done 2026-10-20 |
| [14](#task-14-off-screen-renderer-and-thumbnails) | Off-screen renderer and thumbnails | Done 2026-10-20 |
| [16b](#task-16b-investigate-the-render-thread-crash-when-leaving-the-editor) | Investigate the render-thread crash when leaving the editor | Done 2026-10-20 |
| [17](#task-17-spatial-index-hit-testing-eraser) | Spatial index, hit-testing, eraser | Done 2026-10-20 (device fps check pending) |
| [24](#task-24-tile-pyramid-cache-and-level-of-detail) | Tile pyramid cache and level of detail | Done 2026-10-21 (phone fps pending) |
| [25](#task-25-deep-zoom-hardening-and-performance-fixture) | Deep-zoom hardening and performance fixture | Done 2026-10-21 on the emulator (device numbers pending) |

---

## Task 7: Camera model and canvas math

**Status:** Done 2026-10-20

**Description:** Implement `Camera`, `CameraController` (pan, zoom around a pivot, animate, fit to bounds, clamps 0.001x to 100,000x), `CanvasMath` with double-precision world/screen conversion and `frameToScreenMatrix`, and the frame-level rule `floor(log10(zoom))`.

**Acceptance criteria:**
- [x] World to screen to world round trip error is under 1e-6 world units at zoom 1e5 with coordinates up to 1e6, over 10,000 random cases.
- [x] Zooming around a pivot keeps the pivot's world point fixed to within 1e-9; clamps hold at both ends.
- [x] The float matrix produced for an element carries translations no larger than the viewport size in pixels (for anchors inside the view).

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.canvas.*"` (18 tests)
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: none

**Dependencies:** Task 3

**Files likely touched:**
- `canvas/Camera.kt`, `canvas/CameraController.kt`, `canvas/CanvasMath.kt`, `canvas/FrameLevel.kt`
- tests under `canvas/`

**Estimated scope:** Small

**Notes:**

Camera animation is left to the UI layer (`CanvasMath.interpolate` plus `CameraController.set`), picked up with the zoom commands in Task 19.

---

## Task 8: SheetCanvas composable with pan, pinch, background

**Status:** Done 2026-10-20

**Description:** Build the `SheetCanvas` composable: a drawing surface that observes the sheet snapshot and camera, draws the background (plain, grid, dots) in screen space, draws element bounds as placeholder rectangles, routes single-pointer events to a `CanvasInputSink`, and gives two-pointer pinch and pan to the camera in every tool. Includes a dirty-region invalidation hook for later tasks.

**Acceptance criteria:**
- [x] Pinch keeps the point between the fingers stationary within 1 px; two-finger drag pans; double-tap zooms 2x toward the tap (in navigate mode).
- [x] Single-pointer events arrive at the sink with world coordinates, pressure, tool type, and phase.
- [x] Background grid does not alias when zooming through buckets (line count identical at 1x, 10x and 100x in a captured image).

**Verification:**
- [x] Tests pass: `SheetCanvasGestureTest` (six instrumented tests) on the API 36 emulator
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: pinch, pan, double-tap on a device, part of the owner's Checkpoint B review

**Dependencies:** Task 7, Task 2

**Files likely touched:**
- `canvas/SheetCanvas.kt`, `canvas/CanvasInput.kt`, `canvas/internal/GestureHandling.kt`, `canvas/internal/BackgroundPainter.kt`
- `androidTest/.../canvas/SheetCanvasGestureTest.kt`

**Estimated scope:** Medium

**Notes:**

Dirty-region invalidation is deferred to the tile cache (Task 24); Compose state invalidation covers the vector path until then.

---

## Task 9: Ink wet strokes (InProgressStrokes) with frame transform

**Status:** Done 2026-10-20

**Description:** Integrate Jetpack Ink's `InProgressStrokes` into `SheetCanvas`: feed it the inverse frame-to-screen transform for the current frame level, enable motion prediction, honour pointer cancellation, and hand finished strokes to the sink as `FinishedStroke` (inputs in frame units plus the frame level and anchor). Begin with a spike on stylus tilt availability through Compose and record the outcome in an ADR.

**Acceptance criteria:**
- [x] Drawing with finger or stylus shows a wet stroke that follows the pointer; `ACTION_CANCEL` removes it without a `FinishedStroke` (Compose delivers cancel as a pre-consumed release; handled).
- [x] `FinishedStroke` carries inputs whose coordinates are in frame units consistent with `CanvasMath` for the camera at stroke start.
- [x] `docs/adr/0002-stylus-input-path.md` records whether Compose exposes tilt and which input path was chosen.

**Verification:**
- [x] Tests pass: `WetStrokeTest` (four instrumented tests) on the API 36 emulator
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: stroke feel with stylus and finger on both reference devices, part of the owner's Checkpoint B review

**Dependencies:** Task 8

**Files likely touched:**
- `canvas/internal/WetStrokeRouter.kt`, `canvas/internal/InkBridge.kt`, `canvas/CanvasInput.kt`, `canvas/SheetCanvas.kt`
- `docs/adr/0002-stylus-input-path.md`

**Estimated scope:** Medium

**Notes:**

Ink is driven through `InProgressStrokesView`'s `StrokeInput` API from the Compose gesture handler, so pinch and finger policy stay in one place. Deviations from the spec recorded in ADR 0002 and picked up by Task 9b: strokes carry pressure but not tilt, and motion prediction is not wired yet.

---

## Task 9b: Stylus tilt and motion prediction through MotionEvent interop

**Status:** Not started

**Description:** Close the two gaps left by Task 9: read `AXIS_TILT` and `AXIS_ORIENTATION` from raw `MotionEvent`s with a `pointerInteropFilter` placed next to the gesture modifier and attach them to stroke inputs, and feed the same events to `androidx.input.motionprediction.MotionEventPredictor` so each `addToStroke` call carries a prediction batch.

**Acceptance criteria:**
- [ ] On a stylus device (or an injected stylus `MotionEvent` with tilt), finished strokes carry non-zero tilt and orientation that match the injected values.
- [ ] `addToStroke` receives a non-empty prediction batch on devices where the predictor produces one; finger-mode and pinch behaviour from Task 8 are unchanged.
- [ ] ADR 0002 is updated to "follow-ups done".

**Verification:**
- [ ] Tests pass: instrumented test injecting stylus events with tilt; existing canvas tests still pass
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: stroke latency with and without prediction on the stylus tablet

**Dependencies:** Task 9

**Files likely touched:**
- `canvas/internal/CanvasGestures.kt`, `canvas/internal/WetStrokeRouter.kt`, `canvas/internal/InkBridge.kt`
- `docs/adr/0002-stylus-input-path.md`

**Estimated scope:** Small

---

## Task 10: Dry stroke rendering with same-frame handoff

**Status:** Done 2026-10-20

**Description:** Render `StrokeElement`s with Ink: a `StrokeCache` builds Ink `Stroke` objects from `StrokePoints` and `BrushSpec` (mapping `ink:*` family ids to stock brushes) with an LRU memory budget; `CanvasStrokeRenderer.draw` is called with the exact per-element matrix; finished wet strokes are drawn dry in the same frame they are committed. Visible elements are selected by a simple bounds pass (the R-tree arrives in Task 17).

**Acceptance criteria:**
- [x] Strokes from the snapshot render at the camera's position; two consecutive captured frames after a wet stroke finishes both show it (no handoff gap).
- [x] Strokes drawn at 1x remain crisp when viewed zoomed in (width scales 25x with the matrix exact); the cache evicts under its byte budget and rebuilds transparently.
- [x] Snapshot changes invalidate the layer through Compose state; dirty-region invalidation is deferred to the tile cache (Task 24).

**Verification:**
- [x] Tests pass: `DryStrokeRenderingTest` (four instrumented tests) plus the rest of the canvas package
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: draw, zoom in, confirm crisp edges, part of the owner's Checkpoint B review

**Dependencies:** Task 9

**Files likely touched:**
- `canvas/internal/StrokeCache.kt`, `canvas/internal/BrushMapping.kt`, `canvas/internal/DryLayer.kt`, `canvas/SheetCanvas.kt`

**Estimated scope:** Medium

**Notes:**

Ink's `CanvasStrokeRenderer.draw` positions a stroke with the canvas transform; the matrix argument only informs anti-aliasing, so the frame matrix is concatenated with `withMatrix` before each draw.

---

## Task 14: Off-screen renderer and thumbnails

**Status:** Done 2026-10-20

**Description:** Implement `Renderer.renderBitmap` (software canvas, internal tiling above 100 megapixels) and `Renderer.renderTo` in `canvas-engine`, then generate a 512 px WebP thumbnail when the editor closes a sheet whose `updatedAt` is newer than its thumbnail, and show thumbnails in the library through Coil.

**Acceptance criteria:**
- [x] `renderBitmap` of a region matches the on-screen rendering of the same region with under 1 percent pixel difference (0.003 percent measured).
- [x] A 100-megapixel request succeeds through tiling (`renderTiled`, 25 tiles of 2,048 px) without a hardware-canvas size exception; single bitmaps above 100 megapixels are refused with a message pointing to tiling.
- [x] Closing an edited sheet writes `thumb.webp` (longest edge 512 px); the library shows it within one refresh.

**Verification:**
- [x] Tests pass: `RenderGeometryTest` (JVM), `SoftwareRendererTest` and the thumbnail case in `EditorFlowTest`; full suite of 51 instrumented tests green on `afnopadd36`
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: thumbnails visible in the library (owner)

**Dependencies:** Task 13, Task 10

**Files likely touched:**
- `canvas/Renderer.kt`, `canvas/internal/SoftwareRenderer.kt`
- `library/internal/ThumbnailRefresher.kt`, `library/internal/SheetCard.kt`
- `shell/EditorScreen.kt`

**Estimated scope:** Medium

**Notes:**

Thumbnails are only re-rendered when the sheet changed since the last one; an empty sheet has none.

---

## Task 16b: Investigate the render-thread crash when leaving the editor

**Status:** Done 2026-10-20

**Description:** About 1 in 5 runs of `EditorFlowTest` on `afnopadd36` ends with a native crash: `SIGSEGV` in `libhwui.so` (`DrawFrameTask::postAndWait`, fault address `0x63e8`) on the app's RenderThread, while a test leaves the editor. The backtrace has no Ink frames, but the editor hosts Ink's `InProgressStrokesView`, whose API 33+ path drives its own `HardwareRenderer`/`SurfaceControl`; the suspicion is that it is released while a frame is still posted. First seen during Task 16; not seen in about eight earlier full runs, though more tests now leave the editor. Find whether it reproduces on a real device, whether Ink 1.1 changes it, and whether flushing or cancelling Ink strokes before release prevents it.

**Acceptance criteria:**
- [x] Root cause identified and recorded in `docs/adr/0003-fixed-size-ink-surface.md`: Jetpack Ink 1.0.0's `InProgressStrokesView` races when resized (its render helper draws into a torn-down viewport: `IllegalStateException` in `CanvasInProgressStrokesRenderHelperV33`, or the libhwui SIGSEGV). The editor resized it on every tool switch that showed or hid the options bar.
- [x] Avoided: the canvas keeps a fixed size and the bars float over it (`switchingToolsNeverResizesTheCanvas` guards it). `EditorFlowTest` then ran ten times in a row with no native crash and no failure (70 test executions, 2026-10-20).
- [x] Residual, with a recipe since 2026-10-21: removing the editor within about half a second of drawing crashed Ink 1.0.0 (3 of 4 runs; ADR 0003 has the details and what was ruled out). Fixed 2026-10-21: `InkHost` keeps one Ink view attached over the activity's content for the activity's life and canvases borrow it, so leaving the editor never detaches it. `InkHostTest` reproduced the crash before the fix and passes after; restoring the detach brings the SIGSEGV back. Left open: rotating within half a second of drawing (the window goes), and a real-device check (owner). Earlier note: the same RenderThread SIGSEGV still appears on an emulator that has run many test cycles. With the emulator at 6.5–6.9 GB of host memory, the shell tests crashed in 2 of 2 runs, on the group-3 commit as well as with later changes (bisected). On a cold-booted emulator, 2 shell runs and a full 87-test run had no crash. Re-entering the editor 30 times did not reproduce it. Next: confirm on a real device before Checkpoint C; if it reproduces there, try Ink 1.1 once it is stable.
- [x] Found on the way: Ink starts a render-helper thread per view and never quits it (30 threads after 30 editor visits). Fixed with `SharedInkView` (one view per activity); `InkThreadLeakTest` guards it; numbers in `docs/perf/PERF.md`.

**Dependencies:** Task 16. **Priority:** before Checkpoint C.

**Notes:**

Evidence from Task 17 (2026-10-20): with three emulators on a 15 GB host the crash hit every `EditorFlowTest` run, mid-editor as well as on leaving (fault address `0x63e8` or `0x6348`, frames of 1 to 3 s); after the other emulators were shut down and `afnopadd36` cold-booted, three runs had no crash. The router listener leak fixed in the performance review (replaced `WetStrokeRouter`s stayed registered on the Ink view) may also have contributed.

---

## Task 17: Spatial index, hit-testing, eraser

**Status:** Done 2026-10-20 (device fps check pending)

**Description:** Add an incremental R-tree over element bounds to `canvas-engine`, `HitTester` (point with tolerance, rectangle), switch the dry layer's visibility pass to the index, and implement the eraser tool that removes whole strokes (Ink geometry intersection) and shapes (geometry hit) touched during a gesture as one transaction. Images are not erasable.

**Acceptance criteria:**
- [x] R-tree queries match brute force on 50,000 random rectangles, including after moves and deletes (`RTreeTest`, incremental and bulk-loaded).
- [x] Dragging the eraser across three strokes and one image removes the three strokes in one undoable transaction and leaves the image (`EraserToolTest`).
- [ ] Visibility pass through the index keeps a 5,000-stroke sheet panning at 60 fps on the API 34+ device. Emulator, warm debug build: culling median 0.42 ms per frame, p95 1.0 ms, index build 56 ms (cold 158 ms), sync after one stroke 0.07 ms (`ElementIndexPerfTest`). Device fps pending the owner's tablet.

**Verification:**
- [x] Tests pass: `.\gradlew.bat check` (129 unit tests); instrumented `InkStrokeHitTestTest` (4), `ElementIndexPerfTest` and `EditorFlowTest` (including the eraser flow, twice) green on `afnopadd36`. Earlier runs on an overloaded host were killed by the Task 16b crash.
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: erase on a device

**Dependencies:** Task 15

**Files likely touched:**
- `canvas/SpatialIndex.kt`, `canvas/internal/RTree.kt`, `canvas/HitTester.kt`, `canvas/internal/DryLayer.kt`
- `tools/eraser/EraserTool.kt`
- `app/src/main/assets/tool_layout_default.json`

**Estimated scope:** Medium

**Notes:**

Ink 1.0.0's `ImmutableParallelogram.fromSegmentAndPadding` gives wrong mesh coverage, so the mesh test uses two triangles. `ElementSnapshot.changedSince` lets the index follow a sheet per transaction instead of diffing all elements.

---

## Task 24: Tile pyramid cache and level of detail

**Status:** Done 2026-10-21 (phone fps pending)

**Description:** Implement the raster tile cache: 256 px tiles keyed by bucket and tile coordinates, rendered on a background thread into hardware bitmaps, LRU budget of 10 percent of `memoryClass` (floor 48 MB, cap 128 MB), invalidation by dirty rectangles after each transaction, and the vector-versus-tile threshold (strokes thinner than 0.5 px or more than 2,000 visible elements). Release all tiles on `onTrimMemory`.

**Acceptance criteria:**
- [ ] On the API 29 phone, panning the 5,000-stroke fixture zoomed out stays at 60 fps with tiles and drops below without them (recorded numbers). Emulator (`TileLayerTest`, UI-thread cost of recording a frame): vectors median 119 ms, tiles median 0.97 ms (p95 2.7 ms). Phone pending (owner).
- [x] A transaction invalidates only the tiles overlapping its dirty rectangle (`TileIndexTest` on the store; `TileLayerTest.aTransactionRedrawsOnlyTheTilesItTouched` on the fixture: one tile patched and redrawn, the rest kept).
- [x] Tile memory never exceeds the budget; backgrounding releases tiles within one second (`TileIndexTest` eviction; `TileLayerTest`: a pan stays under a 150-tile budget, and stopping the activity drops all tiles at once).

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.canvas.tiles.*"` (9); `TileLayerTest` (5, frame timing, invalidation, budget, background release, and tiles against a direct render: 0 of 401 inked 20 px cells differ). Mutation checks: no invalidation and no release on stop each fail a test. Full suite 147/147 on a cold `afnopadd36`.
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: zoomed-out pan on the API 29 phone (owner)

**Dependencies:** Task 23

**Files likely touched:**
- `canvas/internal/tiles/TileCache.kt`, `canvas/internal/tiles/TileRasterizer.kt`, `canvas/internal/tiles/TileIndex.kt`, `canvas/internal/DryLayer.kt`

**Estimated scope:** Medium

**Notes:**

tiles are 256 px at the power-of-two level nearest the zoom (drawn at 0.71x to 1.41x), rendered on one background thread by `RegionPainter` (now shared with `SoftwareRenderer`) into a software bitmap and copied to a hardware one; empty tiles cost nothing. A tile that is missing, invalidated, or under an element a gesture is hiding (the eraser's preview) is drawn as vectors clipped to its square, so the view is always complete and current; at most 8 renders are queued, centre first, and renders a pan made pointless are skipped. Tiles are used above 2,000 visible elements or when most visible strokes are under 0.5 px. Images in tiles are decoded synchronously on the tile thread. Tiles are dropped on `ON_STOP` and on `onTrimMemory(UI_HIDDEN)` or above.

---

## Task 25: Deep-zoom hardening and performance fixture

**Status:** Done 2026-10-21 on the emulator (device numbers pending)

**Description:** Finish the zoom story: re-tessellate strokes at a finer `epsilon` when the relative zoom to their frame exceeds 100x (cached per bucket), clip strokes beyond 1e4 relative zoom, build the shared performance fixture (5,000 strokes, 20 images) under `androidTest`, and add timing tests for criteria 1 to 4 of `docs/product/spec.md` (open time, stroke frame drops, pan/zoom fps, zoom range). Known hotspot from Task 6: `SheetImpl.apply` copies the whole element map per transaction (O(n) allocation per gesture); measure it on the fixture and, if it matters, propose `kotlinx.collections.immutable` (JetBrains, Apache-2.0, needs the owner's dependency approval) for a persistent map with structural sharing.

**Acceptance criteria:**
- [x] A stroke drawn at 1x viewed at 100,000x shows smooth edges and does not jitter relative to neighbours (`DeepZoomRenderingTest`: the cap of a 10,000-unit stroke sits on the exact pixel on every row, and a neighbour drawn at 100,000x keeps its gap across a 64 px pan; without the deep-zoom variants the edge is 16 px off).
- [ ] Opening the fixture takes under 1.5 s to first interactive frame; a 1,000-point stroke on it drops no frames; pan/zoom holds 60 fps at every bucket on both devices. Emulator (`PerfTest`): 435 ms; 4.1 % of frames one vsync late while drawing; worst 9.1 ms median UI-thread frame across every bucket. Devices pending (owner).
- [x] Results are written to `docs/checklists/perf-2026.md` with device names and dates (emulator now; device rows pending).

**Verification:**
- [x] Tests pass: perf suite `PerfTest` (3), `DeepZoomRenderingTest`, `TileLayerTest` (5), `RendererPerfTest` (4); `.\gradlew.bat check` (256 unit tests, including `DeepZoomTest`). Mutation checks: without deep-zoom variants, and with only the finer one, the deep-zoom test fails.
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: zoom to the cap and draw (owner)

**Dependencies:** Task 24

**Files likely touched:**
- `canvas/internal/StrokeCache.kt`, `canvas/internal/DryLayer.kt`
- `androidTest/.../fixtures/PerfFixture.kt`, `androidTest/.../canvas/PerfTest.kt`
- `docs/checklists/perf-2026.md`

**Estimated scope:** Medium

**Notes:**

measuring the fixture found three tile problems, now fixed (`docs/perf/PERF.md`): tiles tessellated every tiny stroke (now polylines, as on screen), the 2,000-element threshold let 330 mesh strokes cost 22 ms a frame (now 200), and nearest-level tiles blurred when enlarged (now the level at or above the zoom, so they only shrink). The `SheetImpl.apply` map copy costs 1.66 ms per commit at 5,000 strokes, once per gesture, so the persistent-collection dependency is not proposed. The fixture's strokes have 60 points, not the spec's 150. Criterion 2 is close to its emulator allowance (5 %) and needs the device.
