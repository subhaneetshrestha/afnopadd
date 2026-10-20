# Spec: Afnopadd

Status: **APPROVED by the owner on 2026-10-20** (decisions recorded at the end of this document).
This is the project-wide spec. Each capability module in `CAPABILITY-MAP.md` has its own `SPEC-<module>.md` that inherits everything here and adds module scope, contracts, and success criteria. Research behind the decisions: `docs/research/2026-10-20-infinite-canvas-research.md`.

## Objective

**What:** Afnopadd is an open-source Android app for drawing and arranging on an unbounded vector canvas called a *sheet*. Pen, pencil, and brush strokes with pressure; lines and geometric shapes; imported images arranged like a sticky board; very deep zoom with no loss of quality; sheets saved locally, shareable as files.

**Why:** a Concepts-style infinite vector canvas that is free software, works fully offline, and can later be reshaped by its users through JSON tool layouts, custom tools, and plugins.

**Users:** the owner first, then F-Droid users: people who sketch, plan, and think on phones and stylus tablets, who want no accounts and no tracking.

**User stories (v1):**

1. When I open the app I can start a new sheet, continue the last sheet, or pick an older sheet.
2. I draw with pen, pencil, or brush, choosing colour, brush type, and thickness; strokes respond to pressure on capable devices.
3. I draw straight lines, rectangles, ellipses, and arrows.
4. I import photos and arrange them like sticky notes: move, scale, rotate, crop, reorder.
5. I zoom in as far as I like and keep drawing finer detail; nothing I drew blurs. I can pinch to zoom in any tool, and a hand tool lets me pan and pinch-zoom without drawing.
6. I undo and redo any change.
7. The app saves as I go; a crash loses at most the stroke I was making.
8. I export a sheet as PNG, JPEG, or PDF, send the sheet file to someone, and open a sheet file someone sent me; each of us then edits our own copy.

**Non-goals for v1:** cloud links, sync, real-time collaboration, accounts, any network access, raster painting brushes, pages, layers, text notes (reserved in the data model, no UI), handwriting recognition.

## Tech stack (versions verified 2026-10-20)

| Component | Choice | Version |
|---|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 | Kotlin 2.4.21, Compose BOM 2026.09.00 |
| Build | Android Gradle Plugin with built-in Kotlin, Gradle wrapper, version catalog | AGP 9.4.1, Gradle 9.8.2 |
| Android | minSdk 29, compileSdk 37 (platform 37.2), targetSdk 37 | |
| Stroke engine | Jetpack Ink (`androidx.ink:*`), Apache-2.0 | 1.0.0 |
| Input | `androidx.input:input-motionprediction` | 1.0.0 |
| Persistence | Room with bundled SQLite driver, KSP | Room 2.8.5, androidx.sqlite 2.7.1, KSP 2.3.12 |
| Serialization | kotlinx.serialization JSON | 1.12.0 |
| Images | Photo Picker (activity 1.13.0), exifinterface 1.4.2, Coil 3 | Coil 3.6.3 |
| Navigation | Navigation 3 | 1.2.0 |
| Settings | DataStore Preferences | 1.2.1 |
| Tests | JUnit 4, kotlinx-coroutines-test, AndroidX test, Compose UI test | see Testing |
| Licence | GPL-3.0-or-later (`LICENSE`) | |
| Application ID | `io.github.subhaneetshrestha.afnopadd` (permanent) | |
| Source | https://github.com/subhaneetshrestha/afnopadd | |

Dependencies outside AndroidX/Google approved by the owner on 2026-10-20: kotlinx.serialization (JetBrains), kotlinx-coroutines-test (JetBrains), JUnit 4, Coil 3. Later phases add quickjs-kt and possibly a JSON Schema validator; those get their own approval when scheduled.

## Commands

Run from the project root in PowerShell. The wrapper finds Java on PATH (mise-managed JDK 25) and provisions the JDK 17 toolchain itself.

```powershell
.\gradlew.bat assembleDebug                      # Build debug APK -> app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat testDebugUnitTest                  # JVM unit tests (fast; run before every commit)
.\gradlew.bat lintDebug                          # Android Lint; warnings are errors
.\gradlew.bat check                              # lint + unit tests (the pre-commit gate)
$env:ANDROID_SERIAL = 'emulator-5560'            # Pin Gradle to Afnopadd's own AVD (afnopadd36); other emulators belong to other projects
.\gradlew.bat connectedDebugAndroidTest          # Instrumented tests on the pinned device
.\gradlew.bat installDebug                       # Install on the connected device
adb shell am start -n io.github.subhaneetshrestha.afnopadd/.MainActivity
.\gradlew.bat assembleRelease                    # Unsigned release build (F-Droid builds and signs from source)
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath   # Audit what ships
```

