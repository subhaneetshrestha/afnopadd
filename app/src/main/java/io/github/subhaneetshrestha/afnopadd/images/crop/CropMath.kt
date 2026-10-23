package io.github.subhaneetshrestha.afnopadd.images.crop

import io.github.subhaneetshrestha.afnopadd.canvas.FrameLevels
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.LocalRect
import io.github.subhaneetshrestha.afnopadd.document.LocalSize
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Crop rectangles live in the image's own frame units, from (0, 0) at its unturned top-left corner
 * to its size, so a crop survives moving, scaling and turning the image (image-board spec decision 5).
 */
object CropMath {

    enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }

    /** [crop] kept inside the image and at least [minSize] on each side, growing right and down from its top-left. */
    fun clamp(crop: LocalRect, size: LocalSize, minSize: Float): LocalRect {
        val minWidth = min(minSize, size.width)
        val minHeight = min(minSize, size.height)
        var left = crop.left.coerceIn(0f, size.width - minWidth)
        var top = crop.top.coerceIn(0f, size.height - minHeight)
        val right = crop.right.coerceIn(left + minWidth, size.width)
        val bottom = crop.bottom.coerceIn(top + minHeight, size.height)
        left = min(left, right - minWidth)
        top = min(top, bottom - minHeight)
        return LocalRect(left, top, right, bottom)
    }

    /** [crop] with [corner] moved to [to] (image units), the opposite corner fixed, clamped. */
    fun dragCorner(crop: LocalRect, corner: Corner, to: LocalPoint, size: LocalSize, minSize: Float): LocalRect {
        val x = to.x.coerceIn(0f, size.width)
        val y = to.y.coerceIn(0f, size.height)
        return when (corner) {
            Corner.TOP_LEFT -> LocalRect(min(x, crop.right - minSize), min(y, crop.bottom - minSize), crop.right, crop.bottom)
            Corner.TOP_RIGHT -> LocalRect(crop.left, min(y, crop.bottom - minSize), max(x, crop.left + minSize), crop.bottom)
            Corner.BOTTOM_RIGHT -> LocalRect(crop.left, crop.top, max(x, crop.left + minSize), max(y, crop.top + minSize))
            Corner.BOTTOM_LEFT -> LocalRect(min(x, crop.right - minSize), crop.top, crop.right, max(y, crop.top + minSize))
        }.let { clamp(it, size, minSize) }
    }

    /** [point] in the image's own units: undoes its anchor, frame and turn about its centre. */
    fun toImageLocal(point: WorldPoint, image: ImageElement): LocalPoint {
        val perWorld = 1.0 / FrameLevels.worldUnitsPerLocalUnit(image.frameLevel)
        val cx = image.size.width / 2.0
        val cy = image.size.height / 2.0
        val dx = (point.x - image.anchor.x) * perWorld - cx
        val dy = (point.y - image.anchor.y) * perWorld - cy
        val radians = -Math.toRadians(image.rotationDeg.toDouble())
        return LocalPoint((cx + dx * cos(radians) - dy * sin(radians)).toFloat(), (cy + dx * sin(radians) + dy * cos(radians)).toFloat())
    }

    /** The world positions of [rect]'s corners (image units) in [Corner] order, as the image is placed and turned. */
    fun cornersInWorld(image: ImageElement, rect: LocalRect): List<WorldPoint> {
        val unit = FrameLevels.worldUnitsPerLocalUnit(image.frameLevel)
        val cx = image.size.width / 2.0
        val cy = image.size.height / 2.0
        val radians = Math.toRadians(image.rotationDeg.toDouble())
        return listOf(rect.left to rect.top, rect.right to rect.top, rect.right to rect.bottom, rect.left to rect.bottom).map { (x, y) ->
            val dx = x - cx
            val dy = y - cy
            WorldPoint(
                image.anchor.x + (cx + dx * cos(radians) - dy * sin(radians)) * unit,
                image.anchor.y + (cy + dx * sin(radians) + dy * cos(radians)) * unit,
            )
        }
    }

    /** The rectangle a crop covers, or the whole image without one. */
    fun visible(image: ImageElement): LocalRect = image.crop ?: LocalRect(0f, 0f, image.size.width, image.size.height)
}
