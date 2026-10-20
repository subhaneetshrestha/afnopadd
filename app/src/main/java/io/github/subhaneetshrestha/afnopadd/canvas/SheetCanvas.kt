package io.github.subhaneetshrestha.afnopadd.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import io.github.subhaneetshrestha.afnopadd.canvas.internal.BackgroundPainter.drawBackground
import io.github.subhaneetshrestha.afnopadd.canvas.internal.canvasGestures
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint

/**
 * The drawing surface. Observes the sheet and the camera, paints the background in screen space,
 * routes gestures (two pointers to the camera, one pointer per [pointerRole]), and reports its
 * pixel size through [onViewportChanged]. Element rendering is a placeholder outline until the
 * stroke, shape and image painters land.
 */
@Composable
fun SheetCanvas(
    sheet: Sheet,
    cameraController: CameraController,
    inputSink: CanvasInputSink,
    modifier: Modifier = Modifier,
    background: Background = Background.PLAIN,
    pointerRole: (PointerKind) -> PointerRole = { PointerRole.DRAW },
    onViewportChanged: (Viewport) -> Unit = {},
) {
    val snapshot by sheet.snapshot.collectAsState()
    val camera by cameraController.camera.collectAsState()
    var viewport by remember { mutableStateOf(Viewport(0, 0)) }

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                viewport = Viewport(size.width, size.height)
                onViewportChanged(viewport)
            }
            .canvasGestures(cameraController, inputSink, pointerRole) { viewport }
            .drawBehind {
                drawBackground(background, camera, viewport)
                for (element in snapshot.ordered) {
                    val topLeft = CanvasMath.worldToScreen(camera, viewport, WorldPoint(element.bounds.left, element.bounds.top))
                    val bottomRight = CanvasMath.worldToScreen(camera, viewport, WorldPoint(element.bounds.right, element.bounds.bottom))
                    if (bottomRight.x < 0 || bottomRight.y < 0 || topLeft.x > viewport.widthPx || topLeft.y > viewport.heightPx) continue
                    drawRect(
                        color = PlaceholderOutline,
                        topLeft = Offset(topLeft.x.toFloat(), topLeft.y.toFloat()),
                        size = Size((bottomRight.x - topLeft.x).toFloat(), (bottomRight.y - topLeft.y).toFloat()),
                        style = Stroke(width = 1f),
                    )
                }
            },
    )
}

private val PlaceholderOutline = Color(0xFF9E9E9E)
