# Spec: canvas-engine

Status: **APPROVED by the owner on 2026-10-20.** Module id `canvas-engine`. Depends on: `sheet-document`. Package: `io.github.subhaneetshrestha.afnopadd.canvas`. Project-wide rules in `SPEC.md` apply.

## Objective

Show a sheet on screen at any zoom and turn pointer input into world-space events. This module owns the camera, the mapping between screen pixels and sheet coordinates, Jetpack Ink stroke rendering (wet and dry), rendering of shapes and images, the spatial index and culling, the raster tile cache that keeps zoomed-out views fast, hit-testing, and off-screen rendering for thumbnails and exports. It knows nothing about tools, toolbars, or files.

## Scope

**In:** camera model and gestures (pan, pinch zoom, optional two-finger rotate off by default); frame-aware transforms; Ink `InProgressStrokes` integration and `CanvasStrokeRenderer` for dry strokes; re-tessellation for deep relative zoom; shape and image rendering; spatial index; dirty regions and invalidation; tile pyramid; hit-testing and marquee queries; `Renderer` for off-screen bitmaps and PDF canvases; background styles (plain, grid, dots); selection overlay drawing primitives used by tools.

**Out:** deciding what a pointer gesture *means* (`drawing-tools`), image decoding and import (`image-board`), persistence (`sheet-document`).

## Contract

```kotlin
data class Camera(val center: WorldPoint, val zoom: Double)             // zoom = screen px per world unit
class CameraController { val camera: StateFlow<Camera>; fun panBy(dxPx, dyPx); fun zoomBy(factor, pivotPx); fun animateTo(camera); fun fit(bounds: WorldRect, paddingPx) }
object CanvasMath { fun worldToScreen(camera, viewportPx, point): ScreenPoint; fun screenToWorld(...): WorldPoint; fun frameToScreenMatrix(camera, viewportPx, anchor, frameLevel): android.graphics.Matrix }

@Composable fun SheetCanvas(
    sheet: Sheet, cameraController: CameraController, inputSink: CanvasInputSink,
    wetStrokeBrush: () -> BrushSpec?, overlay: DrawScope.(CanvasFrame) -> Unit, modifier: Modifier,
)
interface CanvasInputSink {                                    // implemented by the active tool
    fun onPointer(event: CanvasPointerEvent)                   // world point, pressure, tilt, orientation, tool type, phase
    fun onWetStrokeFinished(stroke: FinishedStroke)            // Ink stroke + inputs, already in frame units
}
interface SpatialIndex { fun query(rect: WorldRect): List<ElementId>; fun nearest(point: WorldPoint, maxDistance: Double): ElementId? }
class ElementIndex : SpatialIndex { fun sync(snapshot: ElementSnapshot); fun visible(snapshot, rect): List<Element> }   // shared by canvas and tools
interface HitTester {
    fun elementAt(point: WorldPoint, tolerancePx: Float, camera: Camera): ElementId?           // top-most
    fun elementsIn(rect: WorldRect): List<ElementId>                                           // bounds fully inside, back to front
    fun elementsAlong(from: WorldPoint, to: WorldPoint, radius: Double): List<ElementId>       // eraser path, front to back
}
fun interface StrokeHitTest { fun touches(stroke, from: LocalPoint, to: LocalPoint, radius: Float): Boolean }   // Centerline (JVM) or ink() (mesh)
interface Renderer {
    fun renderBitmap(snapshot: ElementSnapshot, bounds: WorldRect, scalePxPerUnit: Double, background: Background): Bitmap   // software canvas, tiled internally above 100 MP
    fun renderTo(canvas: android.graphics.Canvas, snapshot: ElementSnapshot, bounds: WorldRect, scalePxPerUnit: Double)     // used for PDF pages
}
```

## Design decisions

