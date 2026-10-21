package io.github.subhaneetshrestha.afnopadd.document

/**
 * A copy of this element with its shared fields replaced, whatever its kind, so edits that only
 * touch placement, order or identity (move, reorder, duplicate) work on every element alike,
 * including kinds this version does not know.
 */
fun Element.withCommon(
    id: ElementId = this.id,
    anchor: WorldPoint = this.anchor,
    z: FractionalIndex = this.z,
    bounds: WorldRect = this.bounds,
    version: Int = this.version,
    versionNonce: Int = this.versionNonce,
    createdAt: Long = this.createdAt,
    updatedAt: Long = this.updatedAt,
): Element = when (this) {
    is StrokeElement -> copy(id = id, anchor = anchor, z = z, bounds = bounds, version = version, versionNonce = versionNonce, createdAt = createdAt, updatedAt = updatedAt)
    is ShapeElement -> copy(id = id, anchor = anchor, z = z, bounds = bounds, version = version, versionNonce = versionNonce, createdAt = createdAt, updatedAt = updatedAt)
    is ImageElement -> copy(id = id, anchor = anchor, z = z, bounds = bounds, version = version, versionNonce = versionNonce, createdAt = createdAt, updatedAt = updatedAt)
    is NoteElement -> copy(id = id, anchor = anchor, z = z, bounds = bounds, version = version, versionNonce = versionNonce, createdAt = createdAt, updatedAt = updatedAt)
    is UnknownElement -> copy(id = id, anchor = anchor, z = z, bounds = bounds, version = version, versionNonce = versionNonce, createdAt = createdAt, updatedAt = updatedAt)
}

/** This element shifted by ([dx], [dy]) world units: geometry is anchor-relative, so only the anchor and bounds move. */
fun Element.movedBy(dx: Double, dy: Double): Element = withCommon(
    anchor = WorldPoint(anchor.x + dx, anchor.y + dy),
    bounds = WorldRect(bounds.left + dx, bounds.top + dy, bounds.right + dx, bounds.bottom + dy),
)
