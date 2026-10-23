# Implementation Plan: Afnopadd v1

Status: **DRAFT, pending owner review** (2026-10-20). Source of truth for scope: `docs/product/spec.md` and the module specs; module ids from `docs/product/capability-map.md`. Tasks are tracked per feature in `docs/features/<module>/tasks.md`; the task list below links to each one and holds the checkpoints.

## Overview

Build Afnopadd v1 as a sequence of vertical slices. Phase 0 produces a walking skeleton within eleven tasks: open the app, draw a pen stroke with Jetpack Ink, kill the app, reopen, the stroke is still there, and pinch zoom already works. Later phases widen that path one capability at a time (library, the full tool set, selection and images, deep-zoom performance, sharing), so the app is demonstrable after every checkpoint. Each task is sized S or M, names its files, and carries acceptance criteria plus a verification command.

## Architecture decisions (from the approved specs)

- **Jetpack Ink 1.0.0 renders strokes; everything else is ours.** Ink gives wet and dry strokes with front-buffer latency; the element model, camera, tiles, tools, and files are Afnopadd code so Ink stays replaceable behind `canvas-engine`.
- **Double-precision world, per-element zoom frames.** The camera and anchors are `Double`; each element's geometry is `Float` in a frame scaled by `10^-frameLevel`, so Ink's fixed `epsilon` and float32 both stay happy at 100,000x.
- **SQLite per sheet, one transaction per gesture.** Room 2.8.5 with the bundled driver; each finished gesture is an atomic commit plus op-log rows, which doubles as undo history and crash recovery. No save timer.
- **Vendor-neutral stroke points.** Packed Float32 columns with a version byte, converted to Ink input batches at load. The `.afnopadd` package is a ZIP whose first entry is a `mimetype` file so stripped files still import.
- **Registry-driven toolbar from day one.** Tools, presets, and commands have stable string IDs; the default toolbar is a JSON asset parsed at startup, which is the hook `tool-config` and `plugins` later use.
- **Single Gradle module, package per capability**, manual dependency container, Navigation 3 with a saveable back stack.
- **Offline only, FOSS only.** No `INTERNET` permission; dependencies limited to AndroidX, Google, and the four approved third-party libraries.

## Dependency graph

```
T1 build plumbing ─┬─ T2 app shell skeleton ─────────────────────────────┐
                   ├─ T3 model primitives ─ T4 in-memory sheet ─ T5 Room ─ T6 persisted txns
                   │                              │                              │
                   └─ T7 camera math ─ T8 SheetCanvas ─ T9 Ink wet ─ T10 dry render ─ T11 pen + toolbar + editor
                                                                                  │
   Phase 1: T12 library index ─ T13 library screen ─ T14 renderer + thumbnails    │
            T15 stroke presets + thickness ─ T16 colours                          │  (all depend on T11)
            T17 index + hit-test + eraser ─ T18 shapes ─ T19 hand + zoom + settings
   Phase 2: T20 select basics ─ T21 select transforms ; T22 image import ─ T23 image render + crop ; T24 tiles ─ T25 deep zoom + perf
   Phase 3: T26 package format ─ T29 receive ; T27 image export ─ T28 PDF ; T30 settings/about/lifecycle ; T31 quality gates
```

## Task list

Each task lives in its feature's `tasks.md` (linked); this list is the order of work and holds the checkpoints.

### Phase 0: Foundation and walking skeleton

