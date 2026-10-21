package io.github.subhaneetshrestha.afnopadd.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import io.github.subhaneetshrestha.afnopadd.canvas.internal.BackgroundPainter.drawBackground
import io.github.subhaneetshrestha.afnopadd.canvas.internal.DryLayer
import io.github.subhaneetshrestha.afnopadd.canvas.internal.SharedInkView
import io.github.subhaneetshrestha.afnopadd.canvas.internal.WetStrokeRouter
import io.github.subhaneetshrestha.afnopadd.canvas.internal.canvasGestures
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.Sheet
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
    var releaseTick by remember { mutableIntStateOf(0) }
    val dryLayer = remember { DryLayer() }
    // One router for the canvas's lifetime; it reads the current tool when each gesture starts, so a
    // tool switch never strands a stroke that the previous tool started.
    val currentSink by rememberUpdatedState(inputSink)
    val currentBrush by rememberUpdatedState(wetStrokeBrush)
    val router = remember(cameraController) {
        WetStrokeRouter({ currentSink }, cameraController, { viewport }, { frame -> currentBrush(frame) }, onReleasePending = { releaseTick++ })
    }
    // A replaced router must drop its Ink view, or the view keeps it and its tools alive.
    DisposableEffect(router) { onDispose { router.view = null } }

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
                releaseTick // read so a lifted stroke redraws the dry layer even if nothing else changed
                drawBackground(background, camera, viewport)
                drawIntoCanvas { composeCanvas ->
                    val visible = index.visible(snapshot, visibleRect(camera, viewport))
                    dryLayer.draw(composeCanvas.nativeCanvas, visible, hiddenElements, camera, viewport)
                }
                // The dry layer now draws everything the tool committed; Ink's copies can go after this frame.
                router.releaseAfterDraw()
            },
        )
        // Jetpack Ink's low-latency wet-stroke layer. It never handles touch itself; the gesture
        // handler above feeds it through the router so pinch and finger policy stay ours.
        AndroidView(
            factory = { context -> SharedInkView.obtain(context) },
            modifier = Modifier.fillMaxSize(),
            update = { view -> router.view = view },
            onRelease = { view ->
                router.view = null
                SharedInkView.release(view)
            },
        )
    }
}

private fun visibleRect(camera: Camera, viewport: Viewport): WorldRect {
    val topLeft = CanvasMath.screenToWorld(camera, viewport, ScreenPoint(0.0, 0.0))
    val bottomRight = CanvasMath.screenToWorld(camera, viewport, ScreenPoint(viewport.widthPx.toDouble(), viewport.heightPx.toDouble()))
    return WorldRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
}