1. **Double-precision camera, float only at the edge.** All camera math is `Double`. For each element the engine composes `camera ∘ frame(anchor, frameLevel)` in `Double` and converts to a float `Matrix` as the last step, so the float matrix only ever carries screen-pixel-sized translations. This is what keeps strokes stable at 100,000x.
2. **Zoom range** is clamped to 0.001x to 100,000x (ratio 1e8) and buckets are `floor(log2(zoom))`. Pinch gestures use the focal point as pivot; double-tap zooms 2x toward the tap.
3. **Wet strokes through Ink.** `InProgressStrokes` runs in the current frame level `floor(log10(zoom))`; its `pointerEventToWorldTransform` is the inverse of the frame-to-screen matrix, so Ink sees frame units and brush sizes stay in its recommended range. Motion prediction from `input-motionprediction` is enabled. On finish, the engine hands the stroke to the active tool through `onWetStrokeFinished` and draws it as a dry stroke in the same frame to avoid the wet-to-dry flicker Ink's documentation warns about.
4. **Dry strokes.** Ink `Stroke` objects are built lazily from `StrokeElement.points` and cached per element (LRU by memory). Rendering uses `CanvasStrokeRenderer.draw` with the exact per-element matrix. When the relative zoom between the camera and the stroke's frame level exceeds 100x, the engine re-tessellates the stroke with a 100x finer `epsilon` and caches that variant per bucket, so zooming into an old coarse stroke never shows facets.
5. **Tile pyramid for zoomed-out views.** 256 px tiles keyed by `(bucket, tileX, tileY)` rendered on a background thread into hardware bitmaps, LRU budget 96 MB, invalidated by dirty rectangles after each transaction. Tiles are used only when the bucket is below the vector threshold (strokes thinner than 0.5 px or more than 2,000 visible elements); otherwise the engine draws vectors directly. This is on the critical path for devices below API 34, where Ink renders dry strokes with `drawPath`.
6. **Spatial index** is an in-memory R-tree over world-space bounds, bulk-loaded (sort-tile-recursive) from the first snapshot and updated incrementally after that: each `ElementSnapshot` made by a transaction names the ids it changed (`changedSince`), so a commit touches only those entries; any other snapshot is diffed by reference. Stroke hit-testing intersects the eraser path with the Ink mesh using two triangles, because Ink 1.0.0's `ImmutableParallelogram.fromSegmentAndPadding` reports coverage far from where the parallelogram lies (Task 17).
7. **Images** render from Coil 3 bitmaps requested at the power-of-two size that matches the bucket (Coil's size-keyed cache is the mip pyramid), drawn with bilinear filtering through the element's matrix, honouring crop and rotation. Pixelation at deep zoom is expected and documented.
8. **Shapes** are drawn as `Path` with stroke width in frame units scaled through the matrix, anti-aliased, with arrowheads scaled to line width.
9. **Gesture policy** is injected: the engine reports every pointer with its tool type; `drawing-tools` decides whether a single finger draws or pans, and its hand tool pans with any single pointer. Two-pointer gestures (pinch zoom around the focal point, two-finger pan) always belong to the camera in every tool. Pointer cancellation (`ACTION_CANCEL`, `FLAG_CANCELED`) cancels the wet stroke. Two-finger rotate is not in v1.
10. **Backgrounds**: plain, 5 mm grid, dots; drawn in screen space at the current bucket so they do not alias.

## Testing

- JVM unit tests for `CanvasMath`: round trip world → screen → world error under 1e-6 world units at zoom 1e5 with coordinates up to 1e6; matrix composition equals the double-precision reference for 10,000 random cameras; frame-level selection at bucket boundaries.
- R-tree tests: query correctness against brute force on 50,000 random rectangles; incremental update after move/delete.
- Instrumented: wet-to-dry handoff produces no missing frame (screenshot diff of the frame after finish); render a 5,000-stroke fixture sheet and time pan/zoom through all buckets; tile invalidation after a transaction redraws only the affected tiles; `renderBitmap` of a 100 MP region tiles correctly and matches a direct render of a small region pixel-for-pixel.
- Manual: stroke latency and palm rejection on a stylus tablet.

## Success criteria

1. Pan and zoom at 60 fps on the fixture sheet (5,000 strokes, 20 images) on both reference devices at every bucket; no frame over 16 ms during a 1,000-point stroke.
2. Wet stroke visible within the next display frame on API 29+.
3. At 100,000x, a stroke drawn at 1x shows smooth edges (re-tessellation) and does not jitter relative to neighbouring strokes (double camera).
4. Tile memory never exceeds its budget; dropping to the background releases all tiles within one second.
5. `renderBitmap` output of the whole sheet at 4x matches on-screen rendering for the same region (pixel diff under 1 percent).
6. No public API references tool or file concepts.

## Decisions (owner, 2026-10-20)

1. Stylus tilt access is settled by a spike in the planning phase: Compose pointer API if it exposes tilt, otherwise Ink's own input path or `pointerInteropFilter` on the drawing surface.
2. Two-finger rotate is excluded from v1.
3. The tile budget scales with `ActivityManager.memoryClass`: 10 percent of it, floor 48 MB, cap 128 MB.
