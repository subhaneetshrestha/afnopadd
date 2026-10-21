# Afnopadd v1 task list

Status: **DRAFT, pending owner review** (2026-10-20). Plan and rationale: `tasks/plan.md`. Paths below are relative to `app/src/main/java/io/github/subhaneetshrestha/afnopadd/` unless they start with `app/`, `docs/`, or `gradle/`. Tests mirror the package under `app/src/test/java/...` (JVM) or `app/src/androidTest/java/...` (device). Every task also meets the definition of done in the plan.

---

## Phase 0: Foundation and walking skeleton

## Task 1: Build plumbing and approved dependencies

**Description:** Add every approved dependency to the version catalog and wire the build so later tasks never touch Gradle: Jetpack Ink, motion prediction, Room with KSP and the bundled SQLite driver plus the Room Gradle plugin with a schema directory, kotlinx.serialization plugin and JSON, Coil 3, DataStore, Navigation 3, exifinterface, lifecycle-runtime-compose, and the test stack (JUnit 4, kotlinx-coroutines-test, AndroidX test, Compose UI test). Turn on Lint warnings-as-errors and unit-test coverage. Verify each unverified version against Maven metadata before pinning.

**Acceptance criteria:**
- [x] All dependencies resolve; a trivial `@Entity` compiles through KSP and its schema JSON appears in `app/schemas/`; a trivial `@Serializable` class round-trips in a unit test.
- [x] `lint { warningsAsErrors = true; abortOnError = true }` and `enableUnitTestCoverage = true` are set; no `org.jetbrains.kotlin.android` plugin anywhere.
- [x] `docs/adr/0001-dependency-baseline.md` lists each library, version, licence, and why.

**Verification:**
- [x] `.\gradlew.bat check` passes (includes the two trivial tests)
- [x] `.\gradlew.bat assembleDebug` passes
- [x] Manual: `.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath` shows no unexpected transitive network libraries (only okio, a file I/O library used by DataStore and Coil)

Done 2026-10-20. Bonus: the instrumented Room test (`SheetDatabaseTest`) passes on the API 36 emulator, which also settles the Task 5 spike.

**Dependencies:** None

**Files likely touched:**
- `gradle/libs.versions.toml`
- `build.gradle.kts`, `app/build.gradle.kts`
- `app/src/test/java/.../BuildSmokeTest.kt`
- `docs/adr/0001-dependency-baseline.md`

**Estimated scope:** Medium

---

## Task 2: App shell skeleton (navigation, theme, container)

**Description:** Create the application class, a manual `AppContainer` with stubbed services, Navigation 3 with a saveable back stack holding `Library` and `Editor(sheetId)` placeholders, the Material 3 theme (light/dark, no dynamic colour), and move `MainActivity` into the `shell` package. This is the frame every later slice plugs into.

**Acceptance criteria:**
- [x] Launching shows the Library placeholder; a placeholder button opens the Editor placeholder; system back returns; rotation keeps the current destination.
- [x] `AppContainer` is created once in `AfnopaddApp` and reachable from composables via a `CompositionLocal`.
- [x] Each screen-level composable has a `@Preview` that renders in light and dark.

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest` (destination serialization) and `NavigationTest` (launch, navigate-and-back, recreate) on the API 36 emulator
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: covered on the emulator by the instrumented recreate test; a hands-on rotate is part of the owner's Checkpoint B review

Done 2026-10-20.

**Dependencies:** Task 1

**Files likely touched:**
- `shell/AfnopaddApp.kt`, `shell/AppContainer.kt`, `shell/MainActivity.kt`, `shell/Navigation.kt`, `shell/theme/Theme.kt`
- `app/src/main/AndroidManifest.xml`

**Estimated scope:** Medium

---

## Task 3: Document model primitives and stroke point codec

**Description:** Implement the value types the whole app shares: `WorldPoint`, `WorldRect`, `ElementId` with a ULID generator, `FractionalIndex` with insert-between, the `Element` sealed hierarchy (`StrokeElement`, `ShapeElement`, `ImageElement`, reserved `NoteElement`, `UnknownElement`), `BrushSpec`, shape kinds and geometry, and the packed Float32 `StrokePoints` codec with a version byte, plus kotlinx.serialization serializers for element payloads with unknown-key tolerance.

**Acceptance criteria:**
- [x] ULIDs are 26 characters, Crockford base32, and monotonic within a millisecond; `FractionalIndex.between` never collides across 10,000 random inserts and sorts correctly.
- [x] `StrokePoints` encode/decode round-trips 1,000 random points bit-exactly, rejects unknown versions, and stores pressure, tilt, orientation, and time.
- [x] Element payload JSON with an extra unknown field decodes and re-encodes without losing the field (`UnknownElement` for unknown kinds).

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.document.*"` (29 tests)
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: none

Done 2026-10-20. Fractional indexes use an integer-plus-fraction scheme, so 10,000 appends or prepends stay under 8 characters.

**Dependencies:** Task 1

**Files likely touched:**
- `document/Ids.kt`, `document/FractionalIndex.kt`, `document/Geometry.kt`, `document/Element.kt`, `document/StrokePoints.kt`, `document/internal/json/ElementSerializers.kt`
- tests mirrored under `app/src/test/java/.../document/`

**Estimated scope:** Medium

---

## Task 4: In-memory sheet with transactions and undo/redo

**Description:** Implement `Sheet`, `Transaction`, `ElementSnapshot` (persistent map plus z-ordered list), diff generation (before/after per element), the undo/redo stack capped at 200 entries, and a `SheetWriter` hook that receives committed diffs (a no-op writer for now). Snapshots publish through `StateFlow` immediately on commit.

**Acceptance criteria:**
- [x] A transaction that adds, updates, and deletes elements yields exactly those diffs; an empty transaction is dropped; `undo` then `redo` restores the identical snapshot.
- [x] `canUndo`/`canRedo` flows are correct through 250 transactions (cap honoured, oldest dropped).
- [x] Diffs reach the `SheetWriter` in commit order with a shared transaction id and label.

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.document.*"` (14 transaction tests)
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: none

Done 2026-10-20. Undo and redo are written as inverse transactions (`kind`, `inverseOf`) so the op log stays append-only; a throwing or invalid transaction applies nothing.

**Dependencies:** Task 3

**Files likely touched:**
- `document/Sheet.kt`, `document/Transaction.kt`, `document/internal/SheetImpl.kt`, `document/internal/UndoStack.kt`, `document/internal/SheetWriter.kt`
- tests under `app/src/test/java/.../document/`

