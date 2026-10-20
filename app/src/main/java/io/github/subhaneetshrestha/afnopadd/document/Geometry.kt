package io.github.subhaneetshrestha.afnopadd.document

import kotlinx.serialization.Serializable

/** World-space point in sheet units (1 unit = 1 dp at zoom 1.0). Double because sheets are deep. */
@Serializable
data class WorldPoint(val x: Double, val y: Double)

/** Axis-aligned world-space rectangle; `right >= left` and `bottom >= top` by convention. */
@Serializable
data class WorldRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
}

/** Point in an element's own frame: float units relative to the element anchor, scaled by its frame level. */
@Serializable
data class LocalPoint(val x: Float, val y: Float)

@Serializable
data class LocalSize(val width: Float, val height: Float)

@Serializable
data class LocalRect(val left: Float, val top: Float, val right: Float, val bottom: Float)
