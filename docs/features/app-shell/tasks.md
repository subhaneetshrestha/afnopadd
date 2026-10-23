# Tasks: app-shell

Spec: [spec.md](spec.md). Roadmap, checkpoints and the definition of done: [docs/product/roadmap.md](../../product/roadmap.md). Task numbers are global across features; paths in a task are relative to `app/src/main/java/io/github/subhaneetshrestha/afnopadd/` unless they start with `app/`, `docs/` or `gradle/`.

| Task | Title | Status |
|---|---|---|
| [1](#task-1-build-plumbing-and-approved-dependencies) | Build plumbing and approved dependencies | Done 2026-10-20 |
| [2](#task-2-app-shell-skeleton-navigation-theme-container) | App shell skeleton (navigation, theme, container) | Done 2026-10-20 |
| [30](#task-30-settings-about-lifecycle-hardening) | Settings, About, lifecycle hardening | In progress (part done early; see the acceptance criteria) |
| [31](#task-31-quality-gates-adrs-release-scaffolding) | Quality gates, ADRs, release scaffolding | Not started |

---

## Task 1: Build plumbing and approved dependencies

**Status:** Done 2026-10-20

**Description:** Add every approved dependency to the version catalog and wire the build so later tasks never touch Gradle: Jetpack Ink, motion prediction, Room with KSP and the bundled SQLite driver plus the Room Gradle plugin with a schema directory, kotlinx.serialization plugin and JSON, Coil 3, DataStore, Navigation 3, exifinterface, lifecycle-runtime-compose, and the test stack (JUnit 4, kotlinx-coroutines-test, AndroidX test, Compose UI test). Turn on Lint warnings-as-errors and unit-test coverage. Verify each unverified version against Maven metadata before pinning.

**Acceptance criteria:**
- [x] All dependencies resolve; a trivial `@Entity` compiles through KSP and its schema JSON appears in `app/schemas/`; a trivial `@Serializable` class round-trips in a unit test.
- [x] `lint { warningsAsErrors = true; abortOnError = true }` and `enableUnitTestCoverage = true` are set; no `org.jetbrains.kotlin.android` plugin anywhere.
- [x] `docs/adr/0001-dependency-baseline.md` lists each library, version, licence, and why.

**Verification:**
- [x] `.\gradlew.bat check` passes (includes the two trivial tests)
- [x] `.\gradlew.bat assembleDebug` passes
- [x] Manual: `.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath` shows no unexpected transitive network libraries (only okio, a file I/O library used by DataStore and Coil)

**Dependencies:** None

**Files likely touched:**
- `gradle/libs.versions.toml`
- `build.gradle.kts`, `app/build.gradle.kts`
- `app/src/test/java/.../BuildSmokeTest.kt`
- `docs/adr/0001-dependency-baseline.md`

**Estimated scope:** Medium

**Notes:**

Bonus: the instrumented Room test (`SheetDatabaseTest`) passes on the API 36 emulator, which also settles the Task 5 spike.

---

## Task 2: App shell skeleton (navigation, theme, container)

**Status:** Done 2026-10-20

**Description:** Create the application class, a manual `AppContainer` with stubbed services, Navigation 3 with a saveable back stack holding `Library` and `Editor(sheetId)` placeholders, the Material 3 theme (light/dark, no dynamic colour), and move `MainActivity` into the `shell` package. This is the frame every later slice plugs into.

**Acceptance criteria:**
- [x] Launching shows the Library placeholder; a placeholder button opens the Editor placeholder; system back returns; rotation keeps the current destination.
- [x] `AppContainer` is created once in `AfnopaddApp` and reachable from composables via a `CompositionLocal`.
- [x] Each screen-level composable has a `@Preview` that renders in light and dark.

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest` (destination serialization) and `NavigationTest` (launch, navigate-and-back, recreate) on the API 36 emulator
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: covered on the emulator by the instrumented recreate test; a hands-on rotate is part of the owner's Checkpoint B review

**Dependencies:** Task 1

**Files likely touched:**
- `shell/AfnopaddApp.kt`, `shell/AppContainer.kt`, `shell/MainActivity.kt`, `shell/Navigation.kt`, `shell/theme/Theme.kt`
- `app/src/main/AndroidManifest.xml`

**Estimated scope:** Medium

---

## Task 30: Settings, About, lifecycle hardening

**Status:** In progress (part done early; see the acceptance criteria)

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

**Status:** Not started

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
