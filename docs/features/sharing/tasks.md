# Tasks: sharing

Spec: [spec.md](spec.md). Roadmap, checkpoints and the definition of done: [docs/product/roadmap.md](../../product/roadmap.md). Task numbers are global across features; paths in a task are relative to `app/src/main/java/io/github/subhaneetshrestha/afnopadd/` unless they start with `app/`, `docs/` or `gradle/`.

| Task | Title | Status |
|---|---|---|
| [27](#task-27-image-export-png-jpeg-with-sharesheet-and-save-as) | Image export (PNG, JPEG) with Sharesheet and Save as | Not started |
| [28](#task-28-pdf-export) | PDF export | Not started |
| [29](#task-29-share-and-receive-packages) | Share and receive packages | Not started |

---

## Task 27: Image export (PNG, JPEG) with Sharesheet and Save as

**Status:** Not started

**Description:** Add the export options sheet (region: all content, current view, selection; scale 1x, 2x, 4x, or target long edge; PNG or JPEG; background white, sheet, or transparent for PNG), `Exporter.exportImage` through `Renderer.renderBitmap` with the 100-megapixel cap and message, `ShareLauncher` using `FileProvider` with `ACTION_SEND` and a chooser, `CreateDocument` for Save as, and cleanup of `cache/exports/` after 24 hours.

**Acceptance criteria:**
- [ ] Exporting the fixture at 4x completes in under 5 s and the PNG opens in the system viewer; JPEG output uses quality 90.
- [ ] Share hands the file to another app through a content URI with read permission; Save as writes through the documents UI.
- [ ] Requests above 100 megapixels are refused with a message suggesting PDF or a smaller region.

**Verification:**
- [ ] Tests pass: unit tests for option validation and pixel-cap math; `connectedDebugAndroidTest` export test
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: share a PNG to Gmail and save to Files

**Dependencies:** Task 14, Task 20

**Files likely touched:**
- `sharing/Exporter.kt`, `sharing/internal/ImageExporter.kt`, `sharing/ShareLauncher.kt`, `sharing/ui/ExportOptionsSheet.kt`
- `app/src/main/res/xml/file_paths.xml`, `app/src/main/AndroidManifest.xml`

**Estimated scope:** Medium

---

## Task 28: PDF export

**Status:** Not started

**Description:** Implement `Exporter.exportPdf` with `PdfDocument`: one page sized to the region at 72 points per world unit up to 14,400 points per side, larger regions tiled into pages with 1 cm overlap, strokes and shapes drawn through `Renderer.renderTo` so they stay vector, images embedded at stored resolution.

**Acceptance criteria:**
- [ ] The fixture exports to a PDF that opens in Android's viewer; zooming shows vector stroke edges, not pixels.
- [ ] A region wider than 14,400 points produces the expected page count with overlaps (unit test on the tiling math).
- [ ] Transparent background is not offered for PDF; white and sheet background render correctly.

**Verification:**
- [ ] Tests pass: unit tests for page tiling; `connectedDebugAndroidTest` PDF generation
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: open the PDF and zoom

**Dependencies:** Task 27

**Files likely touched:**
- `sharing/internal/PdfExporter.kt`, `sharing/ui/ExportOptionsSheet.kt`

**Estimated scope:** Small

---

## Task 29: Share and receive packages

**Status:** Not started

**Description:** Wire `cmd:share-sheet` and `cmd:save-sheet-as` (flush, export package, Sharesheet or Save as), the intent filters (`ACTION_VIEW` and `ACTION_SEND` for the custom MIME, `application/zip`, `application/octet-stream`, and `*/*` with the `.afnopadd` path pattern), stream sniffing for the `mimetype` entry, the import dialog with preview and "Import as copy" on duplicate origin, registration in the library, and opening the editor on success. Replace the library's share stub.

**Acceptance criteria:**
- [ ] A package sent from device A through a messaging app imports on device B after a tap, opens in the editor, and edits on B do not affect A.
- [ ] A content URI with no display name and `application/octet-stream` still imports; a random ZIP is rejected with a specific message.
- [ ] Importing a package whose origin already exists shows the duplicate dialog and creates a separate copy when confirmed.

**Verification:**
- [ ] Tests pass: `connectedDebugAndroidTest` intent-delivery tests; unit tests for sniffing
- [ ] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: `docs/checklists/release.md` sharing section across Gmail, Telegram, Files, Quick Share

**Dependencies:** Task 26, Task 13

**Files likely touched:**
- `sharing/PackageReceiver.kt`, `sharing/internal/PackageSniffer.kt`, `sharing/ui/ImportDialog.kt`, `sharing/internal/ShareCommands.kt`
- `shell/MainActivity.kt`, `app/src/main/AndroidManifest.xml`, `library/LibraryScreen.kt`

**Estimated scope:** Medium
