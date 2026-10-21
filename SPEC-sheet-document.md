# Spec: sheet-document

Status: **APPROVED by the owner on 2026-10-20.** Module id `sheet-document` in `CAPABILITY-MAP.md`. Depends on: nothing. Package: `io.github.subhaneetshrestha.afnopadd.document`. Project-wide rules in `SPEC.md` apply.

## Objective

The single source of truth for a sheet's content and its persistence. Everything a sheet contains (strokes, shapes, images, reserved notes) lives here as immutable values with stable IDs, is changed only through transactions, is written to disk atomically per gesture, and can be exported to and imported from a self-contained, versioned package file. Every other module reads from and writes to this one; none of them touch SQLite or files directly.

## Scope

**In:** element model; IDs and z-order; coordinate conventions (world units, anchors, frame levels); styles; transaction and diff model; undo/redo stack; Room persistence with autosave; content-addressed asset store; schema versioning and migrations; package format (`.afnopadd`) export and import; sheet metadata (title, timestamps, origin).

**Out:** rendering and spatial indexing (`canvas-engine`), any UI, the library index of all sheets (`sheet-library`), intents and share flows (`sharing`), thumbnails (rendered by `canvas-engine`, stored by `sheet-library`).

## Contract

Public API (Kotlin, conceptual signatures; names are binding, parameter details may move during planning):

```kotlin
data class WorldPoint(val x: Double, val y: Double)
data class WorldRect(val left: Double, val top: Double, val right: Double, val bottom: Double)
@JvmInline value class ElementId(val value: String)        // 26-char ULID
@JvmInline value class AssetId(val sha256Hex: String)
@JvmInline value class FractionalIndex(val key: String)     // base-62 string, orderable, insert-between

sealed interface Element {
    val id: ElementId; val anchor: WorldPoint; val frameLevel: Int; val z: FractionalIndex
    val bounds: WorldRect; val version: Int; val versionNonce: Int
    val createdAt: Long; val updatedAt: Long
}
data class StrokeElement(..., val brush: BrushSpec, val points: StrokePoints) : Element
data class ShapeElement(..., val kind: ShapeKind, val geometry: ShapeGeometry, val style: ShapeStyle, val rotationDeg: Float = 0f) : Element
data class ImageElement(..., val asset: AssetId, val size: LocalSize, val rotationDeg: Float, val crop: LocalRect?) : Element
// rotationDeg (added in Task 21, before the format freeze): clockwise turn about the centre of the
// shape's geometry box or the image's size box; the anchor is that box's corner before turning.
// Only rectangles and ellipses use it (a turned line or arrow stores turned endpoints), and a shape
// payload carries "rotationDeg" only when it is non-zero, so unrotated shapes are byte-for-byte as before.
data class NoteElement(...) : Element                        // reserved; v1 never creates one

data class BrushSpec(val familyId: String, val size: Float, val colorArgb: Long, val opacity: Float, val epsilon: Float)
class StrokePoints(/* packed Float32 columns: x, y, pressure, tiltRad, orientationRad, tMillis */)

interface Sheet {
    val meta: StateFlow<SheetMeta>
    val snapshot: StateFlow<ElementSnapshot>                 // immutable map id -> Element, plus z-ordered list
    val canUndo: StateFlow<Boolean>; val canRedo: StateFlow<Boolean>
    fun transact(label: String, block: Transaction.() -> Unit): TransactionResult
    fun undo(); fun redo()
    val assets: AssetStore
    suspend fun flush()                                      // waits for pending writes
    suspend fun close()
}
interface Transaction { fun add(element: Element); fun update(id: ElementId, change: (Element) -> Element); fun delete(id: ElementId); fun setTitle(title: String) }
interface AssetStore { suspend fun put(source: InputStream, mime: String): AssetId; fun file(id: AssetId): File; suspend fun collectGarbage(referenced: Set<AssetId>) }
interface SheetStore {
    suspend fun create(dir: File, title: String): Sheet
    suspend fun open(dir: File): Sheet
    suspend fun exportPackage(sheet: Sheet, out: OutputStream)
    suspend fun importPackage(input: InputStream, intoDir: File): ImportResult   // Ok(meta) | Error(reason)
}
```

## Design decisions

