# Spec: sharing

Status: **APPROVED by the owner on 2026-10-20.** Module id `sharing`. Depends on: `sheet-document`, `canvas-engine`, `sheet-library`. Package: `io.github.subhaneetshrestha.afnopadd.sharing`. Project-wide rules in `SPEC.md` apply.

## Objective

Get a sheet out of the app and into someone else's hands with no server involved: export a flat image or PDF through the Android Sharesheet, send the editable sheet package, and open a package someone sent so the recipient can keep editing their own copy. Explicitly no sync, no cloud links, no accounts.

## Scope

**In:** PNG/JPEG export with region, scale, and background options; PDF export with vector strokes and shapes; "Share" and "Save to…" for exports and packages; intent filters to receive packages via `ACTION_VIEW` and `ACTION_SEND`; the import flow (preview, confirm, import into the library, open); duplicate-origin handling.

**Out:** the package format itself (`sheet-document` owns it), cloud, sync, collaboration.

## Contract

```kotlin
sealed interface ExportRegion { data object AllContent : ExportRegion; data object CurrentView : ExportRegion; data class Selection(val ids: Set<ElementId>) : ExportRegion }
data class ImageExportOptions(val region: ExportRegion, val scale: ExportScale, val format: ImageFormat, val background: Background, val marginDp: Int = 24)
data class PdfExportOptions(val region: ExportRegion, val background: Background, val marginDp: Int = 24)
interface Exporter {
    suspend fun exportImage(sheet: Sheet, camera: Camera, options: ImageExportOptions): File     // in cache/exports/
    suspend fun exportPdf(sheet: Sheet, camera: Camera, options: PdfExportOptions): File
    suspend fun exportPackage(sheet: Sheet): File                                               // <title>.afnopadd
}
interface ShareLauncher { fun share(file: File, mime: String, title: String); fun saveAs(file: File, mime: String, suggestedName: String) }
interface PackageReceiver { suspend fun inspect(uri: Uri): PackagePreview?; suspend fun import(uri: Uri): ImportResult }   // Ok(SheetSummary) | Error(reason)
```

Commands: `cmd:export-image`, `cmd:export-pdf`, `cmd:share-sheet`, `cmd:save-sheet-as`.

## Design decisions

1. **Image export** renders through `canvas-engine.Renderer.renderBitmap` on a software canvas (hardware canvases throw above about 100 MB). Scale options: 1x, 2x, 4x, or a target long edge; the output is capped at 100 megapixels with a message that suggests PDF or a smaller region. Background: white, sheet background, or transparent (PNG only). Files go to `cache/exports/` and are cleaned after 24 hours.
2. **PDF export** uses `android.graphics.pdf.PdfDocument`: one page sized to the region at 72 points per 1 world unit up to the PDF page limit of 14,400 points per side; larger regions are tiled into multiple pages with 1 cm overlap. Strokes and shapes are drawn through `Renderer.renderTo`, which falls back to Ink's path renderer on the PDF canvas, so they stay vector; images are embedded at their stored resolution.
3. **Sharing** uses `FileProvider` content URIs with `FLAG_GRANT_READ_URI_PERMISSION`, `ACTION_SEND`, and `Intent.createChooser`; "Save to…" uses `CreateDocument` so Drive, Nextcloud, and local folders all work without their APIs. Quick Share appears automatically in the Sharesheet.
4. **Package send** calls `sheet-document.exportPackage` after `flush()`, names the file `<title>.afnopadd`, MIME `application/vnd.afnopadd.sheet+zip`.
5. **Receiving.** Manifest intent filters on `MainActivity` (or a lightweight `ImportActivity`): `ACTION_VIEW` with `content` and `file` schemes for the custom MIME, for `application/zip`, for `application/octet-stream`, and for `*/*` with path pattern `.*\.afnopadd`; `ACTION_SEND` for the same MIME types. Every incoming stream is sniffed for the `mimetype` ZIP entry before anything else happens; non-matching files are rejected with a message.
6. **Import flow.** Show a preview (title, element count, embedded thumbnail if present, sender app if known) with "Import" and "Cancel". Import always creates a new sheet via `sheet-document.importPackage` and registers it in the library; if a sheet with the same `origin.sheetId` already exists the dialog says so and offers "Import as copy" (default) or "Cancel". No merge in v1. On success the editor opens the new sheet.
7. **Privacy.** Exports contain only sheet content; packages carry no device identifiers; the app version string is the only metadata beyond the sheet's own.

## Testing

- JVM unit tests: export option validation and pixel-cap math; PDF page tiling math; MIME sniffing on fixture bytes (valid package, renamed zip, random bytes, truncated file).
- Instrumented: end-to-end export of the fixture sheet to PNG at 4x and to PDF, then re-render comparison; `ACTION_VIEW` and `ACTION_SEND` delivery with a content URI whose display name and MIME are missing; import of a package whose origin already exists.
- Manual checklist: send through Gmail, Telegram, Files, Quick Share, and open from each; open from a file manager by tapping.

## Success criteria

1. Two devices: export on A, send through any messaging app, tap on B, import, edit on B; A is unaffected and B's copy survives further edits and re-shares.
2. PNG export at 4x of the fixture sheet completes in under 5 s; PDF of the same sheet opens in Android's viewer with vector strokes (zoomable without pixelation).
3. A renamed or stripped package (`.zip`, no name, `application/octet-stream`) still imports; a random ZIP is rejected with a specific message.
4. No `INTERNET` permission; no file leaves the device without a user action.

## Decisions (owner, 2026-10-20)

1. The share menu offers both the package and an image export, package first.
2. JPEG export (quality 90) is included alongside PNG; PDF is the vector option.
3. No read-only "open without importing" in v1.
