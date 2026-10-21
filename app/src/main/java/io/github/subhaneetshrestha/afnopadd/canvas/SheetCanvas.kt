package io.github.subhaneetshrestha.afnopadd.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.subhaneetshrestha.afnopadd.canvas.internal.BackgroundPainter.drawBackground
import io.github.subhaneetshrestha.afnopadd.canvas.internal.DryLayer
import io.github.subhaneetshrestha.afnopadd.canvas.internal.ImagePainter
import androidx.core.graphics.withSave
import io.github.subhaneetshrestha.afnopadd.canvas.internal.tiles.TileGrid
import io.github.subhaneetshrestha.afnopadd.canvas.internal.tiles.TileLayer
import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.subhaneetshrestha.afnopadd.canvas.internal.InkHost
import io.github.subhaneetshrestha.afnopadd.canvas.internal.WetStrokeRouter
import io.github.subhaneetshrestha.afnopadd.canvas.internal.canvasGestures
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.WorldRect

/**
 * The drawing surface. Observes the sheet and the camera, paints the background in screen space,
 * draws dry strokes through Ink's renderer with each element's exact frame transform, routes
 * gestures (two pointers to the camera, one pointer per [pointerRole]), draws wet strokes through
 * Jetpack Ink when [wetStrokeBrush] returns a brush, and reports its pixel size through
 * [onViewportChanged]. Only elements the [index] finds in view are drawn, minus [hiddenElements]
 * (a tool's in-gesture preview, such as the eraser's), with [previewElements] (a shape being
 * dragged) drawn on top. With [secondPointerConstrains], a second pointer during a drawing gesture
 * is a constrain modifier rather than a pinch. [selectionOutline] and [marquee] are drawn as dashed
 * screen-space rectangles on top, and [cropFrame] (an image's crop corners in crop mode) with corner
 * handles. Strokes draw through Ink, shapes as vector paths, images through [imageLoader] (Coil
 * unless a test supplies its own), at a bitmap size that follows the zoom.
 */