**Estimated scope:** Medium

---

## Task 5: Room schema and SheetStore create/open

**Description:** Define the per-sheet Room database (`meta`, `element`, `stroke_points`, `asset`, `op`) with the bundled SQLite driver in WAL mode, export its schema, and implement `SheetStore.create` and `open`, which build the initial snapshot from rows. The Room-on-JVM spike was answered in Task 1: Room's Android artifact needs a `Context`, so Room-backed tests live in `androidTest` and run on the emulator (ADR 0001); `SPEC.md`'s testing table already allows this.

**Acceptance criteria:**
- [x] `create` makes `sheets/<ulid>/sheet.db` with `schema_version = 1`; `open` returns a `Sheet` whose snapshot matches rows written directly through the DAO.
- [x] Schema JSON for version 1 is committed under `app/schemas/` and a migration test harness exists (empty for now).
- [x] Opening a 5,000-stroke database yields the first snapshot in under 1.0 s on the reference phone (instrumented timing test). Measured 795 ms cold on the API 36 emulator after moving common fields into columns, hand-decoding JSON, direct-buffer point decoding and parallel decode (was 1,435 ms); the phone number is still to be taken at Checkpoint B.

**Verification:**
- [x] Tests pass: `connectedDebugAndroidTest` (`SheetStoreTest`, `SheetDatabaseSchemaTest`)
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: inspect the schema JSON for the expected tables (meta, element, stroke_points, asset, op)

Done 2026-10-20. Lesson: keep the emulator awake (`adb shell svc power stayon true`) before instrumented runs; a sleeping screen stalled an activity-recreation test for eleven minutes.

**Dependencies:** Task 4

**Files likely touched:**
- `document/SheetStore.kt`, `document/internal/db/SheetDatabase.kt`, `document/internal/db/Entities.kt`, `document/internal/db/SheetDao.kt`, `document/internal/SheetStoreImpl.kt`
- `app/schemas/...`

**Estimated scope:** Medium

---

## Task 6: Persisted transactions, op log, autosave, crash safety

**Description:** Implement the real `SheetWriter`: a single-threaded dispatcher per sheet that turns each committed diff set into one SQLite transaction (element upserts, stroke blobs, op rows with `txn_id`, `undo_of` for inverse ops), `flush()` and `close()` with compaction of ops beyond the last 1,000 and tombstone purge, and the content-addressed `AssetStore`.

**Acceptance criteria:**
- [x] 5,000 stroke transactions written then reopened reproduce the snapshot exactly; the op table holds one row per diff.
- [x] Simulated failure in the middle of a transaction (a hook inside the database transaction that throws) leaves the database consistent with all earlier transactions present; later transactions still persist and `flush()` reports the failure once.
- [x] A 1,000-point stroke transaction reaches disk in under 30 ms median on the reference phone (instrumented timing test): 2.7 ms median, 9.3 ms max on the API 36 emulator with WAL and `synchronous=NORMAL`; `AssetStore.put` deduplicates by SHA-256 and `collectGarbage` removes unreferenced files.

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.document.*"` (FileAssetStore) plus `connectedDebugAndroidTest` (`SheetPersistenceTest`)
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: none

Done 2026-10-20. Close compacts the op log to the newest 1,000 rows, purges tombstones (stroke points follow through the foreign key) and garbage-collects assets no image references.

**Dependencies:** Task 5

**Files likely touched:**
- `document/internal/RoomSheetWriter.kt`, `document/AssetStore.kt`, `document/internal/FileAssetStore.kt`, `document/internal/db/SheetDao.kt`
- tests under `document/`

**Estimated scope:** Medium

---

## Checkpoint A: Document foundation (reached 2026-10-20)
- [x] `.\gradlew.bat check` green
- [x] Round trip of 5,000 strokes and the crash test pass
- [x] Spec testing table updated if the Room-on-JVM spike failed (Room tests are instrumented; `SPEC.md` testing table adjusted)

---

## Task 7: Camera model and canvas math

**Description:** Implement `Camera`, `CameraController` (pan, zoom around a pivot, animate, fit to bounds, clamps 0.001x to 100,000x), `CanvasMath` with double-precision world/screen conversion and `frameToScreenMatrix`, and the frame-level rule `floor(log10(zoom))`.

**Acceptance criteria:**
- [x] World to screen to world round trip error is under 1e-6 world units at zoom 1e5 with coordinates up to 1e6, over 10,000 random cases.
- [x] Zooming around a pivot keeps the pivot's world point fixed to within 1e-9; clamps hold at both ends.
- [x] The float matrix produced for an element carries translations no larger than the viewport size in pixels (for anchors inside the view).

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.canvas.*"` (18 tests)
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: none

Done 2026-10-20. Camera animation is left to the UI layer (`CanvasMath.interpolate` plus `CameraController.set`), picked up with the zoom commands in Task 19.

**Dependencies:** Task 3

**Files likely touched:**
- `canvas/Camera.kt`, `canvas/CameraController.kt`, `canvas/CanvasMath.kt`, `canvas/FrameLevel.kt`
- tests under `canvas/`

**Estimated scope:** Small

---

## Task 8: SheetCanvas composable with pan, pinch, background

**Description:** Build the `SheetCanvas` composable: a drawing surface that observes the sheet snapshot and camera, draws the background (plain, grid, dots) in screen space, draws element bounds as placeholder rectangles, routes single-pointer events to a `CanvasInputSink`, and gives two-pointer pinch and pan to the camera in every tool. Includes a dirty-region invalidation hook for later tasks.

**Acceptance criteria:**
- [x] Pinch keeps the point between the fingers stationary within 1 px; two-finger drag pans; double-tap zooms 2x toward the tap (in navigate mode).
- [x] Single-pointer events arrive at the sink with world coordinates, pressure, tool type, and phase.
- [x] Background grid does not alias when zooming through buckets (line count identical at 1x, 10x and 100x in a captured image).

**Verification:**
- [x] Tests pass: `SheetCanvasGestureTest` (six instrumented tests) on the API 36 emulator
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: pinch, pan, double-tap on a device, part of the owner's Checkpoint B review

Done 2026-10-20. Dirty-region invalidation is deferred to the tile cache (Task 24); Compose state invalidation covers the vector path until then.

**Dependencies:** Task 7, Task 2