## Project structure

```
app/src/main/java/io/github/subhaneetshrestha/afnopadd/
  document/     sheet-document: element model, transactions, Room persistence, package format
  canvas/       canvas-engine: camera, rendering, Ink integration, tiles, hit-testing
  tools/        drawing-tools: tool registry, tool state machines, toolbar, presets, colours
  images/       image-board: import pipeline, image transforms, sticky-board helpers
  library/      sheet-library: menu screen, library index, thumbnails
  sharing/      sharing: PNG/JPEG/PDF export, package send/receive
  shell/        app-shell: MainActivity, navigation, theme, dependency wiring, settings
  toolconfig/   tool-config (later phase)
  plugins/      plugins (later phase)
app/src/main/res/                 Resources; all user-visible text in values/strings.xml
app/src/main/assets/              Built-in default tool layout JSON, sample sheets
app/src/test/java/...             JVM unit tests, package mirrors main
app/src/test/resources/           Golden files: sheet packages per format version, layout JSON
app/src/androidTest/java/...      Instrumented tests (Room on device, Compose UI, performance)
app/schemas/                      Room schema history (committed, never edited by hand)
docs/research/                    Research notes
docs/adr/                         Architecture decision records (NNNN-title.md)
docs/format/                      The sheet package format, versioned (public contract once sharing ships)
docs/checklists/                  Manual release checklist
tasks/                            plan.md and todo.md produced by the planning phase
CAPABILITY-MAP.md, SPEC.md, SPEC-<module>.md
```

Each capability package exposes a small public API (interfaces and data types in the package root) and keeps implementation in an `internal` sub-package. Dependencies between packages follow the capability map's arrows only; reviews enforce it.

## Code style

Kotlin official style (`kotlin.code.style=official`), 4-space indent, 120-column lines, trailing commas on multi-line declarations and calls. Immutable data by default; UI state is hoisted; Compose composables take state and lambdas, never repositories.

```kotlin
package io.github.subhaneetshrestha.afnopadd.document

/** World-space point in sheet units (1 unit = 1 dp at zoom 1.0). Double because sheets are deep. */
data class WorldPoint(val x: Double, val y: Double)

@JvmInline
value class ElementId(val value: String) {
    init {
        require(value.length == 26) { "ElementId must be a 26-character ULID, got '$value'" }
    }
}

sealed interface Element {
    val id: ElementId
    val anchor: WorldPoint
    val frameLevel: Int
    val z: FractionalIndex
}

data class StrokeElement(
    override val id: ElementId,
    override val anchor: WorldPoint,
    override val frameLevel: Int,
    override val z: FractionalIndex,
    val brush: BrushSpec,
    val points: StrokePoints,
) : Element

fun Sheet.eraseStrokes(ids: Collection<ElementId>) = transact(label = "Erase ${ids.size} strokes") {
    ids.forEach { delete(it) }
}
```

Conventions:

- Names: `PascalCase` types, `camelCase` members, `SCREAMING_SNAKE_CASE` constants, stable string IDs in `kind:name` form (`tool:pen`, `cmd:undo`).
- No `!!`; no platform types leaking from public APIs; `Result`/sealed errors instead of exceptions for expected failures (import of a corrupt file is expected, a null context is a bug).
- Coroutines: `suspend` for I/O, `Flow`/`StateFlow` for observation, structured concurrency only; a single-threaded dispatcher owns each sheet's database.
- Compose: one `@Preview` per screen-level composable; `Modifier` is always the first optional parameter; no business logic in composables.
- Every public type or function that is not obvious has a KDoc sentence saying *why*, not *what*.
- Comments and commit messages in English; Conventional Commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `build:`).

## Testing strategy

| Level | Framework | Location | What it covers |
|---|---|---|---|
| Unit (JVM) | JUnit 4, kotlinx-coroutines-test | `app/src/test` | Element model, transactions, undo/redo, fractional indexing, asset store file logic, camera and frame math, package round trips, tool state machines fed with scripted pointer sequences, layout JSON parsing |
| Instrumented | AndroidX test runner, Compose UI test | `app/src/androidTest` | Everything Room-backed (persistence, migrations, sheet open/create), Ink stroke creation and rendering, image decode pipeline, intent-filter import, performance timings on a device; Room's Android artifact needs a `Context`, so it cannot run in JVM unit tests |
| Manual checklist | `docs/checklists/release.md` | | Stroke feel and latency, palm rejection, sharing through Gmail/Telegram/Files, F-Droid anti-feature scan |

