package io.github.subhaneetshrestha.afnopadd.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.withMatrix
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import io.github.subhaneetshrestha.afnopadd.canvas.internal.BackgroundPainter.drawBackground
import io.github.subhaneetshrestha.afnopadd.canvas.internal.InkBridge.toAndroidMatrix
import io.github.subhaneetshrestha.afnopadd.canvas.internal.StrokeCache
import io.github.subhaneetshrestha.afnopadd.canvas.internal.WetStrokeRouter
import io.github.subhaneetshrestha.afnopadd.canvas.internal.canvasGestures
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect

/**
 * The drawing surface. Observes the sheet and the camera, paints the background in screen space,
 * draws dry strokes through Ink's renderer with each element's exact frame transform, routes
 * gestures (two pointers to the camera, one pointer per [pointerRole]), draws wet strokes through
 * Jetpack Ink when [wetStrokeBrush] returns a brush, and reports its pixel size through
 * [onViewportChanged]. Only elements the [index] finds in view are drawn, minus [hiddenElements]
 * (a tool's in-gesture preview, such as the eraser's). Shapes and images render as placeholder
 * outlines until their painters land.
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
) {
    val snapshot by sheet.snapshot.collectAsState()
    val camera by cameraController.camera.collectAsState()
    var viewport by remember { mutableStateOf(Viewport(0, 0)) }
    var handoffTick by remember { mutableIntStateOf(0) }
    val renderer = remember { CanvasStrokeRenderer.create() }
    val strokeCache = remember { StrokeCache() }
    val router = remember(inputSink, cameraController, wetStrokeBrush) {
        WetStrokeRouter(inputSink, cameraController, { viewport }, wetStrokeBrush, onHandoffPending = { handoffTick++ })
    }

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                viewport = Viewport(size.width, size.height)
                onViewportChanged(viewport)
            }
            .canvasGestures(cameraController, router, pointerRole) { viewport },
    ) {
        Spacer(
            modifier = Modifier.fillMaxSize().drawBehind {
                @Suppress("UNUSED_EXPRESSION")
                handoffTick // read so a pending handoff redraws the dry layer even if nothing else changed
                drawBackground(background, camera, viewport)
                drawIntoCanvas { composeCanvas ->
                    val nativeCanvas = composeCanvas.nativeCanvas
                    for (element in index.visible(snapshot, visibleRect(camera, viewport))) {
                        if (element.id in hiddenElements) continue
                        if (element is StrokeElement) {
                            // Ink positions the stroke with the canvas transform; the matrix argument only
                            // tells it the stroke-to-screen scale for anti-aliasing, so both must agree.
                            val matrix = CanvasMath.frameToScreen(camera, viewport, element.anchor, element.frameLevel).toAndroidMatrix()
                            val stroke = strokeCache.strokeFor(element)
                            nativeCanvas.withMatrix(matrix) { renderer.draw(this, stroke, matrix) }
                        } else {
                            val topLeft = CanvasMath.worldToScreen(camera, viewport, WorldPoint(element.bounds.left, element.bounds.top))
                            val bottomRight = CanvasMath.worldToScreen(camera, viewport, WorldPoint(element.bounds.right, element.bounds.bottom))
                            drawRect(
                                color = PlaceholderOutline,
                                topLeft = Offset(topLeft.x.toFloat(), topLeft.y.toFloat()),
                                size = Size((bottomRight.x - topLeft.x).toFloat(), (bottomRight.y - topLeft.y).toFloat()),
                                style = Stroke(width = 1f),
                            )
                        }
                    }
                }
                // The dry layer now holds everything the tool committed; let Ink drop its copies after this frame.
                router.drainHandoff()?.let { ids ->
                    router.view?.let { view -> view.post { view.removeFinishedStrokes(ids) } }
                }
            },
        )
        // Jetpack Ink's low-latency wet-stroke layer. It never handles touch itself; the gesture
        // handler above feeds it through the router so pinch and finger policy stay ours.
        AndroidView(
            factory = { context ->
                InProgressStrokesView(context).apply {
                    isClickable = false
                    isFocusable = false
                    eagerInit()
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view -> router.view = view },
            onRelease = { router.view = null },
        )
    }
}

private fun visibleRect(camera: Camera, viewport: Viewport): WorldRect {
    val topLeft = CanvasMath.screenToWorld(camera, viewport, ScreenPoint(0.0, 0.0))
    val bottomRight = CanvasMath.screenToWorld(camera, viewport, ScreenPoint(viewport.widthPx.toDouble(), viewport.heightPx.toDouble()))
    return WorldRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
}

private val PlaceholderOutline = Color(0xFF9E9E9E)