**Files likely touched:**
- `canvas/SheetCanvas.kt`, `canvas/CanvasInput.kt`, `canvas/internal/GestureHandling.kt`, `canvas/internal/BackgroundPainter.kt`
- `androidTest/.../canvas/SheetCanvasGestureTest.kt`

**Estimated scope:** Medium

---

## Task 9: Ink wet strokes (InProgressStrokes) with frame transform

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

Done 2026-10-20. Ink is driven through `InProgressStrokesView`'s `StrokeInput` API from the Compose gesture handler, so pinch and finger policy stay in one place. Deviations from the spec recorded in ADR 0002 and picked up by Task 9b: strokes carry pressure but not tilt, and motion prediction is not wired yet.

---

## Task 9b: Stylus tilt and motion prediction through MotionEvent interop

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

**Description:** Render `StrokeElement`s with Ink: a `StrokeCache` builds Ink `Stroke` objects from `StrokePoints` and `BrushSpec` (mapping `ink:*` family ids to stock brushes) with an LRU memory budget; `CanvasStrokeRenderer.draw` is called with the exact per-element matrix; finished wet strokes are drawn dry in the same frame they are committed. Visible elements are selected by a simple bounds pass (the R-tree arrives in Task 17).

**Acceptance criteria:**
- [x] Strokes from the snapshot render at the camera's position; two consecutive captured frames after a wet stroke finishes both show it (no handoff gap).
- [x] Strokes drawn at 1x remain crisp when viewed zoomed in (width scales 25x with the matrix exact); the cache evicts under its byte budget and rebuilds transparently.
- [x] Snapshot changes invalidate the layer through Compose state; dirty-region invalidation is deferred to the tile cache (Task 24).

**Verification:**
- [x] Tests pass: `DryStrokeRenderingTest` (four instrumented tests) plus the rest of the canvas package
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: draw, zoom in, confirm crisp edges, part of the owner's Checkpoint B review

Done 2026-10-20. Ink's `CanvasStrokeRenderer.draw` positions a stroke with the canvas transform; the matrix argument only informs anti-aliasing, so the frame matrix is concatenated with `withMatrix` before each draw.

**Dependencies:** Task 9

**Files likely touched:**
- `canvas/internal/StrokeCache.kt`, `canvas/internal/BrushMapping.kt`, `canvas/internal/DryLayer.kt`, `canvas/SheetCanvas.kt`

**Estimated scope:** Medium

---

## Task 11: Pen tool, registries, JSON toolbar, editor wiring

**Description:** The slice that makes the skeleton walk: `ToolRegistry`, `Tool`/`ToolController`, `CommandRegistry` with `cmd:undo` and `cmd:redo`, the `PenTool` that turns `FinishedStroke` into a `StrokeElement` transaction, the `ToolbarSpec` model with JSON parsing, `ToolbarHost` rendering it, the default layout asset (pen, undo, redo), and an `EditorScreen` that opens a sheet through `SheetStore` (a fixed sheet directory until the library exists in Task 12) and composes canvas plus toolbar.

**Acceptance criteria:**
- [x] Drawing with the pen creates one `StrokeElement` per gesture with anchor and frame level from the stroke start; undo removes it, redo restores it.
- [x] The toolbar renders from `assets/tool_layout_default.json`; an unknown id in the file renders nothing and logs a warning.
- [x] Leaving the editor and reopening the sheet from disk shows all previous strokes (`EditorFlowTest`); durability across process death is covered by Task 6's persistence tests.

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.tools.*"` (pen tool, registries, layout JSON; 14 tests), `ToolbarHostTest` and `EditorFlowTest` on the API 36 emulator
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: draw, kill via `adb shell am kill`, reopen, part of the owner's Checkpoint B review

Done 2026-10-20.

**Dependencies:** Task 6, Task 10

**Files likely touched:**
- `tools/Tool.kt`, `tools/ToolRegistry.kt`, `tools/CommandRegistry.kt`, `tools/ToolbarSpec.kt`, `tools/ToolbarHost.kt`, `tools/pen/PenTool.kt`
- `app/src/main/assets/tool_layout_default.json`
- `shell/EditorScreen.kt`

**Estimated scope:** Medium

---

## Checkpoint B: Walking skeleton (automated parts reached 2026-10-20)
- [x] Draw, pinch zoom, undo, redo, leave, reopen: strokes intact on the API 36 emulator (`EditorFlowTest`, `SheetCanvasGestureTest`, `SheetPersistenceTest`)
- [x] The same on a device (owner, 2026-10-20: opened the editor, drew, used undo and redo; reopen and kill checks covered by tests)
- [x] `.\gradlew.bat check` green
- [x] Owner reviews before Phase 1

---

## Phase 1: Library and the full tool set

## Task 12: Library index and SheetLibrary

**Description:** Implement `library.db` (Room, `sheet` table) and `SheetLibrary`: create, markOpened, rename, duplicate (copy directory, new ULID, rewrite meta), delete to `files/trash/` with 7-day purge, register (for imports), and startup reconciliation against `files/sheets/` directories.

**Acceptance criteria:**
- [x] `sheets` emits in `lastOpenedAt` then `updatedAt` order; `lastOpened` emits the right sheet after `markOpened`.
- [x] Reconciliation adds a directory missing from the index and removes a row whose directory is gone.
- [x] Duplicate of a 5,000-stroke sheet completes in under 2 s (55 to 212 ms) and opens identically; delete then purge frees the directory; restore undoes a delete.

**Verification:**
- [x] Tests pass: `SheetTitlesTest` (JVM) and `SheetLibraryTest` (six instrumented tests)
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: none

Done 2026-10-20. `SheetStore` gained `create(..., id)`, `inspect` and `duplicate` so the library never touches sheet internals. Environment note: the full instrumented suite failed once at commit time because another session's app held the foreground on the shared emulator. The owner chose a dedicated AVD (`afnopadd36`, `emulator-5560`); the full suite of 41 instrumented tests is green there (open 5,000 strokes 482 ms, stroke commit 3.9 ms median, duplicate 71 ms).

**Dependencies:** Task 6

**Files likely touched:**
- `library/SheetLibrary.kt`, `library/SheetSummary.kt`, `library/internal/LibraryDatabase.kt`, `library/internal/SheetLibraryImpl.kt`
- `app/schemas/...`

**Estimated scope:** Medium

---

## Task 13: Library screen (new, continue, open older)

