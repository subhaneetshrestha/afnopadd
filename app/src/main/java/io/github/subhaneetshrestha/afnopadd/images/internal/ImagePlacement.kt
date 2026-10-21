package io.github.subhaneetshrestha.afnopadd.images.internal

import io.github.subhaneetshrestha.afnopadd.canvas.FrameLevels
import io.github.subhaneetshrestha.afnopadd.document.LocalSize
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.images.Placement

/** Default placement of imported images (SPEC-image-board decision 4). Pure, so it is unit-tested on the JVM. */
internal object ImagePlacement {

    data class PixelSize(val width: Int, val height: Int)

    /**
     * World rectangles for images of [sizes], in order: each fitted to a square of the placement's
     * fraction of the viewport's shorter side, laid left to right with the gap between them, the
     * row centred on the placement centre and each image centred vertically on it.
     */
    fun layout(sizes: List<PixelSize>, placement: Placement): List<WorldRect> {
        val box = placement.shortSideWorld * placement.maxSizeFraction
        val fitted = sizes.map { size ->
            val scale = box / maxOf(size.width, size.height).coerceAtLeast(1)
            (size.width * scale) to (size.height * scale)
        }
        val rowWidth = fitted.sumOf { it.first } + placement.gapWorld * (fitted.size - 1).coerceAtLeast(0)
        var left = placement.center.x - rowWidth / 2
        return fitted.map { (width, height) ->
            val top = placement.center.y - height / 2
            WorldRect(left, top, left + width, top + height).also { left += width + placement.gapWorld }
        }
    }

    /** The size of a world rectangle in the frame units of [frameLevel], which is how images store their size. */
    fun localSize(rect: WorldRect, frameLevel: Int): LocalSize {
        val perWorld = 1.0 / FrameLevels.worldUnitsPerLocalUnit(frameLevel)
        return LocalSize((rect.width * perWorld).toFloat(), (rect.height * perWorld).toFloat())
    }
}
