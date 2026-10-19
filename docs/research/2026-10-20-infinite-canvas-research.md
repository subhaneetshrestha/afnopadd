# Research: infinite-canvas drawing app on Android

Date: 2026-10-20. Method: five parallel research tracks (stroke engine and zoom, open-source landscape, persistence and images, sharing, tool configuration and plugins). Library versions were verified against Maven metadata on the date above; anything that could not be verified from a primary source is marked **UNVERIFIED**.

Product decisions this research was scoped to (from the owner):

- Concepts-style **vector** canvas: strokes stay crisp at any zoom. Not a raster painter.
- Open-source app distributed F-Droid style: FOSS-only dependencies, no Google Play Services, no trackers.
- Sharing v1 = send the sheet file; the recipient edits their own copy. No sync, no real-time collaboration.
- JSON tool layouts and custom tools/plugins are later phases, but the data model must leave room for them.

## 1. Summary of recommendations

| Requirement | Recommendation | Why |
|---|---|---|
| Pen / pencil / brush strokes, pressure, low latency | **Jetpack Ink 1.0.0** (`androidx.ink`, Apache-2.0) for wet and dry stroke rendering, with our own document model around it | Native, Compose-ready, mesh-based strokes, front-buffer low latency, delta-compressed stroke storage. Strokes only: shapes, images, selection, undo, camera are ours |
| "Infinite" zoom without quality loss | Double-precision camera and floating-origin element anchors, nested zoom frames to respect Ink's fixed `epsilon`, R-tree culling, raster tile pyramid for zoomed-out views | Vector sources re-render crisply at any scale; float32 jitter and Ink's quantisation are the real limits, both handled by frames |
| Sheet persistence | **SQLite per sheet (Room 2.8.5 + bundled SQLite)** as the working format; a ZIP package only for export/share | Row-sized atomic autosave for stroke-heavy documents; whole-document JSON rewrites are the autosave bottleneck in every surveyed app |
| Undo / redo | tldraw-style diff + marks, stored as an append-only `op` log in the same transaction as the element rows | One mechanism serves undo, crash recovery, and a future sync protocol |
| Image import, sticky board | Photo Picker + SAF, copy bytes into a content-addressed asset store, bake EXIF, cap level-0 at 4096 px, Coil 3 as the mip cache | Picker URI grants expire; self-contained sheets are required for file sharing |
| Share with others | Stage 1: PNG/PDF via Sharesheet. Stage 2: portable `.zip` sheet package with custom MIME + magic-byte sniffing, opened via `ACTION_VIEW`/`ACTION_SEND` filters | No server, no accounts, no Play-policy impact; same serializer as local persistence |
| JSON tool layouts, custom tools | Layer 1 layout JSON and Layer 2 preset JSON over a **tool registry with stable string IDs**; Layer 3 scripted plugins later via a sandboxed interpreter (QuickJS via quickjs-kt) | Config = presence, placement, parameters; code = behaviour. Figma's lesson: a separate interpreter beats same-VM sandboxing |

## 2. Stroke engine: Jetpack Ink

Verified facts (release notes, Maven metadata, AndroidX source):

- Stable **1.0.0** (2025-12-17); newest pre-release 1.1.0-alpha09 (2026-09-23). Artifacts: `ink-authoring`, `ink-authoring-compose`, `ink-brush`, `ink-geometry`, `ink-rendering`, `ink-storage`, `ink-strokes`, `ink-nativeloader` (C++ via JNI). 1.0.0 pins `graphics-core:1.0.4` and `input-motionprediction:1.0.0`.
- Rendering tiers for in-progress ("wet") strokes: API 33+ uses SurfaceControl + `CanvasBufferedRenderer` on its own thread; API 29 to 32 uses `CanvasFrontBufferedRenderer`; below 29 falls back to a plain View with higher latency. Finished ("dry") strokes use `Canvas.drawMesh` only on API 34+ with a hardware canvas, otherwise `drawPath`.
- Compose entry point: `InProgressStrokes(defaultBrush, nextBrush, pointerEventToWorldTransform, strokeToWorldTransform, ...)`; finished strokes are handed back and must be drawn the same frame via `CanvasStrokeRenderer.draw(stroke, canvas, strokeToScreenTransform)` with an exact transform, or they look blurry.
- Stock brushes in 1.x: `marker`, `pressurePen`, `highlighter`, `dashedLine`, `emojiHighlighter`. **No pencil.** A true pencil needs a custom `BrushFamily`, which is experimental (`@ExperimentalInkCustomBrushApi`, programmatic customisation public only since 1.1.0-alpha03). Inputs carry pressure, tilt, and orientation; stock brushes use pressure and speed.
- A `Stroke` = immutable input batch + `Brush` + tessellated `PartitionedMesh` (float32). Geometry is quantised by `epsilon` in world units, which must stay fixed for the life of a document. Google's guidance: 1 world unit = 1 dp, `epsilon = 0.25 / maxZoom`. The mesh is not re-tessellated on zoom, but `Stroke(brush, inputs)` rebuilds it from inputs at any time.
- Storage: `StrokeInputBatch.encode/decode` (protobuf, delta compression); brush size, colour, epsilon, and family are persisted by the app; meshes are regenerated on load.
- Geometry module: whole-stroke erase via intersection tests, lasso selection. Partial eraser is experimental in 1.1 alphas only.
- Not covered by Ink: shapes, text, images, layers, selection UI, undo, camera, tiles, document model.

