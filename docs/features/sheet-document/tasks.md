# Tasks: sheet-document

Spec: [spec.md](spec.md). Roadmap, checkpoints and the definition of done: [docs/product/roadmap.md](../../product/roadmap.md). Task numbers are global across features; paths in a task are relative to `app/src/main/java/io/github/subhaneetshrestha/afnopadd/` unless they start with `app/`, `docs/` or `gradle/`.

| Task | Title | Status |
|---|---|---|
| [3](#task-3-document-model-primitives-and-stroke-point-codec) | Document model primitives and stroke point codec | Done 2026-10-20 |
| [4](#task-4-in-memory-sheet-with-transactions-and-undoredo) | In-memory sheet with transactions and undo/redo | Done 2026-10-20 |
| [5](#task-5-room-schema-and-sheetstore-createopen) | Room schema and SheetStore create/open | Done 2026-10-20 |
| [6](#task-6-persisted-transactions-op-log-autosave-crash-safety) | Persisted transactions, op log, autosave, crash safety | Done 2026-10-20 |
| [26](#task-26-package-format-v1-export-and-import) | Package format v1 export and import | Not started |

---

## Task 3: Document model primitives and stroke point codec

**Status:** Done 2026-10-20

**Description:** Implement the value types the whole app shares: `WorldPoint`, `WorldRect`, `ElementId` with a ULID generator, `FractionalIndex` with insert-between, the `Element` sealed hierarchy (`StrokeElement`, `ShapeElement`, `ImageElement`, reserved `NoteElement`, `UnknownElement`), `BrushSpec`, shape kinds and geometry, and the packed Float32 `StrokePoints` codec with a version byte, plus kotlinx.serialization serializers for element payloads with unknown-key tolerance.

**Acceptance criteria:**
- [x] ULIDs are 26 characters, Crockford base32, and monotonic within a millisecond; `FractionalIndex.between` never collides across 10,000 random inserts and sorts correctly.
- [x] `StrokePoints` encode/decode round-trips 1,000 random points bit-exactly, rejects unknown versions, and stores pressure, tilt, orientation, and time.
- [x] Element payload JSON with an extra unknown field decodes and re-encodes without losing the field (`UnknownElement` for unknown kinds).

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.document.*"` (29 tests)
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: none

**Dependencies:** Task 1

**Files likely touched:**
- `document/Ids.kt`, `document/FractionalIndex.kt`, `document/Geometry.kt`, `document/Element.kt`, `document/StrokePoints.kt`, `document/internal/json/ElementSerializers.kt`
- tests mirrored under `app/src/test/java/.../document/`

**Estimated scope:** Medium

**Notes:**

Fractional indexes use an integer-plus-fraction scheme, so 10,000 appends or prepends stay under 8 characters.

---

## Task 4: In-memory sheet with transactions and undo/redo

**Status:** Done 2026-10-20

**Description:** Implement `Sheet`, `Transaction`, `ElementSnapshot` (persistent map plus z-ordered list), diff generation (before/after per element), the undo/redo stack capped at 200 entries, and a `SheetWriter` hook that receives committed diffs (a no-op writer for now). Snapshots publish through `StateFlow` immediately on commit.

**Acceptance criteria:**
- [x] A transaction that adds, updates, and deletes elements yields exactly those diffs; an empty transaction is dropped; `undo` then `redo` restores the identical snapshot.
- [x] `canUndo`/`canRedo` flows are correct through 250 transactions (cap honoured, oldest dropped).
- [x] Diffs reach the `SheetWriter` in commit order with a shared transaction id and label.

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.document.*"` (14 transaction tests)
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: none

**Dependencies:** Task 3

**Files likely touched:**
- `document/Sheet.kt`, `document/Transaction.kt`, `document/internal/SheetImpl.kt`, `document/internal/UndoStack.kt`, `document/internal/SheetWriter.kt`
- tests under `app/src/test/java/.../document/`

**Estimated scope:** Medium

**Notes:**

Undo and redo are written as inverse transactions (`kind`, `inverseOf`) so the op log stays append-only; a throwing or invalid transaction applies nothing.

---

## Task 5: Room schema and SheetStore create/open

**Status:** Done 2026-10-20

**Description:** Define the per-sheet Room database (`meta`, `element`, `stroke_points`, `asset`, `op`) with the bundled SQLite driver in WAL mode, export its schema, and implement `SheetStore.create` and `open`, which build the initial snapshot from rows. The Room-on-JVM spike was answered in Task 1: Room's Android artifact needs a `Context`, so Room-backed tests live in `androidTest` and run on the emulator (ADR 0001); `docs/product/spec.md`'s testing table already allows this.

**Acceptance criteria:**
- [x] `create` makes `sheets/<ulid>/sheet.db` with `schema_version = 1`; `open` returns a `Sheet` whose snapshot matches rows written directly through the DAO.
- [x] Schema JSON for version 1 is committed under `app/schemas/` and a migration test harness exists (empty for now).
- [x] Opening a 5,000-stroke database yields the first snapshot in under 1.0 s on the reference phone (instrumented timing test). Measured 795 ms cold on the API 36 emulator after moving common fields into columns, hand-decoding JSON, direct-buffer point decoding and parallel decode (was 1,435 ms); the phone number is still to be taken at Checkpoint B.

**Verification:**
- [x] Tests pass: `connectedDebugAndroidTest` (`SheetStoreTest`, `SheetDatabaseSchemaTest`)
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: inspect the schema JSON for the expected tables (meta, element, stroke_points, asset, op)

**Dependencies:** Task 4

**Files likely touched:**
- `document/SheetStore.kt`, `document/internal/db/SheetDatabase.kt`, `document/internal/db/Entities.kt`, `document/internal/db/SheetDao.kt`, `document/internal/SheetStoreImpl.kt`
- `app/schemas/...`

**Estimated scope:** Medium

**Notes:**

Lesson: keep the emulator awake (`adb shell svc power stayon true`) before instrumented runs; a sleeping screen stalled an activity-recreation test for eleven minutes.

---

## Task 6: Persisted transactions, op log, autosave, crash safety

**Status:** Done 2026-10-20

**Description:** Implement the real `SheetWriter`: a single-threaded dispatcher per sheet that turns each committed diff set into one SQLite transaction (element upserts, stroke blobs, op rows with `txn_id`, `undo_of` for inverse ops), `flush()` and `close()` with compaction of ops beyond the last 1,000 and tombstone purge, and the content-addressed `AssetStore`.

**Acceptance criteria:**
- [x] 5,000 stroke transactions written then reopened reproduce the snapshot exactly; the op table holds one row per diff.
- [x] Simulated failure in the middle of a transaction (a hook inside the database transaction that throws) leaves the database consistent with all earlier transactions present; later transactions still persist and `flush()` reports the failure once.
- [x] A 1,000-point stroke transaction reaches disk in under 30 ms median on the reference phone (instrumented timing test): 2.7 ms median, 9.3 ms max on the API 36 emulator with WAL and `synchronous=NORMAL`; `AssetStore.put` deduplicates by SHA-256 and `collectGarbage` removes unreferenced files.

**Verification:**
- [x] Tests pass: `.\gradlew.bat testDebugUnitTest --tests "*.document.*"` (FileAssetStore) plus `connectedDebugAndroidTest` (`SheetPersistenceTest`)
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [x] Manual check: none

**Dependencies:** Task 5

**Files likely touched:**
- `document/internal/RoomSheetWriter.kt`, `document/AssetStore.kt`, `document/internal/FileAssetStore.kt`, `document/internal/db/SheetDao.kt`
- tests under `document/`

**Estimated scope:** Medium

**Notes:**

Close compacts the op log to the newest 1,000 rows, purges tombstones (stroke points follow through the foreign key) and garbage-collects assets no image references.

---

## Task 26: Package format v1 export and import

**Status:** Not started

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
