# Capability Map: Afnopadd (infinite-canvas sheet app)

Status: **APPROVED by the owner on 2026-10-20.** Module ids are stable; specs, plans, and tasks select work by these ids. Research behind the choices: `docs/research/2026-10-20-infinite-canvas-research.md`.

Decisions taken with the approval: licence GPL-3.0-or-later; minSdk 29; app name Afnopadd; application ID derived from the GitHub repository: `io.github.subhaneetshrestha.afnopadd`.

| Module id | Responsibility | Depends on |
|---|---|---|
| `sheet-document` | The sheet itself: element model (stroke, shape, image, note), stable IDs and z-order, styles, asset store, SQLite-per-sheet persistence with autosave, transaction + op log (undo/redo, crash recovery), schema versioning and migrations, portable package export/import (the shareable file format) | — |
| `canvas-engine` | Viewport and camera with deep zoom (double precision, floating origin, zoom frames), gesture handling (pan/zoom, stylus vs finger policy), Jetpack Ink wet/dry stroke rendering, shape and image rendering, spatial index and culling, raster tile cache for zoomed-out views, hit-testing | `sheet-document` |
| `drawing-tools` | Tool registry with stable IDs, tool state machines (pen, pencil, brush/marker, highlighter, eraser, line, rectangle, ellipse, arrow, select/move, hand for pan and pinch-zoom), colour palette and picker, brush type and thickness presets, toolbar rendered from a `ToolbarSpec`, undo/redo and zoom controls | `canvas-engine`, `sheet-document` |
| `image-board` | Image import (photo picker, documents, camera), asset copy with hashing, EXIF and downsampling, placing, moving, scaling, rotating, cropping images, sticky-board arrangement helpers | `canvas-engine`, `sheet-document`, `drawing-tools` |
| `sheet-library` | Launcher menu: new sheet, continue last sheet, open older sheet; library index, thumbnails, rename/duplicate/delete, last-opened tracking | `sheet-document` |
| `sharing` | Export PNG/PDF (region and scale) through the Android Sharesheet; share the portable sheet package; receive packages via `ACTION_VIEW`/`ACTION_SEND` and import them into the library | `sheet-document`, `canvas-engine`, `sheet-library` |
| `app-shell` | Navigation (library ↔ editor), theming, dependency wiring, settings, app-wide lifecycle (autosave flush on stop) | `sheet-library`, `drawing-tools`, `image-board`, `sharing` |
| `tool-config` (later phase) | Import/validate layout JSON (which tools, grouping, order, defaults) and preset JSON (parametric brushes, stamps, macros); schema versioning | `drawing-tools` |
| `plugins` (later phase) | Installable plugin packages with manifest and sandboxed script runtime exposing a capability-scoped drawing API | `tool-config`, `drawing-tools` |

Build order: `sheet-document` → `canvas-engine` → `drawing-tools` → `sheet-library`, `image-board` → `sharing` → `app-shell` → `tool-config` → `plugins`

Notes:

- A navigation skeleton for `app-shell` (two empty screens) is created in the very first implementation task so every module can be exercised in the running app; `app-shell` is listed last only because its contract depends on everyone else's.
- Capability modules map to packages inside the single Gradle module `:app` under `io.github.subhaneetshrestha.afnopadd` (`document`, `canvas`, `tools`, `images`, `library`, `sharing`, `shell`, `toolconfig`, `plugins`); they split into Gradle modules only if build times or boundaries demand it.
- Contracts live in the provider module's spec: `sheet-document` owns the element types, transaction API, and file format; `canvas-engine` owns the camera and render APIs; `drawing-tools` owns the tool registry and `ToolbarSpec` that `tool-config` and `plugins` will target.
- `tool-config` and `plugins` are later phases, but `drawing-tools` and `sheet-document` must ship the hooks they need: stable string IDs, registry-driven toolbar, `schemaVersion` on every file, unknown-field tolerance.
- Out of scope for every v1 module: cloud links, real-time collaboration, accounts, analytics, any network access.
