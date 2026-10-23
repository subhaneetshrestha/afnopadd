# Tasks: sheet-library

Spec: [spec.md](spec.md). Roadmap, checkpoints and the definition of done: [docs/product/roadmap.md](../../product/roadmap.md). Task numbers are global across features; paths in a task are relative to `app/src/main/java/io/github/subhaneetshrestha/afnopadd/` unless they start with `app/`, `docs/` or `gradle/`.

| Task | Title | Status |
|---|---|---|
| [12](#task-12-library-index-and-sheetlibrary) | Library index and SheetLibrary | Done 2026-10-20 |
| [13](#task-13-library-screen-new-continue-open-older) | Library screen (new, continue, open older) | Done 2026-10-20 |

---

## Task 12: Library index and SheetLibrary

**Status:** Done 2026-10-20

**Description:** Implement `library.db` (Room, `sheet` table) and `SheetLibrary`: create, markOpened, rename, duplicate (copy directory, new ULID, rewrite meta), delete to `files/trash/` with 7-day purge, register (for imports), and startup reconciliation against `files/sheets/` directories.

**Acceptance criteria:**
- [x] `sheets` emits in `lastOpenedAt` then `updatedAt` order; `lastOpened` emits the right sheet after `markOpened`.
- [x] Reconciliation adds a directory missing from the index and removes a row whose directory is gone.
- [x] Duplicate of a 5,000-stroke sheet completes in under 2 s (55 to 212 ms) and opens identically; delete then purge frees the directory; restore undoes a delete.

**Verification:**
- [x] Tests pass: `SheetTitlesTest` (JVM) and `SheetLibraryTest` (six instrumented tests)
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: none

**Dependencies:** Task 6

**Files likely touched:**
- `library/SheetLibrary.kt`, `library/SheetSummary.kt`, `library/internal/LibraryDatabase.kt`, `library/internal/SheetLibraryImpl.kt`
- `app/schemas/...`

**Estimated scope:** Medium

**Notes:**

`SheetStore` gained `create(..., id)`, `inspect` and `duplicate` so the library never touches sheet internals. Environment note: the full instrumented suite failed once at commit time because another session's app held the foreground on the shared emulator. The owner chose a dedicated AVD (`afnopadd36`, `emulator-5560`); the full suite of 41 instrumented tests is green there (open 5,000 strokes 482 ms, stroke commit 3.9 ms median, duplicate 71 ms).

---

## Task 13: Library screen (new, continue, open older)

**Status:** Done 2026-10-20

**Description:** Replace the Library placeholder with the real menu: "New sheet", "Continue" (title and thumbnail, hidden when empty), and "Open older" expanding to a thumbnail grid (list on narrow widths) with long-press rename, duplicate, delete (confirm plus 10-second undo snackbar), and share (stub until Task 29). Wire navigation so the editor opens the chosen sheet and marks it opened.

**Acceptance criteria:**
- [x] Fresh install shows only "New sheet"; after creating one, "Continue" appears and opens it.
- [x] Rename validates non-empty and 80 characters; delete shows confirmation and the undo snackbar restores the sheet.
- [x] A library of 500 generated sheets renders in under 500 ms (272 ms on the emulator) and scrolls to the last item; frame-rate measurement moves to the Task 25 performance suite.

**Verification:**
- [x] Tests pass: `LibraryScreenTest` (seven tests), `EditorFlowTest` and `NavigationTest` rewritten for the real menu; full suite of 48 instrumented tests green on `afnopadd36`
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: full flow on a device (owner)

**Dependencies:** Task 12, Task 11

**Files likely touched:**
- `library/LibraryScreen.kt`, `library/internal/LibraryViewModel.kt`, `library/internal/SheetCard.kt`
- `shell/Navigation.kt`, `shell/EditorScreen.kt`

**Estimated scope:** Medium

**Notes:**

The editor now opens sheets by id from `files/sheets/`, marks them opened, and refreshes their library entry on close; the app reconciles the library at startup. Timing assertions in instrumented tests use `PerfBudget` (2x on emulators) because emulator timings swing with host load; raw numbers are still logged.
