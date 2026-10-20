package io.github.subhaneetshrestha.afnopadd.document.internal.json

import io.github.subhaneetshrestha.afnopadd.document.AssetId
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.LocalRect
import io.github.subhaneetshrestha.afnopadd.document.LocalSize
import io.github.subhaneetshrestha.afnopadd.document.NoteElement
import io.github.subhaneetshrestha.afnopadd.document.ShapeElement
import io.github.subhaneetshrestha.afnopadd.document.ShapeGeometry
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import io.github.subhaneetshrestha.afnopadd.document.ShapeStyle
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import io.github.subhaneetshrestha.afnopadd.document.UnknownElement
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** A sheet file or database row could not be understood; expected for corrupt or newer files, never a crash. */
class ElementFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Converts elements to and from their JSON envelope. Stroke points are deliberately not part of
 * the envelope: they live in their own binary blob keyed by element id, so decoding takes a
 * supplier for them. Unknown fields are preserved in `extras`; unknown kinds become [UnknownElement].
 */
class ElementJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    fun encode(element: Element): JsonObject {
        val common = mapOf(
            KEY_KIND to JsonPrimitive(kindOf(element)),
            KEY_ID to JsonPrimitive(element.id.value),
            KEY_ANCHOR to json.encodeToJsonElement(WorldPoint.serializer(), element.anchor),
            KEY_FRAME_LEVEL to JsonPrimitive(element.frameLevel),
            KEY_Z to JsonPrimitive(element.z.key),
            KEY_BOUNDS to json.encodeToJsonElement(WorldRect.serializer(), element.bounds),
            KEY_VERSION to JsonPrimitive(element.version),
            KEY_VERSION_NONCE to JsonPrimitive(element.versionNonce),
            KEY_CREATED_AT to JsonPrimitive(element.createdAt),
            KEY_UPDATED_AT to JsonPrimitive(element.updatedAt),
        )
        val specific: Map<String, JsonElement> = when (element) {
            is StrokeElement -> mapOf(KEY_BRUSH to json.encodeToJsonElement(BrushSpec.serializer(), element.brush))
            is ShapeElement -> mapOf(
                KEY_SHAPE to json.encodeToJsonElement(ShapeKind.serializer(), element.shape),
                KEY_GEOMETRY to json.encodeToJsonElement(ShapeGeometry.serializer(), element.geometry),
                KEY_STYLE to json.encodeToJsonElement(ShapeStyle.serializer(), element.style),
            )
            is ImageElement -> mapOf(
                KEY_ASSET to JsonPrimitive(element.asset.sha256Hex),
                KEY_SIZE to json.encodeToJsonElement(LocalSize.serializer(), element.size),
                KEY_ROTATION_DEG to JsonPrimitive(element.rotationDeg),
                KEY_CROP to (element.crop?.let { json.encodeToJsonElement(LocalRect.serializer(), it) } ?: JsonNull),
            )
            is NoteElement -> mapOf(KEY_TEXT to JsonPrimitive(element.text))
            is UnknownElement -> emptyMap()
        }
        val known = common.keys + specific.keys
        return JsonObject(element.extras.filterKeys { it !in known } + common + specific)
    }

    fun decode(obj: JsonObject, pointsFor: (ElementId) -> StrokePoints): Element = try {
        val kind = obj.string(KEY_KIND)
        val id = ElementId(obj.string(KEY_ID))
        val anchor = json.decodeFromJsonElement(WorldPoint.serializer(), obj.required(KEY_ANCHOR))
        val frameLevel = obj.required(KEY_FRAME_LEVEL).jsonPrimitive.int
        val z = FractionalIndex(obj.string(KEY_Z))
        val bounds = json.decodeFromJsonElement(WorldRect.serializer(), obj.required(KEY_BOUNDS))
        val version = obj.required(KEY_VERSION).jsonPrimitive.int
        val versionNonce = obj.required(KEY_VERSION_NONCE).jsonPrimitive.int
        val createdAt = obj.required(KEY_CREATED_AT).jsonPrimitive.long
        val updatedAt = obj.required(KEY_UPDATED_AT).jsonPrimitive.long
        val extras = JsonObject(obj.filterKeys { it !in COMMON_KEYS && it !in kindKeys(kind) })

        when (kind) {
            KIND_STROKE -> StrokeElement(
                id, anchor, frameLevel, z, bounds, version, versionNonce, createdAt, updatedAt,
                brush = json.decodeFromJsonElement(BrushSpec.serializer(), obj.required(KEY_BRUSH)),
                points = pointsFor(id),
                extras = extras,
            )
            KIND_SHAPE -> ShapeElement(
                id, anchor, frameLevel, z, bounds, version, versionNonce, createdAt, updatedAt,
                shape = json.decodeFromJsonElement(ShapeKind.serializer(), obj.required(KEY_SHAPE)),
                geometry = json.decodeFromJsonElement(ShapeGeometry.serializer(), obj.required(KEY_GEOMETRY)),
                style = json.decodeFromJsonElement(ShapeStyle.serializer(), obj.required(KEY_STYLE)),
                extras = extras,
            )
            KIND_IMAGE -> ImageElement(
                id, anchor, frameLevel, z, bounds, version, versionNonce, createdAt, updatedAt,
                asset = AssetId(obj.string(KEY_ASSET)),
                size = json.decodeFromJsonElement(LocalSize.serializer(), obj.required(KEY_SIZE)),
                rotationDeg = obj.required(KEY_ROTATION_DEG).jsonPrimitive.float,
                crop = obj[KEY_CROP]?.takeUnless { it is JsonNull }
                    ?.let { json.decodeFromJsonElement(LocalRect.serializer(), it) },
                extras = extras,
            )
            KIND_NOTE -> NoteElement(
                id, anchor, frameLevel, z, bounds, version, versionNonce, createdAt, updatedAt,
                text = obj.string(KEY_TEXT),
                extras = extras,
            )
            else -> UnknownElement(
                id, anchor, frameLevel, z, bounds, version, versionNonce, createdAt, updatedAt,
                kind = kind,
                extras = extras,
            )
        }
    } catch (e: IllegalArgumentException) {
        throw ElementFormatException("Invalid element: ${e.message}", e)
    } catch (e: SerializationException) {
        throw ElementFormatException("Invalid element: ${e.message}", e)
    }

    private fun JsonObject.required(key: String): JsonElement =
        this[key] ?: throw ElementFormatException("Element is missing '$key'")

    private fun JsonObject.string(key: String): String {
        val element = required(key)
        if (element !is JsonPrimitive || !element.isString) throw ElementFormatException("Element field '$key' must be a string")
        return element.content
    }

    private fun kindOf(element: Element): String = when (element) {
        is StrokeElement -> KIND_STROKE
        is ShapeElement -> KIND_SHAPE
        is ImageElement -> KIND_IMAGE
        is NoteElement -> KIND_NOTE
        is UnknownElement -> element.kind
    }

    private fun kindKeys(kind: String): Set<String> = when (kind) {
        KIND_STROKE -> STROKE_KEYS
        KIND_SHAPE -> SHAPE_KEYS
        KIND_IMAGE -> IMAGE_KEYS
        KIND_NOTE -> NOTE_KEYS
        else -> emptySet()
    }

    private companion object {
        const val KIND_STROKE = "stroke"
        const val KIND_SHAPE = "shape"
        const val KIND_IMAGE = "image"
        const val KIND_NOTE = "note"

        const val KEY_KIND = "kind"
        const val KEY_ID = "id"
        const val KEY_ANCHOR = "anchor"
        const val KEY_FRAME_LEVEL = "frameLevel"
        const val KEY_Z = "z"
        const val KEY_BOUNDS = "bounds"
        const val KEY_VERSION = "version"
        const val KEY_VERSION_NONCE = "versionNonce"
        const val KEY_CREATED_AT = "createdAt"
        const val KEY_UPDATED_AT = "updatedAt"
        const val KEY_BRUSH = "brush"
        const val KEY_SHAPE = "shape"
        const val KEY_GEOMETRY = "geometry"
        const val KEY_STYLE = "style"
        const val KEY_ASSET = "asset"
        const val KEY_SIZE = "size"
        const val KEY_ROTATION_DEG = "rotationDeg"
        const val KEY_CROP = "crop"
        const val KEY_TEXT = "text"

        val COMMON_KEYS = setOf(
            KEY_KIND, KEY_ID, KEY_ANCHOR, KEY_FRAME_LEVEL, KEY_Z, KEY_BOUNDS,
            KEY_VERSION, KEY_VERSION_NONCE, KEY_CREATED_AT, KEY_UPDATED_AT,
        )
        val STROKE_KEYS = setOf(KEY_BRUSH)
        val SHAPE_KEYS = setOf(KEY_SHAPE, KEY_GEOMETRY, KEY_STYLE)
        val IMAGE_KEYS = setOf(KEY_ASSET, KEY_SIZE, KEY_ROTATION_DEG, KEY_CROP)
        val NOTE_KEYS = setOf(KEY_TEXT)
    }
}