Rules: a bug fix starts with a failing test that reproduces it. Pure logic (document, camera math, tools) targets at least 80 percent line coverage, measured with the JaCoCo support built into AGP (`enableUnitTestCoverage`); UI and rendering are not coverage-measured. Instrumented tests must pass on one API 29 device and one API 34+ device before a release because the two use different Ink rendering paths. Robolectric is not added without approval. Before an instrumented run on the emulator, keep its screen on (`adb shell svc power stayon true`); a sleeping screen stalls activity tests.

## Boundaries

**Always**

- Run `.\gradlew.bat check` before every commit; keep `main` green.
- Ship every database or file-format change with a migration and a test that upgrades a golden file from the previous version.
- Put user-visible text in `strings.xml`; keep all storage inside app-private directories unless the user explicitly exports.
- Record architecture decisions in `docs/adr/`; update the relevant spec before implementing a change of direction.
- Keep the capability map's dependency arrows; a package may import only from packages it depends on.
- Write code that works with both finger and stylus input.

**Ask first**

- Adding any dependency outside AndroidX and Google first-party libraries (the owner's rule), including test and build plugins.
- Adding any network call, the `INTERNET` permission, analytics, or crash reporting (the owner's rule).
- Changing the sheet file format once its version 1 is frozen at the first sharing release (the owner's rule).
- Raising minSdk above 29, changing the application ID, or changing the licence.
- Pushing to GitHub beyond what the owner asked for in the current task; creating releases or tags.

**Never**

- Commit secrets, keystores, or `local.properties`.
- Bundle proprietary or non-FOSS libraries, Google Play Services, or any tracker; F-Droid must build the app from source with no anti-features.
- Delete or skip a failing test to get a green build; weaken Lint thresholds.
- Use kapt (incompatible with AGP 9 built-in Kotlin) or apply `org.jetbrains.kotlin.android`.
- Rewrite published git history or bypass hooks.

## Success criteria (v1, whole app)

Measured on the reference devices (an API 29+ phone and a stylus tablet on API 34+) unless stated.

1. Cold start to the library screen in under 1 second; opening a sheet with 5,000 strokes (about 150 points each) in under 1.5 seconds to first interactive frame.
2. A wet stroke appears within the next display frame on API 29+ (front-buffer path); drawing a 1,000-point stroke on a 5,000-stroke sheet drops no frames.
3. Pan and zoom at 60 frames per second on a sheet with 5,000 strokes and 20 images at any zoom level.
4. Total zoom range of at least 1e8 (0.001x to 100,000x) with crisp stroke edges and stable relative positions at every level.
5. Killing the process mid-stroke loses only that stroke; reopening shows everything else.
6. Export then import of a package yields the same element set (IDs, geometry, styles) and byte-identical assets.
7. Every v1 tool works with finger and stylus; pressure changes pen width on pressure-capable hardware; pinch zoom works in every tool and the hand tool pans with one pointer.
8. The release build requests no `INTERNET` permission and passes an F-Droid anti-feature review (no NonFreeDep, no Tracking).
9. Pure-logic packages reach 80 percent line coverage; all tests pass on an API 29 and an API 34+ device.

## Release blockers (before the first F-Droid build)

- Application ID `io.github.subhaneetshrestha.afnopadd` (done 2026-10-20).
- `LICENSE` is GPL-3.0-or-later (done); add SPDX headers if the owner wants them.
- Real app icon and brand colour (placeholder green check until a design pass).
- Fastlane metadata in `metadata/en-US/` (short and full description, screenshots, changelog).
- Reproducible build check from a clean clone; no signing material in the repository.
- Sheet package format v1 frozen and documented in `docs/format/`.

## Decisions recorded at approval (owner, 2026-10-20)

1. Third-party dependencies kotlinx.serialization, kotlinx-coroutines-test, JUnit 4, and Coil 3 are approved.
2. The app is named **Afnopadd**. Launcher label "Afnopadd"; sheet files use the `.afnopadd` extension and MIME type `application/vnd.afnopadd.sheet+zip`; application ID `io.github.subhaneetshrestha.afnopadd`; repository `subhaneetshrestha/afnopadd`.
3. No layers in v1; z-order only. The element model leaves room for a `layerId` later.
4. Reference devices: an API 29+ phone and a stylus tablet on API 34+.
5. Finger behaviour: fingers draw until a stylus is first seen on the device, then fingers pan and zoom; a setting overrides either way. In addition, a **hand tool** (`tool:hand`) is always in the toolbar: one pointer pans, two pinch-zoom. Pinch zoom works in every tool.
6. Zoom range 0.001x to 100,000x.
7. Stroke points are stored in Afnopadd's own compact format, not Ink's protobuf.
8. Imported photos are normalised to at most 4,096 px on the long edge; the original file is not kept.
9. Module-level proposals in each `SPEC-<module>.md` are adopted as written and recorded in their Decisions sections.

## Open questions

None outstanding at approval. New questions go into the relevant module spec and are raised in the planning phase.
