package io.github.subhaneetshrestha.afnopadd.document.internal.db

import io.github.subhaneetshrestha.afnopadd.document.CommonFields
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.document.internal.json.ElementFormatException
import io.github.subhaneetshrestha.afnopadd.document.internal.json.ElementJsonCodec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

internal fun Element.toElementEntity(codec: ElementJsonCodec, deleted: Boolean = false): ElementEntity = ElementEntity(
    id = id.value,
    kind = codec.kindOf(this),
    z = z.key,
    anchorX = anchor.x,
    anchorY = anchor.y,
    frameLevel = frameLevel,
    boundsLeft = bounds.left,
    boundsTop = bounds.top,
    boundsRight = bounds.right,
    boundsBottom = bounds.bottom,
    version = version,
    versionNonce = versionNonce,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = deleted,
    payloadJson = codec.encodePayload(this).toString(),
)

internal fun StrokeElement.toStrokePointsEntity(): StrokePointsEntity = StrokePointsEntity(
    elementId = id.value,
    formatVersion = StrokePoints.FORMAT_VERSION.toInt(),
    blob = points.encode(),
)

/** Rebuilds the element from its columns and payload; throws [ElementFormatException] for rows this app cannot read. */
internal fun ElementEntity.toElement(codec: ElementJsonCodec, pointsFor: (ElementId) -> StrokePoints): Element {
    val payload = try {
        Json.parseToJsonElement(payloadJson).jsonObject
    } catch (e: SerializationException) {
        throw ElementFormatException("Element $id has malformed JSON", e)
    } catch (e: IllegalArgumentException) {
        throw ElementFormatException("Element $id is not a JSON object", e)
    }
    val common = try {
        CommonFields(
            id = ElementId(id),
            anchor = WorldPoint(anchorX, anchorY),
            frameLevel = frameLevel,
            z = FractionalIndex(z),
            bounds = WorldRect(boundsLeft, boundsTop, boundsRight, boundsBottom),
            version = version,
            versionNonce = versionNonce,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    } catch (e: IllegalArgumentException) {
        throw ElementFormatException("Element $id has an invalid id", e)
    }
    return codec.decodePayload(common, kind, payload, pointsFor)
}
