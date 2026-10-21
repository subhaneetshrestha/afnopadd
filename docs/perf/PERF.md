# Performance ledger

Every performance experiment, kept or reverted, with its numbers. Read this before trying an
optimisation so a dead idea is not re-run. Measurements come from `RendererPerfTest` and
`ElementIndexPerfTest` (instrumented) unless noted.

**Environment:** API 36 emulator `afnopadd36` (pixel_7, 2 GB), debug build, Windows 11 host.
Debug builds run partly interpreted, so absolute numbers are pessimistic; compare runs, not devices.
The authoritative device numbers are taken at Checkpoint D (`docs/checklists/perf-2026.md`).

**Fixture:** 5,000 strokes of 60 points, pressure pen size 4, scattered over a 20,000-unit square.
"Overview" fits the whole sheet in a 1080x2400 viewport (all 5,000 strokes visible); "pan" moves
a 1080x2400 window at 1x across the sheet for 240 frames. Frame time is the UI-thread cost of
recording the dry layer into a `RenderNode`; GPU time on the render thread is not included.

## 2026-10-20: renderer and memory review

### Baseline

| Metric | Value |
|---|---|
| Overview frame, median (cold first frame) | 4,878 ms (6,636 ms) |
| Overview allocation | 23.8 MB/frame, 327 GCs per 60 frames |
| Pan frame, median / p95 | 1.07 ms / 3.62 ms, 22 KB/frame |
| Native memory per cached stroke, drawn | about 27 KB (cache estimated 10 KB) |
| Native memory per stroke, built only | 17.8 KB |
| Native memory kept by `SoftwareRenderer` after one thumbnail | 34 MB |
| Listener leak on tool switch (`CanvasLeakTest`) | the replaced input sink stayed reachable |

Diagnosis: at overview zoom the 48 MB `StrokeCache` cannot hold 5,000 meshes, and drawing them in
the same order every frame makes an LRU evict each mesh just before it is needed again, so every
frame re-tessellated almost every stroke. The budget also undercounted real memory by 2.7x.

### Experiments

| Idea | Baseline → result | Verdict | Why |
|---|---|---|---|
| Draw strokes smaller than 32 px on screen as cached 16-point polylines instead of Ink meshes (`DryLayer` sketches) | overview 4,878 → 178 ms; 23.8 MB → 1.2 MB/frame; 327 → 11 GCs | kept | Ends the cache thrash; `SketchFidelityTest` shows the same ink coverage (75.5 vs 76.8) and centroid. Interim until the tile pyramid (Task 24). |
| Reuse one `Matrix` in the dry layer instead of one per stroke per frame | overview 178 → 120 ms; 1.2 → 0.6 MB/frame | kept | Halves allocation; rendering tests unchanged (pixel diff 0.010 %). |
| Clear `SoftwareRenderer`'s stroke cache after each render call | 34 MB → 4 MB retained after a thumbnail | kept | The renderer is process-wide; the cache now lives for one call, shared by all tiles of that call. |
| Unregister the replaced `WetStrokeRouter` from the Ink view (`DisposableEffect`) | sink reachable → collected | kept | `CanvasLeakTest` failed before, passes after. |
| Estimate mesh memory from Ink's mesh vertex and triangle counts | — | rejected | Those APIs are `@RestrictTo` the Ink library; Lint (`RestrictedApi`) fails the build. |
| Estimate mesh memory from public outline vertex counts (90 B per vertex, 280 vertices per fixture stroke) | estimate 10 KB → 27.6 KB per stroke vs about 27 KB measured | kept | Budget now matches what drawn strokes cost. |
| Index snapshots by transaction (`ElementSnapshot.changedSince`) instead of diffing all elements (Task 17) | sync after one stroke 3.9 → 0.07 ms | kept | Only the changed ids touch the R-tree. |
| Sort-tile-recursive bulk load for the first index sync (Task 17) | build 1,124 → 56 ms warm (158 ms cold) | kept | One sort per level instead of a descent and split per entry. |

### After