**Description:** Replace the Library placeholder with the real menu: "New sheet", "Continue" (title and thumbnail, hidden when empty), and "Open older" expanding to a thumbnail grid (list on narrow widths) with long-press rename, duplicate, delete (confirm plus 10-second undo snackbar), and share (stub until Task 29). Wire navigation so the editor opens the chosen sheet and marks it opened.

**Acceptance criteria:**
- [x] Fresh install shows only "New sheet"; after creating one, "Continue" appears and opens it.
- [x] Rename validates non-empty and 80 characters; delete shows confirmation and the undo snackbar restores the sheet.
- [x] A library of 500 generated sheets renders in under 500 ms (272 ms on the emulator) and scrolls to the last item; frame-rate measurement moves to the Task 25 performance suite.

**Verification:**
- [x] Tests pass: `LibraryScreenTest` (seven tests), `EditorFlowTest` and `NavigationTest` rewritten for the real menu; full suite of 48 instrumented tests green on `afnopadd36`
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: full flow on a device (owner)

Done 2026-10-20. The editor now opens sheets by id from `files/sheets/`, marks them opened, and refreshes their library entry on close; the app reconciles the library at startup. Timing assertions in instrumented tests use `PerfBudget` (2x on emulators) because emulator timings swing with host load; raw numbers are still logged.

**Dependencies:** Task 12, Task 11

**Files likely touched:**
- `library/LibraryScreen.kt`, `library/internal/LibraryViewModel.kt`, `library/internal/SheetCard.kt`
- `shell/Navigation.kt`, `shell/EditorScreen.kt`

**Estimated scope:** Medium

---

## Task 14: Off-screen renderer and thumbnails

**Description:** Implement `Renderer.renderBitmap` (software canvas, internal tiling above 100 megapixels) and `Renderer.renderTo` in `canvas-engine`, then generate a 512 px WebP thumbnail when the editor closes a sheet whose `updatedAt` is newer than its thumbnail, and show thumbnails in the library through Coil.

**Acceptance criteria:**
- [x] `renderBitmap` of a region matches the on-screen rendering of the same region with under 1 percent pixel difference (0.003 percent measured).
- [x] A 100-megapixel request succeeds through tiling (`renderTiled`, 25 tiles of 2,048 px) without a hardware-canvas size exception; single bitmaps above 100 megapixels are refused with a message pointing to tiling.
- [x] Closing an edited sheet writes `thumb.webp` (longest edge 512 px); the library shows it within one refresh.

**Verification:**
- [x] Tests pass: `RenderGeometryTest` (JVM), `SoftwareRendererTest` and the thumbnail case in `EditorFlowTest`; full suite of 51 instrumented tests green on `afnopadd36`
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: thumbnails visible in the library (owner)

Done 2026-10-20. Thumbnails are only re-rendered when the sheet changed since the last one; an empty sheet has none.

**Dependencies:** Task 13, Task 10

**Files likely touched:**
- `canvas/Renderer.kt`, `canvas/internal/SoftwareRenderer.kt`
- `library/internal/ThumbnailRefresher.kt`, `library/internal/SheetCard.kt`
- `shell/EditorScreen.kt`

**Estimated scope:** Medium

---

## Task 15: Stroke tool family, presets, thickness

**Description:** Add pencil, brush, and highlighter as tools backed by `ToolPreset` and `ParamValue`, the `BrushSpec` mapping (pencil = pressure pen at 60 percent opacity and 0.6x size; brush = marker at 2x; highlighter = highlighter at 40 percent), `CurrentStyle` state with per-tool memory, thickness presets (five) and slider (0.5 to 64 px) with screen-pixel to frame-unit conversion, and the tool options panel. Add the tools to the default layout.

**Acceptance criteria:**
- [x] Each tool produces a `StrokeElement` whose `BrushSpec` matches the mapping table in `SPEC-drawing-tools.md`.
- [x] A 3 px thickness chosen at 10x zoom stores a frame-unit size that renders 3 px on screen at that zoom and 0.3 px at 1x.
- [x] Switching tools restores each tool's last thickness and colour (styles live in the app container for the session).

**Verification:**
- [x] Tests pass: `StrokeToolTest` (seven JVM tests), `StrokeOptionsBarTest`, and the pencil case in `EditorFlowTest`; full suite of 53 instrumented tests green on `afnopadd36`
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: draw with all four stroke tools at several zooms (owner)

Done 2026-10-20. The four tools are `ToolPreset`s of one `StrokeTool`, which is the shape user presets will take in the tool-config phase. Styles reset when the app restarts; persisting them belongs with the settings store in Task 19.

**Dependencies:** Task 11

**Files likely touched:**
- `tools/presets/ToolPreset.kt`, `tools/presets/BuiltInPresets.kt`, `tools/stroke/StrokeTools.kt`, `tools/CurrentStyle.kt`, `tools/options/ThicknessOptions.kt`
- `app/src/main/assets/tool_layout_default.json`

**Estimated scope:** Medium

---

## Task 16: Colour palette, picker, recents

**Description:** Build the colour UI: 16-swatch palette, HSV picker with hex entry, eight recents persisted in DataStore, highlighter alpha forcing, and the toolbar colour button that opens it.

**Acceptance criteria:**
- [x] Picking a swatch or entering a hex value updates the active tool's style and the next stroke uses it.
- [x] Recents hold the last eight distinct colours across process restarts (DataStore `settings`).
- [x] The highlighter ignores the chosen alpha and applies its fixed 40 percent (covered in `StrokeToolTest`).

