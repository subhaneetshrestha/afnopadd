package io.github.subhaneetshrestha.afnopadd.document

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Stable string naming an Ink brush family (`ink:pressure-pen`, `ink:marker`, ...); mapped by `canvas-engine`. */
@Serializable
data class BrushSpec(
    val familyId: String,
    /** Brush size in frame units. */
    val size: Float,
    val colorArgb: Long,
    val opacity: Float,
    /** Ink geometry quantisation in frame units; fixed for the life of the stroke. */
    val epsilon: Float,
)

@Serializable
enum class ShapeKind { LINE, RECTANGLE, ELLIPSE, ARROW }

/** Two points in frame units: endpoints for lines and arrows, opposite corners for rectangles and ellipses. */
@Serializable
data class ShapeGeometry(val start: LocalPoint, val end: LocalPoint)

@Serializable
data class ShapeStyle(val strokeWidth: Float, val colorArgb: Long, val opacity: Float = 1f)

/**
 * One thing on a sheet. Every element has a double-precision anchor and a frame level; its own
 * geometry is in float frame units relative to that anchor (`world = anchor + local / 10^frameLevel`).
 * `version` and `versionNonce` give last-writer-wins tie-breaking if two forks are ever reconciled.
 * `extras` carries fields this version of the app does not understand, so a newer file survives a
 * round trip through an older app.
 */
sealed interface Element {
    val id: ElementId
    val anchor: WorldPoint
    val frameLevel: Int
    val z: FractionalIndex
    val bounds: WorldRect
    val version: Int
    val versionNonce: Int
    val createdAt: Long
    val updatedAt: Long
    val extras: JsonObject
}

data class StrokeElement(
    override val id: ElementId,
    override val anchor: WorldPoint,
    override val frameLevel: Int,
    override val z: FractionalIndex,
    override val bounds: WorldRect,
    override val version: Int,
    override val versionNonce: Int,
    override val createdAt: Long,
    override val updatedAt: Long,
    val brush: BrushSpec,
    val points: StrokePoints,
    override val extras: JsonObject = EMPTY_EXTRAS,
) : Element

data class ShapeElement(
    override val id: ElementId,
    override val anchor: WorldPoint,
    override val frameLevel: Int,
    override val z: FractionalIndex,
    override val bounds: WorldRect,
    override val version: Int,
    override val versionNonce: Int,
    override val createdAt: Long,
    override val updatedAt: Long,
    val shape: ShapeKind,
    val geometry: ShapeGeometry,
    val style: ShapeStyle,
    override val extras: JsonObject = EMPTY_EXTRAS,
) : Element

data class ImageElement(
    override val id: ElementId,
    override val anchor: WorldPoint,
    override val frameLevel: Int,
    override val z: FractionalIndex,
    override val bounds: WorldRect,
    override val version: Int,
    override val versionNonce: Int,
    override val createdAt: Long,
    override val updatedAt: Long,
    val asset: AssetId,
    val size: LocalSize,
    val rotationDeg: Float,
    val crop: LocalRect?,
    override val extras: JsonObject = EMPTY_EXTRAS,
) : Element

/** Reserved for a later text-note feature; v1 never creates one but must round-trip it. */
data class NoteElement(
    override val id: ElementId,
    override val anchor: WorldPoint,
    override val frameLevel: Int,
    override val z: FractionalIndex,
    override val bounds: WorldRect,
    override val version: Int,
    override val versionNonce: Int,
    override val createdAt: Long,
    override val updatedAt: Long,
    val text: String,
    override val extras: JsonObject = EMPTY_EXTRAS,
) : Element

/** An element kind this version of the app does not know; kept verbatim so it is written back unchanged. */
data class UnknownElement(
    override val id: ElementId,
    override val anchor: WorldPoint,
    override val frameLevel: Int,
    override val z: FractionalIndex,
    override val bounds: WorldRect,
    override val version: Int,
    override val versionNonce: Int,
    override val createdAt: Long,
    override val updatedAt: Long,
    val kind: String,
    override val extras: JsonObject,
) : Element

private val EMPTY_EXTRAS = JsonObject(emptyMap())