| Metric | Value |
|---|---|
| Overview frame, median / p95 | 120–153 ms / 177–193 ms, 0.6 MB/frame, 7–8 GCs per 60 frames |
| Pan frame, median / p95 | 1.2–3.1 ms / 2.2–4.4 ms, 21–38 KB/frame |
| Culling (index query), median | 0.26–0.42 ms |
| `SoftwareRenderer` retained after a thumbnail | 4 MB |

## 2026-10-20: editor-session leak hunt

Method: drive the installed debug build from outside with adb (open a sheet, draw, switch pencil,
eraser, pen, press back), 30 sessions, sampling `dumpsys meminfo`, the process's open file
descriptors, its threads, and the emulator's host memory every 10 sessions. (The same loop through
`createAndroidComposeRule` hit a test-harness error, "performMeasureAndLayout called during
measure layout", so the app is driven from outside instead.)

| Metric over sessions 10 → 30 | Before | After |
|---|---|---|
| Ink `CanvasInProgressStrokesRenderHelperV33` threads | +1 per session (30 after 30) | 1 in total |
| Open file descriptors, reopening one sheet | +2 per session (eventfd + epoll of each leaked looper) | flat (176 → 175) |
| Open file descriptors, a new sheet each time | +4.5 per session | +2 per session, from library thumbnails |
| Total PSS, reopening one sheet | 128 → 124 MB | 136 → 127 MB |
| Sheet databases left open | 0 | 0 |

| Idea | Baseline → result | Verdict | Why |
|---|---|---|---|
| Share one `InProgressStrokesView` per process (application context) | threads 11 → 1 in `InkThreadLeakTest` | rejected | A view attached to one activity's window stops finishing strokes in the next; every later `EditorFlowTest` test timed out waiting for its stroke. |
| Share one `InProgressStrokesView` per activity (`SharedInkView`), dropped on `ON_DESTROY` | threads +1 per session → 1 in total; reused view still draws | kept | Works around Ink 1.0.0 never quitting its render-helper `HandlerThread`; an activity recreation still costs one thread. Since 2026-10-21 the view is `InkHost`'s, attached over the activity's content and never detached (ADR 0003). |
| Close the sheet from the coroutine that opened it (`EditorScreen`) | no leak measured either way | kept | Removes a window where leaving the editor between the open finishing and the screen showing the sheet left its database open; correct by construction, not reproduced by a test. |

The emulator's own host memory (`qemu-system-x86_64`) grew to 7.3 GB over a day of test runs; it
stayed flat within these loops, so that growth is the emulator's, not the app's. Restart it cold
(`-no-snapshot-load`) when it passes about 6 GB.

## 2026-10-20: scan-resistant stroke cache (review finding)

| Idea | Baseline → result | Verdict | Why |
|---|---|---|---|
| `StrokeCache.beginFrame()`: meshes drawn in the current frame are never evicted for that frame; overflow is drawn uncached | 5 strokes drawn per frame against a 3-stroke budget: 20 builds over 4 frames → 11; overview and pan unchanged (119 ms / 1.3 ms) | kept | At working zoom, dense handwriting can exceed the budget with strokes above the 32 px sketch threshold, which was the same LRU thrash as the overview. A mutant without frames is caught by `theStrokeCacheKeepsWhatFitsWhenEveryFrameDrawsMoreThanItsBudget`. |

### Open gaps (not fixed here)

- **Overview of a full sheet is still 120+ ms per frame.** 5,000 path draws per frame is too many for
  60 fps; the tile pyramid (Task 24, spec decision 5) is the fix and must cover this fixture.
- **Per-commit cost grows with the sheet:** each transaction copies the element map (median 2.5 ms
  at 5,000 strokes) and the next draw re-sorts z-order (0.6 ms). Persistent collections would fix
  the copy but are a third-party dependency (ask the owner first); tracked for Task 25.
- No editor-level leak check yet (sheet released after leaving the editor); LeakCanary would do it
  but is a new dependency.

### Guards

`RendererPerfTest` fails if the overview allocates over 2 MB per frame or its median exceeds 400 ms
(800 ms on an emulator), if panning exceeds 4 ms median (8 ms emulator) or 100 KB per frame, if
`SoftwareRenderer` keeps over 16 MB after a thumbnail, or if the cache estimate falls below the
measured native cost of built strokes. `CanvasLeakTest` guards the router leak.