**Verification:**
- [x] Tests pass: `ColorLogicTest` (JVM: hex, HSV, palette, recents), `ColorPickerTest`, `RecentColorsStoreTest` (on device, because DataStore's atomic rename cannot overwrite on a Windows host JVM), `StrokeOptionsBarTest`, and the palette case in `EditorFlowTest`; full suite of 59 instrumented tests green
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: pick colours, restart, confirm recents (owner)

**Dependencies:** Task 15

**Files likely touched:**
- `tools/color/ColorPalette.kt`, `tools/color/ColorPicker.kt`, `tools/color/RecentColors.kt`, `tools/ToolbarHost.kt`

**Estimated scope:** Medium

Done 2026-10-20.

---

## Task 16b: Investigate the render-thread crash when leaving the editor

**Description:** About 1 in 5 runs of `EditorFlowTest` on `afnopadd36` ends with a native crash: `SIGSEGV` in `libhwui.so` (`DrawFrameTask::postAndWait`, fault address `0x63e8`) on the app's RenderThread, while a test leaves the editor. The backtrace has no Ink frames, but the editor hosts Ink's `InProgressStrokesView`, whose API 33+ path drives its own `HardwareRenderer`/`SurfaceControl`; the suspicion is that it is released while a frame is still posted. First seen during Task 16; not seen in about eight earlier full runs, though more tests now leave the editor. Find whether it reproduces on a real device, whether Ink 1.1 changes it, and whether flushing or cancelling Ink strokes before release prevents it.

**Acceptance criteria:**
- [x] Root cause identified and recorded in `docs/adr/0003-fixed-size-ink-surface.md`: Jetpack Ink 1.0.0's `InProgressStrokesView` races when resized (its render helper draws into a torn-down viewport: `IllegalStateException` in `CanvasInProgressStrokesRenderHelperV33`, or the libhwui SIGSEGV). The editor resized it on every tool switch that showed or hid the options bar.
- [x] Avoided: the canvas keeps a fixed size and the bars float over it (`switchingToolsNeverResizesTheCanvas` guards it). `EditorFlowTest` then ran ten times in a row with no native crash and no failure (70 test executions, 2026-10-20).
- [x] Residual, with a recipe since 2026-10-21: removing the editor within about half a second of drawing crashed Ink 1.0.0 (3 of 4 runs; ADR 0003 has the details and what was ruled out). Fixed 2026-10-21: `InkHost` keeps one Ink view attached over the activity's content for the activity's life and canvases borrow it, so leaving the editor never detaches it. `InkHostTest` reproduced the crash before the fix and passes after; restoring the detach brings the SIGSEGV back. Left open: rotating within half a second of drawing (the window goes), and a real-device check (owner). Earlier note: the same RenderThread SIGSEGV still appears on an emulator that has run many test cycles. With the emulator at 6.5–6.9 GB of host memory, the shell tests crashed in 2 of 2 runs, on the group-3 commit as well as with later changes (bisected). On a cold-booted emulator, 2 shell runs and a full 87-test run had no crash. Re-entering the editor 30 times did not reproduce it. Next: confirm on a real device before Checkpoint C; if it reproduces there, try Ink 1.1 once it is stable.
- [x] Found on the way: Ink starts a render-helper thread per view and never quits it (30 threads after 30 editor visits). Fixed with `SharedInkView` (one view per activity); `InkThreadLeakTest` guards it; numbers in `docs/perf/PERF.md`.

Done 2026-10-20.

Evidence from Task 17 (2026-10-20): with three emulators on a 15 GB host the crash hit every `EditorFlowTest` run, mid-editor as well as on leaving (fault address `0x63e8` or `0x6348`, frames of 1 to 3 s); after the other emulators were shut down and `afnopadd36` cold-booted, three runs had no crash. The router listener leak fixed in the performance review (replaced `WetStrokeRouter`s stayed registered on the Ink view) may also have contributed.

**Dependencies:** Task 16. **Priority:** before Checkpoint C.

---

## Task 17: Spatial index, hit-testing, eraser

**Description:** Add an incremental R-tree over element bounds to `canvas-engine`, `HitTester` (point with tolerance, rectangle), switch the dry layer's visibility pass to the index, and implement the eraser tool that removes whole strokes (Ink geometry intersection) and shapes (geometry hit) touched during a gesture as one transaction. Images are not erasable.

**Acceptance criteria:**
- [x] R-tree queries match brute force on 50,000 random rectangles, including after moves and deletes (`RTreeTest`, incremental and bulk-loaded).
- [x] Dragging the eraser across three strokes and one image removes the three strokes in one undoable transaction and leaves the image (`EraserToolTest`).
- [ ] Visibility pass through the index keeps a 5,000-stroke sheet panning at 60 fps on the API 34+ device. Emulator, warm debug build: culling median 0.42 ms per frame, p95 1.0 ms, index build 56 ms (cold 158 ms), sync after one stroke 0.07 ms (`ElementIndexPerfTest`). Device fps pending the owner's tablet.

**Verification:**
- [x] Tests pass: `.\gradlew.bat check` (129 unit tests); instrumented `InkStrokeHitTestTest` (4), `ElementIndexPerfTest` and `EditorFlowTest` (including the eraser flow, twice) green on `afnopadd36`. Earlier runs on an overloaded host were killed by the Task 16b crash.
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: erase on a device

Notes: Ink 1.0.0's `ImmutableParallelogram.fromSegmentAndPadding` gives wrong mesh coverage, so the mesh test uses two triangles. `ElementSnapshot.changedSince` lets the index follow a sheet per transaction instead of diffing all elements.

**Dependencies:** Task 15

**Files likely touched:**
- `canvas/SpatialIndex.kt`, `canvas/internal/RTree.kt`, `canvas/HitTester.kt`, `canvas/internal/DryLayer.kt`
- `tools/eraser/EraserTool.kt`
- `app/src/main/assets/tool_layout_default.json`

**Estimated scope:** Medium

---

## Task 18: Shape tools and rendering

**Description:** Render `ShapeElement`s (line, rectangle, ellipse, arrow with heads scaled to width) through the element matrix, and add the four shape tools with drag-to-create, live overlay preview, and the second-pointer constrain modifier (square, circle, 45-degree lines). No fill.

**Acceptance criteria:**
- [x] Each shape tool commits one `ShapeElement` per drag with geometry in frame units and the current colour and thickness (`ShapeToolTest`; `EditorFlowTest.theRectangleToolDrawsOneRectangle` end to end).
- [x] With the constrain modifier held, rectangles are exact squares and lines snap to 45-degree multiples (`ShapeToolTest` on scripted input; `SheetCanvasGestureTest` checks that a second finger during a shape drag constrains rather than pinches).
- [x] Shapes stay crisp at 50x and hit-test through `HitTester` (`ShapeRenderingTest`: a 2-unit line is 100 px wide at 50x with a sharp edge; `HitTesterTest` covers arrowheads; outline geometry is shared in `ShapeOutline`).

**Verification:**
- [x] Tests pass: `.\gradlew.bat check` (162 unit tests, Lint); full instrumented suite 103/103 on a cold `afnopadd36` (2026-10-21), including `ShapeRenderingTest` (screen vs `SoftwareRenderer` within 1 percent for all four shapes). Mutation checks: dropping the dry-layer shape draw fails all 5 rendering tests; ignoring `secondPointerConstrains` fails the gesture test.
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: draw all four shapes (owner)

Notes: the preview is drawn by `SheetCanvas` from `ToolController.previewElements` after the dry layer, not by a separate overlay file. The second-pointer modifier conflicted with "two pointers always belong to the camera"; resolved in SPEC-canvas-engine decision 9 (only a second pointer that lands after a shape drag starts constrains). With four more tools the default toolbar row is wider than a phone (about 510 dp on a 411 dp screen), so rows now scroll sideways; undo and redo sit past the edge on phones until the layout is revisited.

**Dependencies:** Task 17

**Files likely touched:**
- `canvas/internal/ShapePainter.kt`
- `tools/shape/ShapeTools.kt`, `tools/shape/ShapePreviewOverlay.kt`
- `app/src/main/assets/tool_layout_default.json`

**Estimated scope:** Medium

Done 2026-10-21.

---

## Task 19: Hand tool, zoom commands, finger-mode policy, settings store

**Description:** Add the hand tool (one pointer pans, two pinch-zoom, never edits), the zoom commands (`cmd:zoom-in`, `cmd:zoom-out`, `cmd:zoom-fit`, `cmd:zoom-100`) with the zoom readout, `SettingsRepository` on DataStore (`fingerMode`, `toolbarPlacement`, `defaultBackground`, `keepScreenOn`, `leftHanded`), the persisted "stylus seen" flag, and the input policy that decides whether a single finger draws or pans.

**Acceptance criteria:**
- [ ] The hand tool changes only the camera; the sheet's transaction count stays zero during its use.
- [ ] In AUTO mode a finger draws until a stylus pointer is seen; afterwards a finger pans and the stylus draws; forcing either mode in settings overrides this.
- [ ] Zoom in/out step by 1.5x around the viewport centre with animation; fit frames all content with padding; 100 percent returns to 1x.

**Verification:**
- [ ] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.tools.*"` (hand tool, policy) and `"*.shell.*"` (settings)
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: hand tool and finger policy on both devices

**Dependencies:** Task 17

**Files likely touched:**
- `tools/hand/HandTool.kt`, `tools/commands/ZoomCommands.kt`, `tools/InputPolicy.kt`, `tools/ZoomReadout.kt`
- `shell/settings/SettingsRepository.kt`
- `app/src/main/assets/tool_layout_default.json`

**Estimated scope:** Medium

---

## Checkpoint C: Every v1 tool usable
- [ ] All tools reachable from the default toolbar with finger and stylus
- [ ] State-machine tests for every tool pass; `.\gradlew.bat check` green
- [ ] Owner tries the tool set on both reference devices

---

## Phase 2: Selection, images, deep-zoom performance

## Task 20: Select tool: selection, marquee, move, delete, duplicate, z-order

**Description:** Implement the select tool's first half: tap selects the topmost hit element, drag on empty space draws a marquee, long-press adds to the selection, dragging a selection moves it (anchors change, one transaction per gesture), and the context bar with `cmd:delete-selection`, `cmd:duplicate-selection`, `cmd:bring-to-front`, `cmd:send-to-back` using fractional indexes.

**Acceptance criteria:**
- [ ] Tap, marquee, and long-press-add produce the expected selection sets on a scripted fixture.
- [ ] Moving a selection of a stroke, a shape, and an image updates all three anchors in one undoable transaction.
- [ ] Bring to front and send to back reorder only the selected elements' `z` values.

**Verification:**
- [ ] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.tools.select.*"`
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: select, move, reorder on a device

**Dependencies:** Task 18

**Files likely touched:**
- `tools/select/SelectTool.kt`, `tools/select/SelectionState.kt`, `tools/select/SelectionOverlay.kt`, `tools/select/SelectionCommands.kt`
- `app/src/main/assets/tool_layout_default.json`

**Estimated scope:** Medium

---

## Task 21: Select tool: scale and rotate handles

**Description:** Add uniform-scale and rotate handles to the selection overlay. Scaling multiplies stroke points and shape geometry about the selection centre; rotation sets `rotationDeg` on images and shapes and rotates stroke points; both are one transaction per gesture and work on multi-selections.

**Acceptance criteria:**
- [ ] Scaling a selection by 2x doubles distances between element anchors and stroke extents within float tolerance; the inverse gesture restores them.
- [ ] Rotating 90 degrees produces the expected geometry for strokes, shapes, and images (unit tests with fixtures).
- [ ] Handles stay a constant screen size at every zoom.

**Verification:**
- [ ] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.tools.select.*"`
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: scale and rotate mixed selections

**Dependencies:** Task 20

**Files likely touched:**
- `tools/select/TransformHandles.kt`, `tools/select/Transforms.kt`, `tools/select/SelectTool.kt`

**Estimated scope:** Medium

---

## Task 22: Image import pipeline

**Description:** Implement `rememberImagePicker` (photo picker for up to 20 images, documents UI, system camera via `TakePicture` with a FileProvider) and `ImageImporter`: stream bytes into `AssetStore`, decode bounds, downsample to at most 4,096 px, bake EXIF orientation, re-encode (JPEG 90 opaque, lossless WebP with alpha), and create `ImageElement`s placed at the viewport centre (single) or in a row (many) at 60 percent of the shorter viewport side.

**Acceptance criteria:**
- [ ] Fixtures: an EXIF-rotated JPEG imports upright; a PNG with alpha keeps transparency; a 48-megapixel JPEG lands at 4,096 px; an unsupported file yields a per-file error and the batch continues.
- [ ] Importing 20 photos of 12 megapixels completes in under 10 s on the reference phone with peak memory under 300 MB.
- [ ] No image or storage permission appears in the merged manifest.

**Verification:**
- [ ] Tests pass: `connectedDebugAndroidTest` for the images package; unit tests for placement math
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: import from gallery, files, and camera

**Dependencies:** Task 6, Task 11

**Files likely touched:**
- `images/ImagePicker.kt`, `images/ImageImporter.kt`, `images/internal/ImageNormalizer.kt`, `images/internal/Placement.kt`
- `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/file_paths.xml`

**Estimated scope:** Medium

---

## Task 23: Image rendering, crop mode, arrange in row

**Description:** Render `ImageElement`s in `canvas-engine` through Coil 3 requests sized per zoom bucket with hardware bitmaps and bilinear filtering, honouring crop and rotation; add crop mode (adjustable rectangle in image space, stored not applied) and `cmd:arrange-row` for selected images, plus `cmd:image-import` wired into the toolbar.

**Acceptance criteria:**
- [ ] Images appear at the right place and size at every zoom; zooming out requests smaller bitmaps (verified through Coil's request sizes in a test).
- [ ] Crop mode stores a rectangle and rendering shows only the cropped area; reset crop restores the full image.
- [ ] Arrange in row lines up selected images left to right with 16 dp gaps in one transaction.

**Verification:**
- [ ] Tests pass: unit tests for crop clamping and arrangement; `connectedDebugAndroidTest` rendering test
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: import, crop, arrange on a device

**Dependencies:** Task 22, Task 20

**Files likely touched:**
- `canvas/internal/ImagePainter.kt`
- `images/crop/CropMode.kt`, `images/commands/ImageCommands.kt`
- `app/src/main/assets/tool_layout_default.json`

**Estimated scope:** Medium

---

## Task 24: Tile pyramid cache and level of detail

**Description:** Implement the raster tile cache: 256 px tiles keyed by bucket and tile coordinates, rendered on a background thread into hardware bitmaps, LRU budget of 10 percent of `memoryClass` (floor 48 MB, cap 128 MB), invalidation by dirty rectangles after each transaction, and the vector-versus-tile threshold (strokes thinner than 0.5 px or more than 2,000 visible elements). Release all tiles on `onTrimMemory`.

**Acceptance criteria:**
- [ ] On the API 29 phone, panning the 5,000-stroke fixture zoomed out stays at 60 fps with tiles and drops below without them (recorded numbers).
- [ ] A transaction invalidates only the tiles overlapping its dirty rectangle (unit test on the tile index).
- [ ] Tile memory never exceeds the budget; backgrounding releases tiles within one second.

**Verification:**
- [ ] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.canvas.tiles.*"`; `connectedDebugAndroidTest` frame timing
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: zoomed-out pan on the API 29 phone

**Dependencies:** Task 23

**Files likely touched:**
- `canvas/internal/tiles/TileCache.kt`, `canvas/internal/tiles/TileRasterizer.kt`, `canvas/internal/tiles/TileIndex.kt`, `canvas/internal/DryLayer.kt`

**Estimated scope:** Medium

---

## Task 25: Deep-zoom hardening and performance fixture

**Description:** Finish the zoom story: re-tessellate strokes at a finer `epsilon` when the relative zoom to their frame exceeds 100x (cached per bucket), clip strokes beyond 1e4 relative zoom, build the shared performance fixture (5,000 strokes, 20 images) under `androidTest`, and add timing tests for criteria 1 to 4 of `SPEC.md` (open time, stroke frame drops, pan/zoom fps, zoom range). Known hotspot from Task 6: `SheetImpl.apply` copies the whole element map per transaction (O(n) allocation per gesture); measure it on the fixture and, if it matters, propose `kotlinx.collections.immutable` (JetBrains, Apache-2.0, needs the owner's dependency approval) for a persistent map with structural sharing.

**Acceptance criteria:**
- [ ] A stroke drawn at 1x viewed at 100,000x shows smooth edges and does not jitter relative to neighbours (screenshot comparison across two frames).
- [ ] Opening the fixture takes under 1.5 s to first interactive frame; a 1,000-point stroke on it drops no frames; pan/zoom holds 60 fps at every bucket on both devices.
- [ ] Results are written to `docs/checklists/perf-2026.md` with device names and dates.

**Verification:**
- [ ] Tests pass: `.\gradlew.bat connectedDebugAndroidTest` (perf suite)
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: zoom to the cap and draw

**Dependencies:** Task 24

**Files likely touched:**
- `canvas/internal/StrokeCache.kt`, `canvas/internal/DryLayer.kt`
- `androidTest/.../fixtures/PerfFixture.kt`, `androidTest/.../canvas/PerfTest.kt`
- `docs/checklists/perf-2026.md`

**Estimated scope:** Medium

---

## Checkpoint D: Performance criteria
- [ ] Criteria 1 to 4 measured on both devices and recorded
- [ ] `.\gradlew.bat check` green
- [ ] Owner reviews numbers before Phase 3

---

## Phase 3: Sharing, settings, release readiness

## Task 26: Package format v1 export and import

**Description:** Implement the `.afnopadd` package in `sheet-document`: ZIP writer with the uncompressed `mimetype` first entry, `manifest.json`, `strokes/<id>.bin`, `assets/<sha256>`, optional thumbnail; `importPackage` creating a new sheet ULID while keeping element IDs, recording origin, verifying asset hashes, rejecting newer `formatVersion`; the JSON-to-JSON migration scaffold; golden files; and `docs/format/afnopadd-v1.md`.

**Acceptance criteria:**
- [ ] Export then import of the perf fixture yields equal element sets by id, geometry, style, and z-order, and byte-identical assets; export completes in under 5 s.
- [ ] Truncated, corrupted, and newer-version packages return `ImportResult.Error` with specific reasons and leave no partial sheet directory.
- [ ] Golden package `v1/basic.afnopadd` is committed and its import matches a checked-in expected snapshot.

**Verification:**
- [ ] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.document.pkg.*"`
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: open the ZIP in a file manager and confirm the entry order

**Dependencies:** Task 6, Task 22

**Files likely touched:**
- `document/internal/pkg/PackageWriter.kt`, `document/internal/pkg/PackageReader.kt`, `document/internal/pkg/Migrations.kt`, `document/SheetStore.kt`
- `app/src/test/resources/packages/v1/basic.afnopadd`, `docs/format/afnopadd-v1.md`

**Estimated scope:** Medium

---

## Task 27: Image export (PNG, JPEG) with Sharesheet and Save as

**Description:** Add the export options sheet (region: all content, current view, selection; scale 1x, 2x, 4x, or target long edge; PNG or JPEG; background white, sheet, or transparent for PNG), `Exporter.exportImage` through `Renderer.renderBitmap` with the 100-megapixel cap and message, `ShareLauncher` using `FileProvider` with `ACTION_SEND` and a chooser, `CreateDocument` for Save as, and cleanup of `cache/exports/` after 24 hours.

**Acceptance criteria:**
- [ ] Exporting the fixture at 4x completes in under 5 s and the PNG opens in the system viewer; JPEG output uses quality 90.
- [ ] Share hands the file to another app through a content URI with read permission; Save as writes through the documents UI.
- [ ] Requests above 100 megapixels are refused with a message suggesting PDF or a smaller region.

**Verification:**
- [ ] Tests pass: unit tests for option validation and pixel-cap math; `connectedDebugAndroidTest` export test
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: share a PNG to Gmail and save to Files

**Dependencies:** Task 14, Task 20

**Files likely touched:**
- `sharing/Exporter.kt`, `sharing/internal/ImageExporter.kt`, `sharing/ShareLauncher.kt`, `sharing/ui/ExportOptionsSheet.kt`
- `app/src/main/res/xml/file_paths.xml`, `app/src/main/AndroidManifest.xml`

**Estimated scope:** Medium

---

## Task 28: PDF export

**Description:** Implement `Exporter.exportPdf` with `PdfDocument`: one page sized to the region at 72 points per world unit up to 14,400 points per side, larger regions tiled into pages with 1 cm overlap, strokes and shapes drawn through `Renderer.renderTo` so they stay vector, images embedded at stored resolution.

**Acceptance criteria:**
- [ ] The fixture exports to a PDF that opens in Android's viewer; zooming shows vector stroke edges, not pixels.
- [ ] A region wider than 14,400 points produces the expected page count with overlaps (unit test on the tiling math).
- [ ] Transparent background is not offered for PDF; white and sheet background render correctly.

**Verification:**
- [ ] Tests pass: unit tests for page tiling; `connectedDebugAndroidTest` PDF generation
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: open the PDF and zoom

**Dependencies:** Task 27

**Files likely touched:**
- `sharing/internal/PdfExporter.kt`, `sharing/ui/ExportOptionsSheet.kt`

**Estimated scope:** Small

---

## Task 29: Share and receive packages

**Description:** Wire `cmd:share-sheet` and `cmd:save-sheet-as` (flush, export package, Sharesheet or Save as), the intent filters (`ACTION_VIEW` and `ACTION_SEND` for the custom MIME, `application/zip`, `application/octet-stream`, and `*/*` with the `.afnopadd` path pattern), stream sniffing for the `mimetype` entry, the import dialog with preview and "Import as copy" on duplicate origin, registration in the library, and opening the editor on success. Replace the library's share stub.

**Acceptance criteria:**
- [ ] A package sent from device A through a messaging app imports on device B after a tap, opens in the editor, and edits on B do not affect A.
- [ ] A content URI with no display name and `application/octet-stream` still imports; a random ZIP is rejected with a specific message.
- [ ] Importing a package whose origin already exists shows the duplicate dialog and creates a separate copy when confirmed.

**Verification:**
- [ ] Tests pass: `connectedDebugAndroidTest` intent-delivery tests; unit tests for sniffing
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: `docs/checklists/release.md` sharing section across Gmail, Telegram, Files, Quick Share

**Dependencies:** Task 26, Task 13

**Files likely touched:**
- `sharing/PackageReceiver.kt`, `sharing/internal/PackageSniffer.kt`, `sharing/ui/ImportDialog.kt`, `sharing/internal/ShareCommands.kt`
- `shell/MainActivity.kt`, `app/src/main/AndroidManifest.xml`, `library/LibraryScreen.kt`

**Estimated scope:** Medium

---

## Task 30: Settings, About, lifecycle hardening

**Description:** Build the Settings screen (finger mode, toolbar placement, left-handed mode, default background, keep screen on) and About (licence, version, auto-generated third-party notices), flush and thumbnail on `onStop`, restoration of sheet, camera, tool, and selection after rotation and process death, predictive back, and split-screen sanity.

**Acceptance criteria:**
- [ ] Every setting persists across restarts and takes effect without reopening the editor.
- [ ] `ActivityScenario.recreate()` and the "Don't keep activities" path restore the same sheet, camera, tool, and selection. (Partly done early from the 2026-10-20 review: `EditorSessions` keeps the open sheet, its undo history and camera across recreation, and the camera and tool are saved for process death; `NavigationTest.recreationKeepsTheOpenSheetItsCameraAndTheTool`. Still open: selection, which arrives with Task 20, and a test of the "Don't keep activities" path.)
- [ ] About shows the GPL-3.0-or-later text link and every dependency from the catalog with its licence.
- [ ] From the 2026-10-20 review: a process-kill crash test (SPEC success criterion 3), with a writer in a separate debug-only process killed mid-transaction, then a reopen that checks integrity; and instrumented shell tests running against an isolated container or cleared `filesDir` instead of the real one.

**Verification:**
- [ ] Tests pass: Compose UI tests for settings; instrumented recreate tests
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: rotate, background, split-screen

**Dependencies:** Task 19, Task 29

**Files likely touched:**
- `shell/settings/SettingsScreen.kt`, `shell/about/AboutScreen.kt`, `shell/EditorScreen.kt`, `shell/MainActivity.kt`, `shell/Navigation.kt`

**Estimated scope:** Medium

---

## Task 31: Quality gates, ADRs, release scaffolding

**Description:** Wire the coverage report and confirm pure-logic packages reach 80 percent (or list gaps with owners), write `docs/checklists/release.md`, add `metadata/en-US/` skeleton (short and full description, changelog placeholder), ADRs for the SQLite-per-sheet, own-stroke-format, and zoom-frame decisions, a README with build instructions and licence, and verify a clean-clone `assembleRelease` succeeds with no signing material in the repository.

**Acceptance criteria:**
- [ ] `.\gradlew.bat check` and the coverage task run green; the report shows at least 80 percent lines for `document`, `canvas` math, and `tools` state machines.
- [ ] ADRs 0003 to 0005 exist and link to the spec sections they implement.
- [ ] A fresh clone builds `assembleRelease` with only the wrapper and the SDK; the release manifest has no `INTERNET` permission.

**Verification:**
- [ ] Tests pass: `.\gradlew.bat check`
- [ ] Build succeeds: `.\gradlew.bat assembleRelease` from a clean clone
- [ ] Manual check: walk `docs/checklists/release.md` on both devices

**Dependencies:** Task 30

**Files likely touched:**
- `docs/checklists/release.md`, `docs/adr/0003-sqlite-per-sheet.md`, `docs/adr/0004-stroke-point-format.md`, `docs/adr/0005-zoom-frames.md`
- `metadata/en-US/*`, `README.md`, `app/build.gradle.kts`

**Estimated scope:** Medium

---

## Checkpoint E: v1 complete
- [ ] All nine success criteria in `SPEC.md` verified and recorded
- [ ] Manual release checklist done on both reference devices
- [ ] Sheet package format v1 frozen in `docs/format/afnopadd-v1.md`
- [ ] Owner sign-off
