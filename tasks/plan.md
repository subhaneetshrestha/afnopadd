# Implementation Plan: Afnopadd v1

Status: **DRAFT, pending owner review** (2026-10-20). Source of truth for scope: `SPEC.md` and the module specs; module ids from `CAPABILITY-MAP.md`. Tasks are tracked in `tasks/todo.md` (checklist file, the default target).

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

### Phase 0: Foundation and walking skeleton
- [x] Task 1: Build plumbing and approved dependencies (2026-10-20)
- [x] Task 2: App shell skeleton (navigation, theme, container) (2026-10-20)
- [x] Task 3: Document model primitives and stroke point codec (2026-10-20)
- [x] Task 4: In-memory sheet with transactions and undo/redo (2026-10-20)
- [x] Task 5: Room schema and SheetStore create/open (2026-10-20)
- [x] Task 6: Persisted transactions, op log, autosave, crash safety (2026-10-20)

### Checkpoint A: Document foundation (reached 2026-10-20)
- [x] `.\gradlew.bat check` green; Room round trip of 5,000 strokes passes; crash test passes

- [ ] Task 7: Camera model and canvas math
- [ ] Task 8: SheetCanvas composable with pan, pinch, background
- [ ] Task 9: Ink wet strokes (InProgressStrokes) with frame transform
- [ ] Task 10: Dry stroke rendering with same-frame handoff
- [ ] Task 11: Pen tool, registries, JSON toolbar, editor wiring

### Checkpoint B: Walking skeleton
- [ ] Draw with the pen, pinch zoom, undo, kill the process, reopen: strokes are intact. Owner reviews on a device before Phase 1.

### Phase 1: Library and the full tool set
- [ ] Task 12: Library index and SheetLibrary
- [ ] Task 13: Library screen (new, continue, open older)
- [ ] Task 14: Off-screen renderer and thumbnails
- [ ] Task 15: Stroke tool family, presets, thickness
- [ ] Task 16: Colour palette, picker, recents
- [ ] Task 17: Spatial index, hit-testing, eraser
- [ ] Task 18: Shape tools and rendering
- [ ] Task 19: Hand tool, zoom commands, finger-mode policy, settings store

### Checkpoint C: Every v1 tool usable
- [ ] All tools reachable from the default toolbar with finger and stylus; state-machine tests pass; owner tries it on both reference devices

### Phase 2: Selection, images, deep-zoom performance
- [ ] Task 20: Select tool: selection, marquee, move, delete, duplicate, z-order
- [ ] Task 21: Select tool: scale and rotate handles
- [ ] Task 22: Image import pipeline
- [ ] Task 23: Image rendering, crop mode, arrange in row
- [ ] Task 24: Tile pyramid cache and level of detail
- [ ] Task 25: Deep-zoom hardening and performance fixture

### Checkpoint D: Performance criteria
- [ ] Success criteria 1 to 4 of `SPEC.md` measured on both devices and recorded in `docs/checklists/perf-2026.md`

### Phase 3: Sharing, settings, release readiness
- [ ] Task 26: Package format v1 export and import
- [ ] Task 27: Image export (PNG, JPEG) with Sharesheet and Save as
- [ ] Task 28: PDF export
- [ ] Task 29: Share and receive packages
- [ ] Task 30: Settings, About, lifecycle hardening
- [ ] Task 31: Quality gates, ADRs, release scaffolding

### Checkpoint E: v1 complete
- [ ] All nine success criteria in `SPEC.md` verified; manual release checklist done on both devices; format v1 frozen

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
