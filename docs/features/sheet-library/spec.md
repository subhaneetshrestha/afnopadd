# Spec: sheet-library

Status: **APPROVED by the owner on 2026-10-20.** Module id `sheet-library`. Depends on: `sheet-document`. Package: `io.github.subhaneetshrestha.afnopadd.library`. Project-wide rules in `docs/product/spec.md` apply.

## Objective

The first screen: start a new sheet, continue the last one, or open an older one. Behind it, the index of all sheets on the device with titles, timestamps, and thumbnails, plus rename, duplicate, and delete.

## Scope

**In:** the menu screen; the library index database; thumbnail storage and refresh; last-opened tracking; rename, duplicate, delete with confirmation; sorting by recency (search by title is deferred past v1); empty state; the directory layout under `files/sheets/`.

**Out:** rendering thumbnails (requested from `canvas-engine`'s `Renderer`), sharing actions (buttons delegate to `sharing`), navigation (`app-shell`).

## Contract

```kotlin
data class SheetSummary(val id: SheetId, val title: String, val dir: File, val createdAt: Long, val updatedAt: Long, val lastOpenedAt: Long?, val thumbnail: File?, val elementCount: Int)
interface SheetLibrary {
    val sheets: Flow<List<SheetSummary>>                       // sorted by lastOpenedAt desc, then updatedAt desc
    val lastOpened: Flow<SheetSummary?>
    suspend fun createSheet(title: String? = null): SheetSummary
    suspend fun markOpened(id: SheetId)
    suspend fun rename(id: SheetId, title: String)
    suspend fun duplicate(id: SheetId): SheetSummary
    suspend fun delete(id: SheetId)
    suspend fun register(dir: File): SheetSummary               // used by sharing after an import
    suspend fun refreshThumbnail(id: SheetId, render: suspend (Sheet) -> Bitmap)
}
@Composable fun LibraryScreen(onNewSheet: () -> Unit, onOpenSheet: (SheetId) -> Unit, onShare: (SheetId) -> Unit, modifier: Modifier)
```

## Design decisions

1. **Index in its own Room database** `files/library.db` with one `sheet` table mirroring `SheetSummary`. The sheet directory remains the source of truth: on startup the library reconciles the table with the directories present (adds unknown directories by reading their `meta`, removes rows whose directory is gone), so a crash between steps never loses a sheet.
2. **Menu layout.** Three primary actions. "Continue" shows the last sheet's title and thumbnail and is hidden when no sheet exists. "Open older" expands to a grid of thumbnails (list on narrow screens) with title and relative time; long-press opens rename, duplicate, delete, share.
3. **Titles** default to "Sheet N" where N is the next unused number; rename is inline with validation (non-empty, 80 characters max).
4. **Delete** asks for confirmation, then moves the directory to `files/trash/<ulid>/` and purges trash older than 7 days or on low storage; a snackbar offers undo for 10 seconds.
5. **Duplicate** copies the directory to a new ULID, rewrites `meta.sheet_id`, keeps element IDs, and titles it "Copy of X".
6. **Thumbnails** are 512 px WebP files rendered by `canvas-engine` when a sheet is closed and when its `updatedAt` is newer than the thumbnail; the library never blocks on rendering.
7. **Last opened** is updated when the editor opens a sheet, not when the library lists it.

## Testing

- JVM unit tests with Room on the JVM (fallback `androidTest`): create, rename, duplicate, delete, trash purge; reconciliation adds and removes rows correctly; sort order.
- Compose UI tests: continue hidden with no sheets; grid shows thumbnails; rename validation; delete confirmation and undo.

## Success criteria

1. With 500 sheets the menu appears in under 500 ms and the grid scrolls at 60 fps.
2. "Continue" always opens the most recently opened sheet, including after a crash or reinstall-without-data-loss scenario.
3. Deleting a sheet frees its storage within 7 days and is undoable for 10 seconds.
4. Duplicating a 5,000-stroke sheet takes under 2 s and the copy opens identically.
5. No sheet directory on disk is ever absent from the library after startup reconciliation.

## Decisions (owner, 2026-10-20)

1. Deleted sheets stay in trash for 7 days, with the 10-second undo snackbar on top.
2. Search by title is deferred until users have many sheets.