@Composable
fun SheetCanvas(
    sheet: Sheet,
    cameraController: CameraController,
    inputSink: CanvasInputSink,
    modifier: Modifier = Modifier,
    background: Background = Background.PLAIN,
    pointerRole: (PointerKind) -> PointerRole = { PointerRole.DRAW },
    wetStrokeBrush: (StrokeFrame) -> BrushSpec? = { null },
    onViewportChanged: (Viewport) -> Unit = {},
    index: ElementIndex = remember(sheet) { ElementIndex() },
    hiddenElements: Set<ElementId> = emptySet(),
    previewElements: List<Element> = emptyList(),
    secondPointerConstrains: Boolean = false,
    selectionOutline: WorldRect? = null,
    marquee: WorldRect? = null,
    selectionHandles: Boolean = false,
    cropFrame: List<WorldPoint>? = null,
    imageLoader: ImageBitmapLoader? = null,
    previewTransform: PreviewTransform? = null,
) {
    val context = LocalContext.current
    val constrains by rememberUpdatedState(secondPointerConstrains)
    // The gesture handler restarts when its role function changes, which would cut a stroke short;
    // hand it one stable function that always asks the latest policy.
    val currentRole by rememberUpdatedState(pointerRole)
    val role = remember { { kind: PointerKind -> currentRole(kind) } }
    val snapshot by sheet.snapshot.collectAsState()
    val camera by cameraController.camera.collectAsState()
    var viewport by remember { mutableStateOf(Viewport(0, 0)) }
    var releaseTick by remember { mutableIntStateOf(0) }
    var imageTick by remember { mutableIntStateOf(0) }
    val loader = imageLoader ?: remember(context) { CoilImageBitmapLoader(context.applicationContext) }
    val dryLayer = remember(sheet, loader) {
        DryLayer(
            imagePainter = ImagePainter(
                loader = loader,
                // An in-memory sheet has no asset store; its images stay placeholders.
                fileFor = { id -> runCatching { sheet.assets.file(id) }.getOrNull() },
                onLoaded = { imageTick++ },
            ),
        )
    }
    DisposableEffect(dryLayer) { onDispose { dryLayer.release() } }
    // The tile pyramid for crowded or zoomed-out views; dropped when the app goes to the background.
    var tileTick by remember { mutableIntStateOf(0) }
    val tiles = remember(sheet) {
        val memoryClass = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
        TileLayer(TileGrid.budgetBytes(memoryClass), fileFor = { id -> runCatching { sheet.assets.file(id) }.getOrNull() }, onTileReady = { tileTick++ })
    }
    LocalTileLayerObserver.current?.let { observe -> SideEffect { observe(tiles) } }
    // Released only when the layer itself is replaced or the canvas goes; a new lifecycle owner just re-registers.
    DisposableEffect(tiles) { onDispose { tiles.release() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(tiles, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) tiles.trim() }
        val trimmer = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) tiles.trim()
            }
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            @Deprecated("Superseded by onTrimMemory")
            override fun onLowMemory() = tiles.trim()
        }
        lifecycle.addObserver(observer)
        context.applicationContext.registerComponentCallbacks(trimmer)
        onDispose {
            lifecycle.removeObserver(observer)
            context.applicationContext.unregisterComponentCallbacks(trimmer)
        }
    }
    // One router for the canvas's lifetime; it reads the current tool when each gesture starts, so a
    // tool switch never strands a stroke that the previous tool started.
    val currentSink by rememberUpdatedState(inputSink)
    val currentBrush by rememberUpdatedState(wetStrokeBrush)
    // Where this canvas sits in its window, so strokes on the window-sized hosted Ink view line up.
    val canvasInWindow = remember { FloatArray(2) }
    val router = remember(cameraController) {
        WetStrokeRouter(
            { currentSink }, cameraController, { viewport }, { frame -> currentBrush(frame) },
            onReleasePending = { releaseTick++ },
            canvasOffsetInView = { view ->
                val location = IntArray(2).also(view::getLocationInWindow)
                Offset(canvasInWindow[0] - location[0], canvasInWindow[1] - location[1])
            },
        )
    }
    // A replaced router must drop its Ink view, or the view keeps it and its tools alive.
    DisposableEffect(router) { onDispose { router.view = null } }
    // Jetpack Ink's low-latency wet-stroke layer, borrowed from the activity: it stays attached when
    // the canvas goes, because detaching it right after a stroke crashes Ink 1.0.0 (ADR 0003). It
    // never handles touch itself; the gesture handler feeds it through the router.
    var privateInk by remember { mutableStateOf(false) }
    DisposableEffect(router, context) {
        val hosted = InkHost.acquire(context)
        if (hosted != null) router.view = hosted else privateInk = true
        onDispose {
            if (hosted != null) {
                router.view = null
                InkHost.release(hosted)
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                viewport = Viewport(size.width, size.height)
                onViewportChanged(viewport)
            }
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                canvasInWindow[0] = position.x
                canvasInWindow[1] = position.y
            }
            .canvasGestures(cameraController, router, role, { constrains }) { viewport },
    ) {
        Spacer(
            modifier = Modifier.fillMaxSize().drawBehind {
                @Suppress("UNUSED_EXPRESSION")
                releaseTick // read so a lifted stroke redraws the dry layer even if nothing else changed
                @Suppress("UNUSED_EXPRESSION")
                imageTick // read so a bitmap that has just arrived is drawn
                @Suppress("UNUSED_EXPRESSION")
                tileTick // and a tile
                drawBackground(background, camera, viewport)
                drawIntoCanvas { composeCanvas ->
                    // One frame for every pass below (tiles' vector patches, the dry layer, previews), so
                    // meshes and bitmaps one pass drew are not evicted by the next.
                    dryLayer.beginFrame()
                    val visibleRect = visibleRect(camera, viewport)
                    val visible = index.visible(snapshot, visibleRect)
                    tiles.sync(snapshot)
                    if (tiles.shouldTile(visible, camera)) {
                        tiles.draw(composeCanvas.nativeCanvas, snapshot, visible, hiddenElements, camera, viewport, visibleRect, dryLayer)
                    } else {
                        dryLayer.draw(composeCanvas.nativeCanvas, visible, hiddenElements, camera, viewport)
                    }
                    // A tool's live preview (a shape being dragged) draws exactly like the element it will
                    // become; a selection being scaled or turned is drawn under its preview transform.
                    if (previewElements.isNotEmpty()) {
                        val native = composeCanvas.nativeCanvas
                        val transform = previewTransform
                        if (transform == null) {
                            dryLayer.draw(native, previewElements, emptySet(), camera, viewport)
                        } else {
                            val pivot = CanvasMath.worldToScreen(camera, viewport, transform.pivot)
                            native.withSave {
                                translate(pivot.x.toFloat(), pivot.y.toFloat())
                                rotate(transform.rotationDeg.toFloat())
                                scale(transform.scale.toFloat(), transform.scale.toFloat())
                                translate(-pivot.x.toFloat(), -pivot.y.toFloat())
                                dryLayer.draw(this, previewElements, emptySet(), camera, viewport)
                            }
                        }
                    }
                }
                selectionOutline?.let {
                    drawSelectionRect(it, camera, viewport, filled = false)
                    if (selectionHandles) drawSelectionHandles(it, camera, viewport)
                }
                marquee?.let { drawSelectionRect(it, camera, viewport, filled = true) }
                cropFrame?.let { drawCropFrame(it, camera, viewport) }
                // The dry layer now draws everything the tool committed; Ink's copies can go after this frame.
                router.releaseAfterDraw()
            },
        )
        // Without an activity to host it, or with a second canvas on screen, this canvas gets its own view.
        if (privateInk) {
            AndroidView(
                factory = InkHost::create,
                modifier = Modifier.fillMaxSize(),
                update = { view -> router.view = view },
                onRelease = { view ->
                    router.view = null
                    InkHost.release(view)
                },
            )
        }
    }
}