- [x] Task 1: Build plumbing and approved dependencies (2026-10-20) ([app-shell](../features/app-shell/tasks.md#task-1-build-plumbing-and-approved-dependencies))
- [x] Task 2: App shell skeleton (navigation, theme, container) (2026-10-20) ([app-shell](../features/app-shell/tasks.md#task-2-app-shell-skeleton-navigation-theme-container))
- [x] Task 3: Document model primitives and stroke point codec (2026-10-20) ([sheet-document](../features/sheet-document/tasks.md#task-3-document-model-primitives-and-stroke-point-codec))
- [x] Task 4: In-memory sheet with transactions and undo/redo (2026-10-20) ([sheet-document](../features/sheet-document/tasks.md#task-4-in-memory-sheet-with-transactions-and-undoredo))
- [x] Task 5: Room schema and SheetStore create/open (2026-10-20) ([sheet-document](../features/sheet-document/tasks.md#task-5-room-schema-and-sheetstore-createopen))
- [x] Task 6: Persisted transactions, op log, autosave, crash safety (2026-10-20) ([sheet-document](../features/sheet-document/tasks.md#task-6-persisted-transactions-op-log-autosave-crash-safety))

### Checkpoint A: Document foundation (reached 2026-10-20)

- [x] `.\gradlew.bat check` green
- [x] Round trip of 5,000 strokes and the crash test pass
- [x] Spec testing table updated if the Room-on-JVM spike failed (Room tests are instrumented; `docs/product/spec.md` testing table adjusted)

- [x] Task 7: Camera model and canvas math (2026-10-20) ([canvas-engine](../features/canvas-engine/tasks.md#task-7-camera-model-and-canvas-math))
- [x] Task 8: SheetCanvas composable with pan, pinch, background (2026-10-20) ([canvas-engine](../features/canvas-engine/tasks.md#task-8-sheetcanvas-composable-with-pan-pinch-background))
- [x] Task 9: Ink wet strokes (InProgressStrokesView) with frame transform (2026-10-20) ([canvas-engine](../features/canvas-engine/tasks.md#task-9-ink-wet-strokes-inprogressstrokes-with-frame-transform))
- [ ] Task 9b: Stylus tilt and motion prediction through MotionEvent interop (added 2026-10-20; before Checkpoint C) ([canvas-engine](../features/canvas-engine/tasks.md#task-9b-stylus-tilt-and-motion-prediction-through-motionevent-interop))
- [x] Task 10: Dry stroke rendering with same-frame handoff (2026-10-20) ([canvas-engine](../features/canvas-engine/tasks.md#task-10-dry-stroke-rendering-with-same-frame-handoff))
- [x] Task 11: Pen tool, registries, JSON toolbar, editor wiring (2026-10-20) ([drawing-tools](../features/drawing-tools/tasks.md#task-11-pen-tool-registries-json-toolbar-editor-wiring))

### Checkpoint B: Walking skeleton (automated parts reached 2026-10-20)

- [x] Draw, pinch zoom, undo, redo, leave, reopen: strokes intact on the API 36 emulator (`EditorFlowTest`, `SheetCanvasGestureTest`, `SheetPersistenceTest`)
- [x] The same on a device (owner, 2026-10-20: opened the editor, drew, used undo and redo; reopen and kill checks covered by tests)
- [x] `.\gradlew.bat check` green
- [x] Owner reviews before Phase 1


### Phase 1: Library and the full tool set

- [x] Task 12: Library index and SheetLibrary (2026-10-20) ([sheet-library](../features/sheet-library/tasks.md#task-12-library-index-and-sheetlibrary))
- [x] Task 13: Library screen (new, continue, open older) (2026-10-20) ([sheet-library](../features/sheet-library/tasks.md#task-13-library-screen-new-continue-open-older))
- [x] Task 14: Off-screen renderer and thumbnails (2026-10-20) ([canvas-engine](../features/canvas-engine/tasks.md#task-14-off-screen-renderer-and-thumbnails))
- [x] Task 15: Stroke tool family, presets, thickness (2026-10-20) ([drawing-tools](../features/drawing-tools/tasks.md#task-15-stroke-tool-family-presets-thickness))
- [x] Task 16: Colour palette, picker, recents (2026-10-20) ([drawing-tools](../features/drawing-tools/tasks.md#task-16-colour-palette-picker-recents))
- [x] Task 16b: Investigate the render-thread crash when leaving the editor (added 2026-10-20; done 2026-10-20, ADR 0003) ([canvas-engine](../features/canvas-engine/tasks.md#task-16b-investigate-the-render-thread-crash-when-leaving-the-editor))
- [x] Task 17: Spatial index, hit-testing, eraser (2026-10-20; device fps check pending) ([canvas-engine](../features/canvas-engine/tasks.md#task-17-spatial-index-hit-testing-eraser))
- [x] Task 18: Shape tools and rendering ([drawing-tools](../features/drawing-tools/tasks.md#task-18-shape-tools-and-rendering))
- [x] Task 19: Hand tool, zoom commands, finger-mode policy, settings store ([drawing-tools](../features/drawing-tools/tasks.md#task-19-hand-tool-zoom-commands-finger-mode-policy-settings-store))

### Checkpoint C: Every v1 tool usable

- [x] All tools reachable from the default toolbar with finger and stylus (`EditorFlowTest.everyToolIsReachableFromTheToolbarAndWorksWithFingerAndStylus`: each drawing tool commits with a finger and with real stylus events, the eraser erases with both; the hand tool in `theHandToolPansWithoutChangingTheSheet`)
- [x] State-machine tests for every tool pass; `.\gradlew.bat check` green (188 unit tests: `StrokeToolTest`, `EraserToolTest`, `ShapeToolTest`, `HandToolTest`, `InputPolicyTest`); full instrumented suite 117/117 on a cold `afnopadd36`, 2026-10-21, no native crash
- [ ] Owner tries the tool set on both reference devices
- Select (`tool:select`) is not a v1 tool yet; it arrives in Task 20.


### Phase 2: Selection, images, deep-zoom performance

- [x] Task 20: Select tool: selection, marquee, move, delete, duplicate, z-order ([drawing-tools](../features/drawing-tools/tasks.md#task-20-select-tool-selection-marquee-move-delete-duplicate-z-order))
- [x] Task 21: Select tool: scale and rotate handles ([drawing-tools](../features/drawing-tools/tasks.md#task-21-select-tool-scale-and-rotate-handles))
- [x] Task 22: Image import pipeline ([image-board](../features/image-board/tasks.md#task-22-image-import-pipeline))
- [x] Task 23: Image rendering, crop mode, arrange in row ([image-board](../features/image-board/tasks.md#task-23-image-rendering-crop-mode-arrange-in-row))
- [x] Task 24: Tile pyramid cache and level of detail ([canvas-engine](../features/canvas-engine/tasks.md#task-24-tile-pyramid-cache-and-level-of-detail))
- [x] Task 25: Deep-zoom hardening and performance fixture ([canvas-engine](../features/canvas-engine/tasks.md#task-25-deep-zoom-hardening-and-performance-fixture))

### Review of Tasks 18 to 25 (2026-10-21)

A deep review of `be04bca..HEAD` found no critical issues, seven required changes and some optional ones; all required ones are fixed (263 unit tests, Lint; full instrumented suite 154/154 on a cold `afnopadd36`):

- [x] Images in tiles: decodes capped at 2,048 px with a cache that holds one, and `OutOfMemoryError` and unchecked decoder errors fall back to placeholders or vector patches instead of killing the tile thread.
- [x] Tiles never draw a view with a stroke past 100x relative zoom; only the vector path draws the deep-zoom variants (`TileIndexTest`).
- [x] Scaling or rotating a selection previews the untouched elements under one `PreviewTransform`, so meshes are reused instead of rebuilt every move (`SelectToolTest`).
- [x] An image import runs in the app's scope, so rotation does not cancel it; a cancelled import stops between images and adds none (`ImageImporterTest`).
- [x] Shared-file input: image size must be finite and positive, turns must be finite, crops are clamped into the image or dropped; crop mode cannot throw on a bad crop (`ElementJsonCodecTest`).
- [x] A picked source over 200 MB is refused before it fills the cache (`ImageImporterTest`).
- [x] Deep-zoom stroke variants have a byte budget and are not evicted within the frame that draws them (`DryStrokeRenderingTest`).
- [x] Optional, fixed: the tile layer is released only with itself, and its rasteriser drops requests after shutdown; one `beginFrame` per frame for every pass; the tile budget never drops below 1.5 screens of tiles (`TileIndexTest`); failed image loads are not retried every frame; `InkHost` ignores a destroyed activity; saved styles no longer overwrite a change made before they load; a notes-only transform commits nothing; a shape preview keeps one id per drag; zoom steps build on an animation's target (`ZoomCommandsTest`).
- [ ] Left for later: `EditorScreen` recomposes on every drag move and has grown to about 380 lines (split crop and import wiring out); the image draw and rotation math is written in two to three places; thumbnails and exports draw images as placeholders (Task 27); the sketch cut-off differs slightly between tiles and vectors.


### Checkpoint D: Performance criteria

- [ ] Criteria 1 to 4 measured on both devices and recorded
- [ ] `.\gradlew.bat check` green
- [ ] Owner reviews numbers before Phase 3


### Phase 3: Sharing, settings, release readiness

- [ ] Task 26: Package format v1 export and import ([sheet-document](../features/sheet-document/tasks.md#task-26-package-format-v1-export-and-import))
- [ ] Task 27: Image export (PNG, JPEG) with Sharesheet and Save as ([sharing](../features/sharing/tasks.md#task-27-image-export-png-jpeg-with-sharesheet-and-save-as))
- [ ] Task 28: PDF export ([sharing](../features/sharing/tasks.md#task-28-pdf-export))
- [ ] Task 29: Share and receive packages ([sharing](../features/sharing/tasks.md#task-29-share-and-receive-packages))
- [ ] Task 30: Settings, About, lifecycle hardening ([app-shell](../features/app-shell/tasks.md#task-30-settings-about-lifecycle-hardening))
- [ ] Task 31: Quality gates, ADRs, release scaffolding ([app-shell](../features/app-shell/tasks.md#task-31-quality-gates-adrs-release-scaffolding))

### Checkpoint E: v1 complete

- [ ] All nine success criteria in `docs/product/spec.md` verified and recorded
- [ ] Manual release checklist done on both reference devices
- [ ] Sheet package format v1 frozen in `docs/format/afnopadd-v1.md`
- [ ] Owner sign-off

## Definition of done (every task)

`.\gradlew.bat check` passes; new logic has tests at the level the module spec names; no new Lint warnings; strings in `strings.xml`; spec updated if the task changed a decision (and an ADR added for an architectural one); one Conventional Commit per task referencing the task number.

## Parallelization

- After Task 4: Tasks 5 and 6 (persistence) can run alongside Tasks 7 to 10 (canvas) since they share only the model from Task 3.
- Phase 1: Tasks 12 to 14 (library) alongside Tasks 15 to 16 (stroke tools and colours); Tasks 17 to 19 after both.
- Phase 2: Tasks 20 and 21 alongside Tasks 22 and 23; Task 24 and 25 after all four.
- Phase 3: Task 26 alongside Tasks 27 and 28; Task 29 after 26; Task 30 anytime; Task 31 last.
- Must be sequential: anything touching the Room schema (Tasks 5, 6, 12, 26) and the default toolbar JSON (Tasks 11, 15 to 19).

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Room with the bundled driver may not run in plain JVM unit tests inside an Android module | Medium (slower test loop) | Task 5 starts with a 30-minute spike; if it fails, persistence tests go to `androidTest` and the spec's testing table is updated |
| Compose pointer API may not expose stylus tilt | Low | Task 9 spike; fall back to Ink's own input path or `pointerInteropFilter`; record in ADR |
| KSP 2.3.12 and Room plugin under AGP 9.4 built-in Kotlin | Medium (blocks Task 1) | Task 1 verifies with a trivial entity before anything else lands; known-good: kapt is forbidden, KSP is the supported path |
| Dry strokes render through `drawPath` below API 34, so thousands of strokes are slow on the API 29 phone | High for criterion 3 | Tiles (Task 24) are scheduled before the performance checkpoint, not after; the fixture sheet is built in Task 25 and reused |
| Float32 jitter at extreme relative zoom | Medium | Double camera plus frames (Task 7) with precision tests at 1e5; clip strokes whose relative zoom exceeds 1e4 |
| Content providers strip file names and MIME types when sharing packages | Medium | `mimetype` first ZIP entry plus sniffing (Task 26, 29) and the manual checklist across Gmail, Telegram, Files |
| Low-memory phones with large images | Medium | 4,096 px cap at import, hardware bitmaps, tile budget scaled by `memoryClass` |
| Ink 1.0 has no pencil; custom brushes are experimental | Low | Pencil is a pen preset (Task 15); revisit when Ink 1.1 is stable |
| F-Droid build with Ink's prebuilt native AAR | Low | AndroidX prebuilts from Google Maven are accepted by F-Droid; verified at Task 31 with a clean-clone build |
| Unit test coverage measurement under AGP 9 | Low | `enableUnitTestCoverage` in Task 1; if the report is unusable, coverage becomes a reviewed checklist item |

## Open questions

- None blocking. Two spikes (Task 5 Room-on-JVM, Task 9 tilt) may reorder tests but not scope.
