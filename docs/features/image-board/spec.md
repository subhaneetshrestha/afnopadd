# Spec: image-board

Status: **APPROVED by the owner on 2026-10-20.** Module id `image-board`. Depends on: `canvas-engine`, `sheet-document`, `drawing-tools`. Package: `io.github.subhaneetshrestha.afnopadd.images`. Project-wide rules in `docs/product/spec.md` apply.

## Objective

Let the user bring photos and pictures onto the sheet and arrange them like a sticky board: import from the photo picker, the documents UI, or the camera; store them inside the sheet so it stays self-contained; place, move, scale, rotate, crop, and reorder them with the same handles as other elements.

## Scope

**In:** import entry points; the decode and normalise pipeline (EXIF, downsampling, re-encoding); asset registration; default placement; crop mode; image-specific context actions (reset crop, replace image, duplicate); multi-image import; sticky-board helpers (bring to front/back, arrange selected images in a row or grid).

**Out:** text sticky notes (reserved `NoteElement`, no UI in v1), drawing on images (strokes simply sit above them in z-order), image filters, camera viewfinder UI (system camera app via `TakePicture`).

## Contract

```kotlin
interface ImageImporter {
    suspend fun import(sources: List<Uri>, sheet: Sheet, placement: Placement): ImportSummary   // per-URI Ok(ElementId) | Error(reason)
}
sealed interface ImageSource { data object PhotoPicker : ImageSource; data object Documents : ImageSource; data object Camera : ImageSource }
@Composable fun rememberImagePicker(onPicked: (List<Uri>) -> Unit): ImagePickerLauncher   // wraps PickMultipleVisualMedia, OpenMultipleDocuments, TakePicture
data class Placement(val center: WorldPoint, val frameLevel: Int, val maxSizeFraction: Float = 0.6f)
```

As built in Task 22: `Placement(center, frameLevel, shortSideWorld, gapWorld, maxSizeFraction: Double = 0.6)` (the box and the gap need the viewport's shorter side and the 16 dp gap in world units); `ImageImporter.import(sources, sheet, placement, onProgress)` returns an `ImportSummary` of `ImportResult.Imported(source, id)` or `ImportResult.Failed(source, ImportFailure)` with `UNSUPPORTED`, `UNREADABLE` or `NOT_STORED`, and adds the batch as one transaction. An upright JPEG, PNG or WebP within the cap is stored byte for byte.

Image-specific commands registered with `CommandRegistry`: `cmd:image-import`, `cmd:image-crop`, `cmd:image-reset-crop`, `cmd:arrange-row` (`cmd:arrange-grid` is deferred past v1).

## Design decisions

1. **Sources.** `PickMultipleVisualMedia` (no permission, up to 20 at once), `OpenMultipleDocuments("image/*")` for files outside the gallery, `TakePicture` into a FileProvider URI in the cache directory. No `READ_MEDIA_*` permissions are requested.
2. **Copy, never reference.** Bytes are streamed into the sheet's `AssetStore` immediately; picker URI grants are never persisted.
3. **Normalise on import.** Decode bounds first; if the long edge exceeds 4,096 px, decode with a power-of-two sample size and re-encode (JPEG quality 90 for opaque images, lossless WebP for images with alpha); bake EXIF orientation into the pixels. The stored asset is therefore at most 4,096 px and the original file is not kept (open question 1). HEIF is decoded where the device supports it; unsupported formats produce a per-file error, not a failed batch.
4. **Placement.** A single image is centred in the viewport and scaled to fit 60 percent of the shorter viewport side; multiple images are laid out in a row with 16 dp gaps at the same scale. The element's frame level is the current camera level, so a sticky placed at 1x stays a sensible size when zoomed.
5. **Transforms** reuse the select tool's handles; crop is a mode entered from the context bar showing an adjustable rectangle in image space; the crop is stored, not applied destructively.
6. **Rendering** is delegated to `canvas-engine` through Coil 3 requests sized per zoom bucket; decoding never happens on the main thread.
7. **Memory.** Import runs one image at a time on `Dispatchers.IO` with a bounded in-memory bitmap (the 4,096 px cap bounds it at 64 MB ARGB); batches report progress.

## Testing

- JVM unit tests: placement math for one and many images; crop rectangle clamping; import summary aggregation.
- Instrumented: import fixtures (JPEG with EXIF rotation 6, PNG with alpha, a 48 MP JPEG, an unsupported file) and assert asset size, orientation, format, and error reporting; memory stays under 300 MB for a 20-image batch.
- Compose UI tests: picker launcher wiring; crop mode handles.

## Success criteria

1. Importing 20 photos of 12 MP each completes in under 10 s on the reference phone with no out-of-memory error.
2. An EXIF-rotated photo appears upright; an image with alpha keeps its transparency.
3. A sheet with imported images exports and re-imports with byte-identical assets (via `sheet-document`).
4. Images can be moved, scaled, rotated, cropped, reordered, duplicated, and deleted, each as one undoable gesture.
5. No image-related permission appears in the manifest.

## Decisions (owner, 2026-10-20)

1. Only the normalised copy is stored; the original file is not kept.
2. Maximum stored long edge is 4,096 px.
3. "Arrange in row" ships in v1; "arrange in grid" waits for user demand.