/**
 * A dashed selection rectangle in screen space, so it stays one pixel-crisp line at every zoom;
 * the selection outline is padded a little so it does not sit on the ink, the marquee is tinted.
 */
private fun DrawScope.drawSelectionRect(rect: WorldRect, camera: Camera, viewport: Viewport, filled: Boolean) {
    val topLeft = CanvasMath.worldToScreen(camera, viewport, WorldPoint(rect.left, rect.top))
    val bottomRight = CanvasMath.worldToScreen(camera, viewport, WorldPoint(rect.right, rect.bottom))
    val pad = if (filled) 0f else SelectionHandles.PAD_DP.dp.toPx()
    val origin = Offset(topLeft.x.toFloat() - pad, topLeft.y.toFloat() - pad)
    val size = Size((bottomRight.x - topLeft.x).toFloat() + 2 * pad, (bottomRight.y - topLeft.y).toFloat() + 2 * pad)
    if (filled) drawRect(SELECTION_COLOR.copy(alpha = 0.12f), origin, size)
    val dash = 6.dp.toPx()
    drawRect(SELECTION_COLOR, origin, size, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash / 2))))
}

/** The scale and rotate handles, at the positions [SelectionHandles] gives the select tool, the same size at every zoom. */
private fun DrawScope.drawSelectionHandles(bounds: WorldRect, camera: Camera, viewport: Viewport) {
    fun screenOf(handle: SelectionHandles.Handle): Offset {
        val p = CanvasMath.worldToScreen(camera, viewport, SelectionHandles.position(handle, bounds, camera.zoom, density))
        return Offset(p.x.toFloat(), p.y.toFloat())
    }
    val rotate = screenOf(SelectionHandles.Handle.ROTATE)
    val outlineTop = rotate.y + SelectionHandles.ROTATE_OFFSET_DP.dp.toPx()
    drawLine(SELECTION_COLOR, rotate, Offset(rotate.x, outlineTop), strokeWidth = 1.5.dp.toPx())
    val radius = SelectionHandles.RADIUS_DP.dp.toPx()
    for (centre in listOf(rotate, screenOf(SelectionHandles.Handle.SCALE))) {
        drawCircle(Color.White, radius, centre)
        drawCircle(SELECTION_COLOR, radius, centre, style = Stroke(width = 2.dp.toPx()))
    }
}

/** A crop rectangle, possibly turned with its image: its four corners joined, with a handle on each. */
private fun DrawScope.drawCropFrame(corners: List<WorldPoint>, camera: Camera, viewport: Viewport) {
    val points = corners.map { CanvasMath.worldToScreen(camera, viewport, it).let { p -> Offset(p.x.toFloat(), p.y.toFloat()) } }
    val outline = androidx.compose.ui.graphics.Path().apply {
        moveTo(points[0].x, points[0].y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(outline, Color.White, style = Stroke(width = 3.dp.toPx()))
    drawPath(outline, SELECTION_COLOR, style = Stroke(width = 1.5.dp.toPx()))
    val radius = SelectionHandles.RADIUS_DP.dp.toPx()
    for (corner in points) {
        drawCircle(Color.White, radius, corner)
        drawCircle(SELECTION_COLOR, radius, corner, style = Stroke(width = 2.dp.toPx()))
    }
}

/** Lets instrumented tests see a canvas's tile layer (how many tiles it holds or had to patch). */
internal val LocalTileLayerObserver = staticCompositionLocalOf<((TileLayer) -> Unit)?> { null }

/** The app's vermilion accent (its icon's dot), for selection outlines, handles, the marquee and crop frames. */
private val SELECTION_COLOR = Color(0xFFE4572E)

private fun visibleRect(camera: Camera, viewport: Viewport): WorldRect {
    val topLeft = CanvasMath.screenToWorld(camera, viewport, ScreenPoint(0.0, 0.0))
    val bottomRight = CanvasMath.screenToWorld(camera, viewport, ScreenPoint(viewport.widthPx.toDouble(), viewport.heightPx.toDouble()))
    return WorldRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
}
