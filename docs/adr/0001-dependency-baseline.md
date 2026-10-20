# ADR 0001: Dependency baseline for v1

Date: 2026-10-20. Status: accepted (owner approved the non-Google libraries on 2026-10-20, see `SPEC.md` decisions).

## Context

Afnopadd ships through F-Droid under GPL-3.0-or-later with no network access. Every dependency must be free software, buildable from Maven artifacts F-Droid accepts, and justified by a module spec. The owner's boundary requires approval for anything outside AndroidX and Google first-party libraries. Versions were verified against Maven metadata on 2026-10-20 (`docs/research/2026-10-20-infinite-canvas-research.md`, section 11, plus a re-check at Task 1).

## Decision

| Library | Version | Licence | Why |
|---|---|---|---|
| androidx.core:core-ktx | 1.19.1 | Apache-2.0 | Kotlin extensions used throughout |
| androidx.activity:activity-compose | 1.13.0 | Apache-2.0 | `ComponentActivity`, Photo Picker contracts, back handling |
| androidx.compose BOM | 2026.09.00 | Apache-2.0 | UI toolkit; versions of ui, material3, tooling, ui-test |
| androidx.lifecycle:lifecycle-runtime-compose, lifecycle-viewmodel-compose | 2.11.0 | Apache-2.0 | `collectAsStateWithLifecycle`, screen view models |
| androidx.navigation3:navigation3-runtime, navigation3-ui | 1.2.0 | Apache-2.0 | Saveable back stack between library and editor (`SPEC-app-shell.md`) |
| androidx.ink:ink-authoring, ink-authoring-compose, ink-brush, ink-brush-compose, ink-geometry, ink-geometry-compose, ink-rendering, ink-strokes | 1.0.0 | Apache-2.0 | Stroke engine (`SPEC-canvas-engine.md`); `ink-storage` is not used because points are stored in Afnopadd's own format |
| androidx.input:input-motionprediction | 1.0.0 | Apache-2.0 | Stylus motion prediction, recommended by Ink |
| androidx.room:room-runtime, room-compiler (KSP), room-gradle-plugin | 2.8.5 | Apache-2.0 | Per-sheet and library databases (`SPEC-sheet-document.md`, `SPEC-sheet-library.md`) |
| androidx.sqlite:sqlite-bundled | 2.7.1 | Apache-2.0 | One modern SQLite on every supported device |
| androidx.datastore:datastore-preferences | 1.2.1 | Apache-2.0 | Settings and small preferences |
| androidx.exifinterface:exifinterface | 1.4.2 | Apache-2.0 | Bake photo orientation at import |
| com.google.devtools.ksp (Gradle plugin) | 2.3.12 | Apache-2.0 | Annotation processing for Room; kapt is incompatible with AGP 9 built-in Kotlin |
| org.jetbrains.kotlinx:kotlinx-serialization-json + plugin | 1.12.0 / Kotlin 2.4.21 | Apache-2.0 | Every JSON surface: element payloads, package manifest, tool layouts (owner-approved) |
| io.coil-kt.coil3:coil-compose | 3.6.3 | Apache-2.0 | Size-keyed bitmap cache for images and thumbnails; no network artifact is included (owner-approved) |
| junit:junit | 4.13.2 | EPL-1.0 | JVM and instrumented test runner (owner-approved) |
| org.jetbrains.kotlinx:kotlinx-coroutines-core, kotlinx-coroutines-android | 1.11.0 | Apache-2.0 | Already shipped transitively by Compose and Lifecycle; pinned explicitly so the app and the instrumented-test APK resolve the same version (a mismatch caused `NoSuchMethodError` at Task 1) |
| org.jetbrains.kotlinx:kotlinx-coroutines-test | 1.11.0 | Apache-2.0 | Deterministic coroutine tests (owner-approved) |
| androidx.test:runner, androidx.test:core, androidx.test.ext:junit | 1.7.0 / 1.7.0 / 1.3.0 | Apache-2.0 | Instrumented test infrastructure |
| androidx.compose.ui:ui-test-junit4, ui-test-manifest | BOM | Apache-2.0 | Compose UI tests |

Build configuration decided alongside: Lint runs with `warningsAsErrors` and `abortOnError`; debug builds enable unit-test coverage; Room schemas export to `app/schemas/`; the instrumentation runner is `AndroidJUnitRunner`.

## Consequences

- No `INTERNET` permission is introduced by any of these; Coil's network artifacts are deliberately excluded.
- Room cannot be constructed in plain JVM unit tests inside an Android module without a `Context`, so Room-backed tests live in `app/src/androidTest` and need a device or emulator. Pure document logic stays JVM-testable behind the `SheetWriter` boundary (`SPEC-sheet-document.md`).
- Any new dependency outside this table requires a new ADR and, if outside AndroidX/Google, the owner's approval.
