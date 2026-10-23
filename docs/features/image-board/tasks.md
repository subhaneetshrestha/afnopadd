# Tasks: image-board

Spec: [spec.md](spec.md). Roadmap, checkpoints and the definition of done: [docs/product/roadmap.md](../../product/roadmap.md). Task numbers are global across features; paths in a task are relative to `app/src/main/java/io/github/subhaneetshrestha/afnopadd/` unless they start with `app/`, `docs/` or `gradle/`.

| Task | Title | Status |
|---|---|---|
| [22](#task-22-image-import-pipeline) | Image import pipeline | Done 2026-10-21 (reference-phone timing pending) |
| [23](#task-23-image-rendering-crop-mode-arrange-in-row) | Image rendering, crop mode, arrange in row | Done 2026-10-21 |

---

## Task 22: Image import pipeline

**Status:** Done 2026-10-21 (reference-phone timing pending)

**Description:** Implement `rememberImagePicker` (photo picker for up to 20 images, documents UI, system camera via `TakePicture` with a FileProvider) and `ImageImporter`: stream bytes into `AssetStore`, decode bounds, downsample to at most 4,096 px, bake EXIF orientation, re-encode (JPEG 90 opaque, lossless WebP with alpha), and create `ImageElement`s placed at the viewport centre (single) or in a row (many) at 60 percent of the shorter viewport side.

**Acceptance criteria:**
- [x] Fixtures: an EXIF-rotated JPEG imports upright; a PNG with alpha keeps transparency; a 48-megapixel JPEG lands at 4,096 px; an unsupported file yields a per-file error and the batch continues (`ImageImporterTest`, fixtures generated in the test; a missing file is reported as unreadable, and a small upright image is stored byte for byte).
- [ ] Importing 20 photos of 12 megapixels completes in under 10 s on the reference phone with peak memory under 300 MB. Emulator (`ImageImportPerfTest`, half the photos EXIF-rotated so they take the re-encode path): 5.1 s, peak 100 MB (+92 MB over baseline). Reference phone pending (owner).
- [x] No image or storage permission appears in the merged manifest (`ImagePickerTest` reads the installed app's requested permissions: no `READ_MEDIA_*`, storage, camera or media-location permission).

**Verification:**
- [x] Tests pass: `connectedDebugAndroidTest` for the images package (12); unit tests for placement math and the import summary (`ImagePlacementTest`, 4); `.\gradlew.bat check` (226, Lint); full suite 135/135 on a cold `afnopadd36`. Mutation checks: ignoring EXIF and skipping the downscale each fail a fixture test.
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: import from gallery, files, and camera (owner; needs the `cmd:image-import` toolbar entry from Task 23)

**Dependencies:** Task 6, Task 11

**Files likely touched:**
- `images/ImagePicker.kt`, `images/ImageImporter.kt`, `images/internal/ImageNormalizer.kt`, `images/internal/Placement.kt`
- `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/file_paths.xml`

**Estimated scope:** Medium

**Notes:**

`ImageDecoder` decodes and turns the image and scales it straight to the 4,096 px cap (exactly 4,096, rather than the nearest power-of-two sample size the spec mentions), so a 48 MP photo never sits in memory at full size. Images with alpha are stored as lossless WebP on API 30+ and PNG on API 29, which cannot write lossless WebP. `Placement` carries the viewport's shorter side and the gap in world units, since the 60 percent box and the 16 dp gap depend on them, and its fraction is a `Double`. The picker (`rememberImagePicker`) wraps the photo picker (up to 20), the documents UI, and `TakePicture` into a cache file shared through a FileProvider (`<applicationId>.files`, cache `camera/` only; files older than a day are cleared). The picker and importer are not wired into the editor yet; `cmd:image-import` and image drawing are Task 23.

---

## Task 23: Image rendering, crop mode, arrange in row

**Status:** Done 2026-10-21

**Description:** Render `ImageElement`s in `canvas-engine` through Coil 3 requests sized per zoom bucket with hardware bitmaps and bilinear filtering, honouring crop and rotation; add crop mode (adjustable rectangle in image space, stored not applied) and `cmd:arrange-row` for selected images, plus `cmd:image-import` wired into the toolbar.

**Acceptance criteria:**
- [x] Images appear at the right place and size at every zoom; zooming out requests smaller bitmaps (`ImageRenderingTest`: frame at 1x and 2x; requested sizes 1,024, 256, 64 px going from 4x to 0.05x through an injected loader; `ImageBucketsTest`).
- [x] Crop mode stores a rectangle and rendering shows only the cropped area; reset crop restores the full image (`CropControllerTest`, `CropMathTest`, `ImageRenderingTest.aCropShowsOnlyTheCroppedPart` through real Coil, `EditorFlowTest.anImageCanBeCroppedAndTheCropReset`).
- [x] Arrange in row lines up selected images left to right with 16 dp gaps in one transaction (`ImageCommandsTest`, `EditorFlowTest.selectedImagesArrangeInARow`).

**Verification:**
- [x] Tests pass: unit tests for crop clamping and arrangement (`.\gradlew.bat check`, 242, Lint); instrumented `ImageRenderingTest` (4, including a turned image) and `EditorFlowTest` (19, including the "Add image" choice); full suite 142/142 on a cold `afnopadd36`. Mutation checks: no crop clip, no turn, and a fixed bitmap size each fail a rendering test.
- [x] Build succeeds: `.\gradlew.bat assembleDebug`
- [ ] Manual check: import, crop, arrange on a device (owner)

**Dependencies:** Task 22, Task 20

**Files likely touched:**
- `canvas/internal/ImagePainter.kt`
- `images/crop/CropMode.kt`, `images/commands/ImageCommands.kt`
- `app/src/main/assets/tool_layout_default.json`

**Estimated scope:** Medium

**Notes:**

`ImagePainter` keeps one bitmap per asset at the power-of-two bucket of its on-screen size (64 to 4,096 px) and draws the previous one, scaled, until a new size arrives; bitmaps are held within 96 MB, dropping assets not drawn this frame first. Loading goes through `ImageBitmapLoader` (Coil 3 in the app: hardware bitmaps, memory cache keyed on file and size), which tests replace. Crop mode is entered from the selection's context bar (Crop, Reset crop and Arrange in row join it for images); it shows the whole image under a frame with corner handles, a drag inside moves the crop, and Done leaves. A crop never gets below 24 dp on screen; a crop of the whole image is stored as none. "Add image" (`cmd:image-import`, end of the top toolbar row) offers photos, files or camera; imported images arrive selected with the select tool active, and failures are reported in a toast. Not done here: thumbnails and the software renderer still draw images as placeholder outlines; drawing them off screen belongs with image export (Task 27).