1. **Coordinates.** World unit = 1 dp at zoom 1.0; world coordinates are `Double`. Each element stores a `Double` anchor and `Float` geometry relative to that anchor in *frame units*, where one frame unit is `10^-frameLevel` world units. An element created at camera zoom `z` gets `frameLevel = floor(log10(z))`, so its local geometry, brush size, and `epsilon` stay in the range Jetpack Ink recommends no matter how deep the user is. Conversion: `world = anchor + local / 10^frameLevel`. Camera state is never stored in the document.
2. **IDs and ordering.** ULIDs for elements and sheets (sortable by creation). Z-order is a fractional index string so insert-between and reorder never renumber other rows. `version` increments on every change and `versionNonce` is random; together they give last-writer-wins tie-breaking if two forks are ever reconciled.
3. **Strokes are vendor-neutral.** Points are stored in our own packed little-endian Float32 columnar format with a one-byte version header, not in Ink's protobuf. `canvas-engine` builds Ink `StrokeInputBatch` objects from them at load time. This keeps the public file format readable without Ink and free of Ink's storage versioning. `BrushSpec.familyId` is a stable string (`ink:pressure-pen`, `ink:marker`, `ink:highlighter`, `ink:dashed-line`) mapped to Ink brushes by `canvas-engine`.
4. **Transactions and undo (tldraw model).** `transact` runs the block against the current snapshot, producing per-element before/after diffs; it publishes the new snapshot immediately, then enqueues one SQLite transaction that updates element rows and appends one `op` row per diff with a shared `txnId`. Undo applies the inverse diff as a new transaction flagged `undoOf = txnId`; the log is append-only. The undo stack lives in memory only (cap 200 entries) and is not restored after process death. Changes that did not originate from the user (future sync, import) are not undoable.
5. **SQLite per sheet, Room 2.8.5 with `BundledSQLiteDriver`.** Each sheet is a directory `files/sheets/<ulid>/` containing `sheet.db` (WAL mode), `assets/`, and later `thumb.webp`. Tables: `meta(key, value)` including `schema_version`, `sheet_id`, `title`, `origin_json`; `element(id, kind, z, anchor_x, anchor_y, frame_level, bounds_l, bounds_t, bounds_r, bounds_b, version, version_nonce, deleted, created_at, updated_at, payload_json)`; `stroke_points(element_id, format_version, blob)`; `asset(id, mime, width, height, byte_size, created_at)`; `op(seq, txn_id, kind, element_id, before_json, after_json, created_at, undo_of)`. Room schema JSON is exported to `app/schemas/` and committed. Writes run on one single-threaded dispatcher per sheet; `flush()` awaits the queue.
6. **Autosave is the transaction.** Each finished gesture is one transaction and one commit; there is no timer. Deleted elements keep a tombstone row for one session, then `close()` compacts ops older than the last 1,000 and purges tombstones.
7. **Assets are content-addressed.** `put` streams to a temp file while hashing, then renames to `assets/<sha256>`; duplicates cost nothing. `collectGarbage` deletes files no live element references and runs on `close()`.
8. **Package format v1 (`.afnopadd`)** is a ZIP whose first entry is an uncompressed file named `mimetype` containing `application/vnd.afnopadd.sheet+zip` (the EPUB/ODF trick, so receivers can sniff the file even when the sender stripped its name). Then `manifest.json` (`formatVersion`, app name and version, `sheet{id, title, createdAt, updatedAt}`, `elements[]` with payloads, `assets[]`), `strokes/<elementId>.bin`, `assets/<sha256>`, optional `thumbnail.webp`. Deleted elements are not exported. Import creates a **new** sheet ULID (a fork) but keeps element IDs, records `origin{sheetId, exportedAt, app}` in `meta`, verifies every asset hash, and refuses files whose `formatVersion` is newer than the app supports with a clear error. Format versions are migrated by pure `JsonObject -> JsonObject` functions before typed decoding; the format document lives in `docs/format/afnopadd-v1.md` and is frozen at the first sharing release.
9. **Forward tolerance.** All JSON decoding uses `ignoreUnknownKeys = true`; unknown element kinds are preserved as `UnknownElement(rawJson)` in memory and written back unchanged, so an older app can round-trip a newer file without destroying data.

## Testing

- JVM unit tests: fractional index properties (ordering, insert-between never collides for 10,000 random inserts); ULID monotonicity; transaction diff correctness; undo/redo round trips; frame-level math; `StrokePoints` encode/decode including NaN-free guarantees.
- Room tests with `BundledSQLiteDriver` on the JVM (fallback: `androidTest`): create, write 5,000 strokes, reopen, compare; migration tests for every schema version using the exported schema JSON; crash simulation by killing the writer mid-transaction and reopening.
- Golden files: `app/src/test/resources/packages/v1/*.afnopadd` built once and never regenerated silently; import must produce the expected snapshot, and export of that snapshot must re-import equal.
- Fuzz: truncated or corrupted packages produce `ImportResult.Error`, never an exception or a half-imported sheet directory.

## Success criteria

1. Committing a 1,000-point stroke transaction reaches disk in under 30 ms median on the reference phone, measured by an instrumented test.
2. Opening a sheet with 5,000 strokes (about 150 points each) produces the first snapshot in under 1.0 s, excluding Ink mesh generation.
3. Process kill during drawing: reopening shows every element except the in-flight gesture (automated instrumented test).
4. Export then import: equal element sets by ID, geometry, style, and z-order; byte-identical assets by hash. A sheet with 5,000 strokes and 20 photos exports in under 5 s.
5. Every schema version since 1 has an automated upgrade test; a package with a newer `formatVersion` is rejected with a specific error, not a crash.
6. No public API of this package references Android UI, Compose, or Ink types.
7. 80 percent line coverage on the package.

## Decisions (owner, 2026-10-20)

1. Points are stored in Afnopadd's own packed format, not Ink's protobuf (decision 3); ZIP deflate recovers most of the compression.
2. Ops older than 1,000 are compacted on close (decision 6).
3. `NoteElement` stays reserved with no UI in v1.