Implication for v1: "pencil" ships as a preset of `pressurePen` (lower opacity, thinner, speed-sensitive). A textured pencil waits for Ink 1.1 stable or a custom brush family.

## 3. Infinite zoom architecture

What "infinite" means in practice: Concepts' own help centre states it does not support infinite zoom, only an infinite canvas. Procreate is raster with hard pixel limits. Our target is a very large practical range (for example 0.001x to 100,000x) with no visible quality loss, not an unbounded number.

Recommended design (sources: Rnote's engine, VectorCraft's infinite canvas PR, sanenotes' tile design, Ink coordinate-system guide):

1. **Double-precision camera** `(x, y, zoom)`; each element stores a `Double` anchor and float32 geometry relative to that anchor (floating origin). Float32 jitter appears once local coordinate times zoom exceeds roughly 1.7e7 px, so chunk-local coordinates keep each chunk safe to roughly 4,000x.
2. **Nested zoom frames** so Ink's fixed `epsilon` and brush sizes stay in range: a stroke drawn at zoom `z` is assigned level `k = floor(log10 z)` and stored in a frame scaled by `10^-k`. Compose `camera ∘ frame` in double precision and convert to a float `Matrix` only at the end, so translations stay screen-pixel sized. Feed the inverse to `pointerEventToWorldTransform`.
3. **Spatial index** (R-tree or loose quadtree) over stroke bounding boxes; draw only visible elements; dirty-region invalidation.
4. **Raster tile pyramid** for zoomed-out views: 256 px tiles in power-of-two buckets, LRU budget around 96 MB, re-rasterise only on bucket change; strokes thinner than about 0.5 px are skipped or come from tiles. This is Rnote's "vector source of truth, rasterise per zoom" strategy and is what makes thousands of strokes affordable on devices that render dry strokes with `drawPath`.
5. **Images** keep their originals and use pre-downsampled levels when zoomed out; they pixelate at deep zoom. There is no vector fix for raster input, and the spec should say so.

Risks: strokes from coarse levels viewed at 1e4x relative zoom can still hit float32 jitter in Skia's matrix math (clip them or draw via the parent tile); wet-to-dry handoff flicker if a finished stroke misses its frame; Ink 1.1 alpha churn; `drawPath` cost below API 34 makes tiles mandatory, not optional.

## 4. Open-source landscape

| Project | Platform / language | Licence | Vector or raster | File format | Plugins | Collaboration | Status 2026 |
|---|---|---|---|---|---|---|---|
| Excalidraw | Web, TS/React | MIT | Vector | `.excalidraw` JSON: `elements[]`, `appState`, `files{}` | Shape libraries only | socket.io relay + Firestore, end-to-end encrypted | Active, 0.18.1 |
| tldraw | Web, TS/React | tldraw licence (source-available, production key required) | Vector | JSON record store, `.tldr` | First-class `ShapeUtil` / `StateNode` | `@tldraw/sync` on Durable Objects | Very active, 5.5 |
| Rnote | Desktop, Rust + GTK4 | GPL-3.0 | Vector, rasterised per zoom | gzip JSON with chained migrations | None | None | Active, 0.14.2 |
| Lorien | Desktop, Godot | MIT | Vector | Binary, undocumented | No | No | Slow, last release 2023 |
| Saber | Flutter, Android/iOS/desktop | GPL-3.0 | Vector, paged | `.sbn2` BSON | No | Nextcloud sync | Active, 1.36 |
| Xournal++ | Desktop, C++/GTK | GPL-2.0 | Vector, paged | `.xopp` gzip XML | Lua plugins | No | Active, 1.3.7 |
| Drawpile | Desktop + Android (F-Droid), C/C++ | GPL-3.0 | Raster, tiled | ORA, command log | No | Core feature, server relay | Active, 2.3 beta |
| OpenBoard | Desktop, C++/Qt | GPL-3.0 | Vector | `.ubz` zip of SVG pages | Undocumented | No | Active, 1.7.7 |
| Penpot | Web, Clojure + Rust/Skia WASM | MPL-2.0 | Vector | `.penpot` zip of JSON per shape | Yes, sandboxed | Real-time | Very active, 2.18 |
| Krita | Desktop + Android beta | GPL-3.0 | Raster, 64 px tiles, LOD preview | `.kra`, `.kpp` presets | Brush engines, Python | No | Active, 5.3 |
| AFFiNE / BlockSuite | Web + mobile, TS | MIT / MPL-2.0 | Vector "edgeless" | Yjs CRDT doc | Custom blocks | Yjs real-time | Active |
| Butterfly (Linwood) | Flutter, Android/desktop/web | AGPL-3.0 | Vector, infinite | `.bfly` (details UNVERIFIED) | Packs only | Experimental sessions | Active |
| Fossify Paint | Android, Kotlin/Views | GPL-3.0 | Raster | PNG/JPG/SVG | No | No | Maintained |
| JSON Canvas (Obsidian) | Spec only | MIT | n/a | `.canvas` nodes + edges | n/a | n/a | Spec 1.0 |
| Jetpack Ink + Cahier sample | Android, Kotlin/C++ | Apache-2.0 | Vector mesh | protobuf stroke batches | Custom brush families (experimental) | No | 1.0.0 stable |

Nothing can be embedded wholesale: tldraw is not open source; Rnote's engine hard-depends on glib, cairo, and librsvg and is GPL; Drawpile is raster; the Flutter apps are GPL/AGPL and would need add-to-app. Excalidraw in a WebView is the only viable embed, but it fights Compose, stylus pressure in WebView is OEM-dependent (UNVERIFIED), and the hand-drawn style is baked in. Jetpack Ink plus our own element store is the natural path.

Reusable ideas, with origin:

1. **Element record** with `id`, `version`, `versionNonce`, `isDeleted` tombstone, and fractional `index` for z-order (Excalidraw). Deterministic merge of two edited copies without a CRDT library, if ever needed.
2. **Tool = state chart, shape = util class with validated props and migrations plus free `meta`** (tldraw). Maps directly onto a tool registry keyed by stable IDs, which is also what JSON layouts and plugins target.
3. **Vector source of truth + per-zoom raster cache** with an R-tree over bounds and re-rasterising only visible strokes (Rnote). This is the infinite-zoom strategy.
4. **Assets as a separate table referenced by ID, binaries never inside element JSON** (Excalidraw `files`, tldraw `TLAsset`, Penpot `objects/`).
5. **JSON Canvas** node/edge/group schema for the sticky-board layer, for interoperability.
6. **Dual polygon cache while drawing** (Saber): cheap preview during the gesture, high quality on completion. Ink already does this with wet/dry strokes.
7. **Manifest + entry script + small `app.*` API** for plugins (Xournal++ Lua, Penpot). Start with the manifest-only layout JSON.

## 5. Document format and persistence

Every surveyed app rewrites the whole document per save (Excalidraw JSON, tldraw records, Rnote gzip JSON, Xournal++ gzip XML). With thousands of 100 to 300-point strokes that is the autosave bottleneck on Android (rough estimate: 5k strokes × 150 points ≈ 19 MB of JSON). SQLite writes only changed pages, commits atomically across crashes, and reads small blobs faster than separate files. A ZIP cannot be updated atomically in place.

Recommended layout: `filesDir/sheets/<ulid>/sheet.db` plus `assets/` and `thumb.webp`.

- Room 2.8.5 with `BundledSQLiteDriver` (androidx.sqlite 2.7.1) so API 24+ devices all get the same modern SQLite (`VACUUM INTO`, R-tree). Which features the system SQLite on API 24 lacks is UNVERIFIED; bundling sidesteps the question.
- Tables: `element(id ULID, kind, z fractional index, bbox, style_json, created, updated, deleted)`, `stroke_points(element_id, format, blob)` holding Ink's encoded input batch, `asset(sha256, mime, w, h, path)`, `op(seq, txn_id, type, element_id, before_json, after_json, ts)`, `meta(schema_version)`.
- ULIDs: 128-bit, UUID-compatible, lexicographically sortable by creation time.
- Build note for AGP 9.4 built-in Kotlin: Room's annotation processor runs through KSP 2.3.12 (`ksp("androidx.room:room-compiler:2.8.5")`), kapt is incompatible. kotlinx.serialization 1.12.0 is built on Kotlin 2.4.10 and matches our 2.4.21 toolchain.
- Autosave: commit each finished gesture as one Room transaction (element rows + op rows). Writes are row-sized so no debounce is needed; a crash loses only the in-flight gesture. Also flush in `onStop`.
- Library index: separate `library.db` with `Sheet(id, title, dir, createdAt, updatedAt, lastOpenedAt, thumbPath, elementCount)`. "Continue last" = `ORDER BY lastOpenedAt DESC LIMIT 1`.
- Thumbnails: render the model to about 512 px after commit, store as WebP in the sheet directory.
- Storage location: internal `filesDir` (no permissions, encrypted on Android 10+). Android Auto Backup has a 25 MB quota, so exclude `sheets/` from cloud backup but keep it in device-to-device transfer.
- Portable package for sharing: ZIP with `manifest.json` (format version, elements, styles), `strokes/<id>.bin`, `assets/<sha>.<ext>` with originals, never base64. The importer owns migrations so the DB schema and the file format evolve independently. Rnote's chained `TryFrom` migrations keyed by semver ranges are the model to copy.

## 6. Image import

1. Sources: `PickVisualMedia` / `PickMultipleVisualMedia` (activity 1.13.0, no permission, falls back to the documents UI), `OpenDocument("image/*")` for arbitrary files, `TakePicture` with a FileProvider URI. CameraX only if an in-app viewfinder is wanted.
2. Copy, don't persist URIs: picker grants last until reboot or process death; persisted grants break when files move and are capped (512 persisted grants per package in AOSP). Stream bytes to `assets/<sha256>.<ext>` and deduplicate by hash.
3. Decode bounds first, then power-of-two downsampling; cap the stored level-0 at 4096 px (Coil 3 also caps at 4096). JPEG, PNG, GIF, BMP, WebP decode everywhere; HEIF needs Android 9+ and device support; AVIF needs Android 12+. `BitmapFactory` ignores EXIF orientation, so read it with exifinterface 1.4.2 and bake the rotation into the copy.
4. Render through Coil 3.6.3 (`coil-compose`, no network artifact) with a request size per power-of-two zoom bucket; its size-keyed memory cache is the mip pyramid. Keep hardware bitmaps on (half the RAM, exempt from the hardware canvas "too large" limit).

## 7. Undo and redo

tldraw's model: every mutation runs in a transaction that yields per-element before/after diffs; diffs accumulate until a mark (gesture end) and flush as one undo entry; undo applies inverses, redo re-applies. The same diff mutates the in-memory model, updates rows, and appends `op` rows with a `txn_id`, all in one SQLite transaction. Undo appends inverse ops rather than deleting, so the log stays append-only. Do not persist the undo stack across process death (neither tldraw nor Excalidraw do); compact old ops on close.

## 8. Sharing

| Tier | Experience | Server? | Effort | Key APIs |
|---|---|---|---|---|
| 1 Static export | Choose viewport, content bounds, or selection and a scale; PNG/PDF via the Sharesheet (Quick Share, Drive, mail appear automatically) | No | S | Off-screen software `Canvas(Bitmap)` at chosen scale; `PdfDocument` keeps paths vector and can tile huge bounds into pages; `FileProvider` + `ACTION_SEND` + `createChooser`. Hardware canvases throw above about 100 MB, so export on a software canvas and tile |
| 2 Portable file (**chosen v1 model**) | Send the sheet package; recipient taps it, the app imports it as a new sheet, both edit independently | No | M | SAF `CreateDocument`/`OpenDocument`; `ACTION_VIEW`/`ACTION_SEND` filters on a custom MIME such as `application/vnd.afnopadd.sheet+zip` plus `application/octet-stream` and `*/*` fallbacks with magic-byte sniffing, because messaging apps strip names and MIME types |
| 3 Cloud link | Copy link, viewer opens read-only or imports a copy, optionally end-to-end encrypted like Excalidraw (key in URL fragment) | Yes | M to L | Out of scope: needs a backend and conflicts with the no-network, F-Droid direction |
| 4 Real-time | Live co-editing | Yes | L to XL | Out of scope. For the record: tldraw rejects CRDTs for canvas data and uses a server-authoritative room; Excalidraw uses per-element last-writer-wins. Kotlin CRDT bindings exist (automerge-java 0.0.9, yrs via community uniffi forks, Loro without Kotlin) but all are immature |

Design-now items so later tiers never force a rewrite: stable element IDs, immutable strokes with packed points, per-element `version` and `versionNonce`, fractional z-index, the append-only op log, content-addressed assets, and keeping camera state out of the document.

## 9. Tool layouts, presets, and plugins

Prior art pattern: config defines presence, placement, parameters, and permissions; code defines behaviour. Everyone versions config with a top-level integer or semver plus stable string IDs. Only VS Code validates with JSON Schema.

| Project | Config format | Config can define | Plugin runtime |
|---|---|---|---|
| tldraw | TypeScript only | Toolbar items `{id, label, icon, kbd, onSelect}` | In-process classes with validators and migrations |
| Excalidraw | `.excalidrawlib` JSON v2 | Reusable element groups (stamps) | None |
| MyPaint | `.myb` JSON v3 | Every brush setting = base value + per-input curve (pressure, speed, tilt) | None |
| Krita | `.kpp` PNG with XML | Entire brush preset | Unsandboxed Python |
| Figma | `manifest.json` | Menu, parameters, network allow-list, permissions | QuickJS in WASM, UI in an iframe; adopted after sandbox escapes |
| Penpot | `manifest.json` v2 | Permissions (`content:read/write`, `library:*`, ...) | iframe + messaging |
| Xournal++ | `plugin.ini` + `main.lua` | Menu entries, shortcuts | Lua with a small `app.*` API |

Proposed layered model:

- **Layer 1, layout JSON**: `{schemaVersion, toolbars:[{id, placement, items:[{ref:"tool:pen"} | {ref:"preset:..."} | {separator} | {group}]}], defaults, shortcuts}`. Unknown refs are dropped with a warning, never fatal.
- **Layer 2, preset JSON** (MyPaint shape): `{id, base:"tool:brush", label, icon, params:{width:{value, curve:{pressure:[[0,.2],[1,1]]}}, opacity, color, dash, texture, stabiliser}, snapping}`. Config-only ceiling: parametric brushes, stamps and composite shapes, snapping and grid, macros as ordered lists of existing command IDs. Anything conditional or looping is a language and belongs in Layer 3.
- **Layer 3, scripted plugins**: `{manifest.json, main.js}` with `id`, semver, `apiVersion`, `minAppVersion`, `permissions[]`, `contributes:{tools, presets, commands}`; the script sees a capability object only (`canvas.beginStroke/addPoint/endStroke`, `shapes.add`, `selection`, `ui.showPanel`, scoped `storage`).

v1 hooks that cost almost nothing: stable string IDs (`tool:pen`, `cmd:undo`) and a `ToolRegistry`; a Compose toolbar rendered from a `ToolbarSpec` list rather than hard-coded composables; commands as `Action(id, argsSchema)`; `ToolPreset(id, baseToolId, params)` with a sealed `ParamValue`; every file carries `schemaVersion` and is decoded with `ignoreUnknownKeys = true`, with migrations as pure JSON-to-JSON functions run before typed decoding.

Scripting engine for Layer 3 (later): **QuickJS via quickjs-kt** (dokar3, Apache-2.0, 1.0.5, about 1.2 MiB per ABI, size UNVERIFIED). Nothing is reachable unless bound, evaluation timeouts and cancellation are supported. Alternatives: Javet (V8, much larger), LuaJ (pure Java, manual sandboxing), Luau (best sandbox, no Android binding), Kotlin scripting (not feasible on Android). Google Play's Device and Network Abuse policy permits interpreted code in a sandbox but forbids downloading dex, JAR, or `.so` files; separate plugin APKs via intents/AIDL are allowed. F-Droid distribution removes the Play constraint but the sandboxing argument stands. JSON Schema validation: OptimumCode json-schema-validator 0.5.5 (MIT, Kotlin Multiplatform; Android target UNVERIFIED).

## 10. Stylus input

- Raw axes via `MotionEvent.getAxisValue`: `AXIS_PRESSURE`, `AXIS_TILT`, `AXIS_ORIENTATION`, `AXIS_DISTANCE` (hover). Filter by `TOOL_TYPE_STYLUS` and `TOOL_TYPE_ERASER`. Hover events enable S Pen brush previews. Compose's `PointerInputChange` exposes pressure but not tilt (UNVERIFIED), so Ink's own input path or `pointerInteropFilter` is needed for tilt.
- Palm rejection: honour `ACTION_CANCEL` on all APIs and `FLAG_CANCELED` pointer-ups on API 33+; Ink's composable already cancels strokes whose events are consumed elsewhere.
- Prediction: `androidx.input:input-motionprediction:1.0.0` (system predictor on Android 14+, built-in fallback otherwise); Ink calls it "strongly recommended".

## 11. Versions verified on 2026-10-20

| Artifact | Stable | Notes |
|---|---|---|
| androidx.ink:* | 1.0.0 | 1.1.0-alpha09 pre-release |
| androidx.graphics:graphics-core | 1.0.4 | pinned by Ink |
| androidx.input:input-motionprediction | 1.0.0 | |
| androidx.room:room-runtime | 2.8.5 | room3 3.0.3 exists (KSP-only, coroutines) |
| androidx.sqlite:sqlite-bundled | 2.7.1 | |
| androidx.datastore:datastore-preferences | 1.2.1 | |
| androidx.navigation3:navigation3-runtime | 1.2.0 | |
| androidx.activity:activity-compose | 1.13.0 | already in project |
| androidx.exifinterface:exifinterface | 1.4.2 | |
| io.coil-kt.coil3:coil-compose | 3.6.3 | |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.12.0 | built on Kotlin 2.4.10 |
| com.google.devtools.ksp | 2.3.12 | independent of Kotlin minor since 2.3.0 |
| io.github.dokar3:quickjs-kt | 1.0.5 | later phase only |
| Compose BOM / AGP / Kotlin / Gradle | 2026.09.00 / 9.4.1 / 2.4.21 / 9.8.2 | already in project |

## 12. Risks to carry into the spec

1. Ink has no pencil brush in 1.0; the custom brush API is experimental.
2. Float32 limits mean "infinite" zoom is a very large bounded range implemented with frames; this must be stated, not implied.
3. Devices below API 34 render dry strokes with `drawPath`; the tile cache is on the critical path for performance, not a polish item.
4. Devices below API 29 get no low-latency wet strokes.
5. Imported raster images pixelate at deep zoom by nature.
6. The sheet file format is a public contract once sharing ships; migrations must exist from version 1.
7. Several candidate libraries are young (room3, OptimumCode validator, quickjs-kt); prefer the stable lines.

## 13. Key sources

- Jetpack Ink: [release notes](https://developer.android.com/jetpack/androidx/releases/ink), [coordinate system and epsilon](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/ink-api-coordinate-system), [modules](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/ink-api-modules), [draw a stroke](https://developer.android.com/develop/ui/compose/touch-input/stylus-input/ink-api-draw-stroke), [persistent storage](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/ink-api-persistent-storage), [StockBrushes.kt](https://github.com/androidx/androidx/blob/androidx-main/ink/ink-brush/src/commonMain/kotlin/androidx/ink/brush/StockBrushes.kt), [InProgressShapesView.kt](https://github.com/androidx/androidx/blob/androidx-main/ink/ink-authoring/src/androidMain/kotlin/androidx/ink/authoring/InProgressShapesView.kt), [Cahier sample](https://github.com/android/cahier)
- Zoom architecture: [Rnote store](https://github.com/flxzt/rnote/blob/main/crates/rnote-engine/src/store/mod.rs), [VectorCraft infinite canvas PR](https://github.com/storytold/vectorcraft/pull/1838), [sanenotes tile design](https://github.com/swiftsaneai/sanenotes/issues/624), [Concepts on infinite zoom](https://tophatch.helpshift.com/hc/en/3-concepts/faq/260-does-concepts-support-infinite-zoom/), [Procreate canvas limits](https://help.procreate.com/articles/dabqrn-maximum-canvas-size), [Romain Guy on Compose Canvas](https://medium.com/@romainguy/your-first-conclusion-avoid-drawing-on-jetpack-compose-canvas-directly-for-drawing-intensive-work-a7b47fede2b)
- Open-source projects: [Excalidraw](https://github.com/excalidraw/excalidraw), [Excalidraw JSON schema](https://docs.excalidraw.com/docs/codebase/json-schema), [Excalidraw reconcile.ts](https://github.com/excalidraw/excalidraw/blob/master/packages/excalidraw/data/reconcile.ts), [tldraw](https://github.com/tldraw/tldraw), [tldraw licence](https://github.com/tldraw/tldraw/blob/main/LICENSE.md), [tldraw history](https://tldraw.dev/sdk-features/history), [Rnote](https://github.com/flxzt/rnote), [Rnote file format](https://github.com/flxzt/rnote/blob/main/crates/rnote-engine/src/fileformats/rnoteformat/mod.rs), [Saber](https://github.com/saber-notes/saber), [Xournal++ plugins](https://xournalpp.github.io/guide/plugins/plugins/), [Drawpile architecture](https://docs.drawpile.net/help/development/architecture), [Penpot file format](https://help.penpot.app/technical-guide/developer/data-model/penpot-file-format/), [JSON Canvas spec](https://jsoncanvas.org/spec/1.0/), [Butterfly](https://github.com/LinwoodDev/Butterfly), [Fossify Paint](https://github.com/FossifyOrg/Paint), [Lorien](https://github.com/mbrlabs/Lorien)
- Persistence and images: [Room releases](https://developer.android.com/jetpack/androidx/releases/room), [SQLite as application file format](https://sqlite.org/appfileformat.html), [ULID spec](https://github.com/ulid/spec), [Photo Picker](https://developer.android.com/training/data-storage/shared/photopicker), [SAF](https://developer.android.com/training/data-storage/shared/documents-files), [Load large bitmaps](https://developer.android.com/topic/performance/graphics/load-bitmap), [Supported media formats](https://developer.android.com/media/platform/supported-formats), [Coil 3](https://coil-kt.github.io/coil/compose/), [Auto Backup](https://developer.android.com/identity/data/autobackup), [Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin), [KSP 2.3.12](https://github.com/google/ksp/releases/tag/2.3.12), [kotlinx.serialization 1.12.0](https://github.com/Kotlin/kotlinx.serialization/releases/tag/v1.12.0)
- Sharing: [Android sharing](https://developer.android.com/training/sharing/send), [Intent filters](https://developer.android.com/training/basics/intents/filters), [RecordingCanvas size limit](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/RecordingCanvas.java), [Excalidraw end-to-end encryption](https://plus.excalidraw.com/blog/end-to-end-encryption), [tldraw sync](https://tldraw.dev/docs/sync), [automerge-java](https://github.com/automerge/automerge-java/releases), [yrs](https://crates.io/crates/yrs), [loro-ffi](https://github.com/loro-dev/loro-ffi)
- Tool config and plugins: [Google Play Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/9888379), [tldraw tools](https://tldraw.dev/sdk-features/tools), [libmypaint brushsettings.json](https://raw.githubusercontent.com/mypaint/libmypaint/master/brushsettings.json), [Figma plugin security update](https://www.figma.com/blog/an-update-on-plugin-security/), [Figma manifest](https://developers.figma.com/docs/plugins/manifest), [Penpot plugins](https://help.penpot.app/plugins/getting-started/), [VS Code contribution points](https://code.visualstudio.com/api/references/contribution-points), [quickjs-kt](https://github.com/dokar3/quickjs-kt), [Zipline](https://github.com/cashapp/zipline), [LuaJ](https://github.com/luaj/luaj), [OptimumCode json-schema-validator](https://github.com/OptimumCode/json-schema-validator)
- Stylus: [Advanced stylus features](https://developer.android.com/develop/ui/views/touch-and-input/stylus-input/advanced-stylus-features), [Reject palm touches](https://developer.android.com/develop/ui/compose/quick-guides/content/reject-stylus-palm-touches), [input releases](https://developer.android.com/jetpack/androidx/releases/input)
