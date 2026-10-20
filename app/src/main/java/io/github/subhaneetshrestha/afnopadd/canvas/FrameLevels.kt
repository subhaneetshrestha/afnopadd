package io.github.subhaneetshrestha.afnopadd.canvas

import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Zoom frames: an element drawn at zoom `z` stores its geometry in a frame whose unit is
 * `10^-level` world units, with `level = floor(log10(z))`. Inside that frame one unit is between
 * one and ten screen pixels at drawing time, which keeps Ink's quantisation and float32 geometry in
 * their comfortable range no matter how deep the user has zoomed.
 */
object FrameLevels {

    fun levelFor(zoom: Double): Int = floor(log10(zoom) + LOG_EPSILON).toInt()

    /** How many world units one local unit of [level] spans. */
    fun worldUnitsPerLocalUnit(level: Int): Double = 10.0.pow(-level)

    fun localToWorld(local: LocalPoint, anchor: WorldPoint, level: Int): WorldPoint {
        val unit = worldUnitsPerLocalUnit(level)
        return WorldPoint(anchor.x + local.x * unit, anchor.y + local.y * unit)
    }

    fun worldToLocal(world: WorldPoint, anchor: WorldPoint, level: Int): LocalPoint {
        val localUnitsPerWorldUnit = 10.0.pow(level)
        return LocalPoint(
            x = ((world.x - anchor.x) * localUnitsPerWorldUnit).toFloat(),
            y = ((world.y - anchor.y) * localUnitsPerWorldUnit).toFloat(),
        )
    }

    /** Guards against `log10` landing a hair below an exact power of ten. */
    private const val LOG_EPSILON = 1e-12
}
