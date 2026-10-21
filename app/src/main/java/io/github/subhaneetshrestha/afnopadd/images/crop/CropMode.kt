package io.github.subhaneetshrestha.afnopadd.images.crop

import io.github.subhaneetshrestha.afnopadd.canvas.CanvasPointerEvent
import io.github.subhaneetshrestha.afnopadd.canvas.PointerPhase
import io.github.subhaneetshrestha.afnopadd.canvas.SelectionHandles
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.LocalRect
import io.github.subhaneetshrestha.afnopadd.document.withCommon
import io.github.subhaneetshrestha.afnopadd.tools.ToolContext
import io.github.subhaneetshrestha.afnopadd.tools.ToolController
import io.github.subhaneetshrestha.afnopadd.tools.select.Transforms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot

/**
 * Crop mode for one image (SPEC-image-board decision 5): the crop rectangle, in the image's own
 * units, has a handle on each corner. Dragging a corner moves it; dragging inside the rectangle
 * moves the whole crop; either is clamped to the image and to [MIN_SIZE_DP], previewed through
 * [pendingCrop], and stored as one "Crop" transaction when the pointer lifts. The crop is stored,
 * never applied to the pixels. The editor draws the whole image with the frame on top.
 */
class CropController(private val context: ToolContext, val imageId: ElementId) : ToolController {

    private sealed interface Grab {
        data class Corner(val corner: CropMath.Corner) : Grab
        data class Move(val startLocal: LocalPoint) : Grab
    }

    private class Gesture(val pointerId: Long, val grab: Grab, val startCrop: LocalRect)

    private val _pendingCrop = MutableStateFlow<LocalRect?>(null)
    /** The crop as it is being dragged, or null when no drag is in progress. */
    val pendingCrop: StateFlow<LocalRect?> = _pendingCrop.asStateFlow()

    private var gesture: Gesture? = null

    private fun image(): ImageElement? = context.sheet.snapshot.value.elements[imageId] as? ImageElement

    override fun onPointer(event: CanvasPointerEvent) {
        val image = image() ?: return
        when (event.phase) {
            PointerPhase.DOWN -> {
                val crop = CropMath.visible(image)
                gesture = grabAt(image, crop, event)?.let { Gesture(event.pointerId, it, crop) }
            }
            PointerPhase.MOVE -> gesture?.takeIf { it.pointerId == event.pointerId }?.let { _pendingCrop.value = cropFor(image, it, event) }
            PointerPhase.UP -> gesture?.takeIf { it.pointerId == event.pointerId }?.let { current ->
                val crop = cropFor(image, current, event)
                reset()
                if (crop != current.startCrop) commit(crop)
            }
            PointerPhase.CANCEL -> if (gesture?.pointerId == event.pointerId) reset()
        }
    }

    override fun onDeactivated() = reset()

    private fun reset() {
        gesture = null
        _pendingCrop.value = null
    }

    /** A corner handle within reach of the pointer, else the inside of the crop, else nothing. */
    private fun grabAt(image: ImageElement, crop: LocalRect, event: CanvasPointerEvent): Grab? {
        val zoom = context.camera.camera.value.zoom
        val reach = SelectionHandles.TOUCH_RADIUS_DP * context.density / zoom
        val corners = CropMath.cornersInWorld(image, crop)
        val nearest = corners.indices.minBy { hypot(corners[it].x - event.world.x, corners[it].y - event.world.y) }
        if (hypot(corners[nearest].x - event.world.x, corners[nearest].y - event.world.y) <= reach) {
            return Grab.Corner(CropMath.Corner.entries[nearest])
        }
        val local = CropMath.toImageLocal(event.world, image)
        return if (local.x in crop.left..crop.right && local.y in crop.top..crop.bottom) Grab.Move(local) else null
    }

    private fun cropFor(image: ImageElement, gesture: Gesture, event: CanvasPointerEvent): LocalRect {
        val local = CropMath.toImageLocal(event.world, image)
        val minSize = minSizeLocal(image)
        return when (val grab = gesture.grab) {
            is Grab.Corner -> CropMath.dragCorner(gesture.startCrop, grab.corner, local, image.size, minSize)
            is Grab.Move -> {
                val start = gesture.startCrop
                val width = start.right - start.left
                val height = start.bottom - start.top
                // A crop never exceeds the image (the decoder clamps it), but never let a bad one throw here.
                val left = (start.left + local.x - grab.startLocal.x).coerceIn(0f, maxOf(0f, image.size.width - width))
                val top = (start.top + local.y - grab.startLocal.y).coerceIn(0f, maxOf(0f, image.size.height - height))
                LocalRect(left, top, left + width, top + height)
            }
        }
    }

    /** [MIN_SIZE_DP] on screen, in the image's units. */
    private fun minSizeLocal(image: ImageElement): Float {
        val zoom = context.camera.camera.value.zoom
        val worldPerLocal = io.github.subhaneetshrestha.afnopadd.canvas.FrameLevels.worldUnitsPerLocalUnit(image.frameLevel)
        return (MIN_SIZE_DP * context.density / zoom / worldPerLocal).toFloat()
    }

    private fun commit(crop: LocalRect) {
        val now = context.clock()
        context.sheet.transact(label = "Crop") {
            update(imageId) { element ->
                val image = element as ImageElement
                // The whole image is the same as no crop.
                val stored = crop.takeUnless { it == LocalRect(0f, 0f, image.size.width, image.size.height) }
                image.copy(
                    crop = stored,
                    bounds = Transforms.imageBounds(image.anchor, image.frameLevel, image.size, image.rotationDeg, stored),
                ).withCommon(version = image.version + 1, versionNonce = context.random.nextInt(), updatedAt = now)
            }
        }
    }

    companion object {
        /** A crop never gets smaller than this on screen, so its handles stay apart. */
        const val MIN_SIZE_DP = 24.0
    }
}
