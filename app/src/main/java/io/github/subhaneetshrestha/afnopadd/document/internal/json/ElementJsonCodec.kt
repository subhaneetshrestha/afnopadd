package io.github.subhaneetshrestha.afnopadd.document.internal.json

import io.github.subhaneetshrestha.afnopadd.document.AssetId
import io.github.subhaneetshrestha.afnopadd.document.BrushSpec
import io.github.subhaneetshrestha.afnopadd.document.CommonFields
import io.github.subhaneetshrestha.afnopadd.document.EMPTY_EXTRAS
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.LocalPoint
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A sheet file or database row could not be understood; expected for corrupt or newer files, never a crash. */
class ElementFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Converts elements to and from JSON in two shapes that share one decode path:
 * - the full **envelope** (`encode`/`decode`): common fields plus kind-specific fields, used by the
 *   sheet package format;
 * - the lean **payload** (`encodePayload`/`decodePayload`): kind-specific fields only, stored in the
 *   database next to the common fields as columns, so opening a sheet parses a quarter of the text.
 *
 * Stroke points are never part of the JSON: they live in their own binary blob keyed by element id,
 * so decoding takes a supplier for them. Unknown fields are preserved in `extras`; unknown kinds
 * become [UnknownElement]. Decoding reads primitives straight out of the parsed tree instead of
 * running a serializer per sub-object because this path bounds how fast a sheet opens.
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
        val specific = specificFields(element)
        val known = common.keys + specific.keys
        return JsonObject(element.extras.filterKeys { it !in known } + common + specific)
    }

    fun encodePayload(element: Element): JsonObject {
        val specific = specificFields(element)
        return JsonObject(element.extras.filterKeys { it !in specific.keys } + specific)
    }

    fun decode(obj: JsonObject, pointsFor: (ElementId) -> StrokePoints): Element = guarded {
        val kind = obj.string(KEY_KIND)
        val common = CommonFields(
            id = ElementId(obj.string(KEY_ID)),
            anchor = obj.obj(KEY_ANCHOR).let { WorldPoint(it.double("x"), it.double("y")) },
            frameLevel = obj.int(KEY_FRAME_LEVEL),
            z = FractionalIndex(obj.string(KEY_Z)),
            bounds = obj.obj(KEY_BOUNDS).let {
                WorldRect(it.double("left"), it.double("top"), it.double("right"), it.double("bottom"))
            },
            version = obj.int(KEY_VERSION),
            versionNonce = obj.int(KEY_VERSION_NONCE),
            createdAt = obj.long(KEY_CREATED_AT),
            updatedAt = obj.long(KEY_UPDATED_AT),
        )
        decodeKind(common, kind, obj, extrasOf(obj, COMMON_KEYS + kindKeysFor(kind)), pointsFor)
    }

    fun decodePayload(
        common: CommonFields,
        kind: String,
        payload: JsonObject,
        pointsFor: (ElementId) -> StrokePoints,
    ): Element = guarded {
        decodeKind(common, kind, payload, extrasOf(payload, kindKeysFor(kind)), pointsFor)
    }

    /** The `kind` discriminator written for [element]; also stored as a plain column for queries. */
    fun kindOf(element: Element): String = when (element) {
        is StrokeElement -> KIND_STROKE
        is ShapeElement -> KIND_SHAPE
        is ImageElement -> KIND_IMAGE
        is NoteElement -> KIND_NOTE
        is UnknownElement -> element.kind
    }

    private fun specificFields(element: Element): Map<String, JsonElement> = when (element) {
        is StrokeElement -> mapOf(KEY_BRUSH to json.encodeToJsonElement(BrushSpec.serializer(), element.brush))
        is ShapeElement -> buildMap {
            put(KEY_SHAPE, json.encodeToJsonElement(ShapeKind.serializer(), element.shape))
            put(KEY_GEOMETRY, json.encodeToJsonElement(ShapeGeometry.serializer(), element.geometry))
            put(KEY_STYLE, json.encodeToJsonElement(ShapeStyle.serializer(), element.style))
            // Absent when zero, so unrotated shapes are written exactly as before rotation existed.
            if (element.rotationDeg != 0f) put(KEY_ROTATION_DEG, JsonPrimitive(element.rotationDeg))
        }
        is ImageElement -> mapOf(
            KEY_ASSET to JsonPrimitive(element.asset.sha256Hex),
            KEY_SIZE to json.encodeToJsonElement(LocalSize.serializer(), element.size),
            KEY_ROTATION_DEG to JsonPrimitive(element.rotationDeg),
            KEY_CROP to (element.crop?.let { json.encodeToJsonElement(LocalRect.serializer(), it) } ?: JsonNull),
        )
        is NoteElement -> mapOf(KEY_TEXT to JsonPrimitive(element.text))
        is UnknownElement -> emptyMap()
    }

    private fun decodeKind(
        c: CommonFields,
        kind: String,
        source: JsonObject,
        extras: JsonObject,
        pointsFor: (ElementId) -> StrokePoints,
    ): Element = when (kind) {
        KIND_STROKE -> StrokeElement(
            c.id, c.anchor, c.frameLevel, c.z, c.bounds, c.version, c.versionNonce, c.createdAt, c.updatedAt,
            brush = source.obj(KEY_BRUSH).let {
                BrushSpec(
                    familyId = it.string("familyId"),
                    size = it.float("size"),
                    colorArgb = it.long("colorArgb"),
                    opacity = it.float("opacity"),
                    epsilon = it.float("epsilon"),
                )
            },
            points = pointsFor(c.id),
            extras = extras,
        )
        KIND_SHAPE -> ShapeElement(
            c.id, c.anchor, c.frameLevel, c.z, c.bounds, c.version, c.versionNonce, c.createdAt, c.updatedAt,
            shape = ShapeKind.valueOf(source.string(KEY_SHAPE)),
            geometry = source.obj(KEY_GEOMETRY).let { ShapeGeometry(start = it.localPoint("start"), end = it.localPoint("end")) },
            style = source.obj(KEY_STYLE).let {
                ShapeStyle(
                    strokeWidth = it.float("strokeWidth"),
                    colorArgb = it.long("colorArgb"),
                    opacity = it.floatOr("opacity", 1f),
                )
            },
            rotationDeg = source.floatOr(KEY_ROTATION_DEG, 0f),
            extras = extras,
        )
        KIND_IMAGE -> ImageElement(
            c.id, c.anchor, c.frameLevel, c.z, c.bounds, c.version, c.versionNonce, c.createdAt, c.updatedAt,
            asset = AssetId(source.string(KEY_ASSET)),
            size = source.obj(KEY_SIZE).let { LocalSize(width = it.float("width"), height = it.float("height")) },
            rotationDeg = source.float(KEY_ROTATION_DEG),
            crop = source[KEY_CROP]?.takeUnless { it is JsonNull }?.let { crop ->
                (crop as? JsonObject ?: throw ElementFormatException("Element field '$KEY_CROP' must be an object"))
                    .let { LocalRect(it.float("left"), it.float("top"), it.float("right"), it.float("bottom")) }
            },
            extras = extras,
        )
        KIND_NOTE -> NoteElement(
            c.id, c.anchor, c.frameLevel, c.z, c.bounds, c.version, c.versionNonce, c.createdAt, c.updatedAt,
            text = source.string(KEY_TEXT),
            extras = extras,
        )
        else -> UnknownElement(
            c.id, c.anchor, c.frameLevel, c.z, c.bounds, c.version, c.versionNonce, c.createdAt, c.updatedAt,
            kind = kind,
            extras = extras,
        )
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: IllegalArgumentException) {
        throw ElementFormatException("Invalid element: ${e.message}", e)
    }

    private fun extrasOf(obj: JsonObject, known: Set<String>): JsonObject =
        if (obj.keys.all { it in known }) EMPTY_EXTRAS else JsonObject(obj.filterKeys { it !in known })

    private fun kindKeysFor(kind: String): Set<String> = when (kind) {
        KIND_STROKE -> STROKE_KEYS
        KIND_SHAPE -> SHAPE_KEYS
        KIND_IMAGE -> IMAGE_KEYS
        KIND_NOTE -> NOTE_KEYS
        else -> emptySet()
    }

    private fun JsonObject.required(key: String): JsonElement =
        this[key] ?: throw ElementFormatException("Element is missing '$key'")

    private fun JsonObject.obj(key: String): JsonObject =
        required(key) as? JsonObject ?: throw ElementFormatException("Element field '$key' must be an object")

    private fun JsonObject.primitive(key: String): JsonPrimitive =
        required(key) as? JsonPrimitive ?: throw ElementFormatException("Element field '$key' must be a value")

    private fun JsonObject.string(key: String): String {
        val primitive = primitive(key)
        if (!primitive.isString) throw ElementFormatException("Element field '$key' must be a string")
        return primitive.content
    }

    private fun JsonObject.int(key: String): Int =
        primitive(key).content.toIntOrNull() ?: throw ElementFormatException("Element field '$key' must be an integer")

    private fun JsonObject.long(key: String): Long =
        primitive(key).content.toLongOrNull() ?: throw ElementFormatException("Element field '$key' must be an integer")

    private fun JsonObject.double(key: String): Double =
        primitive(key).content.toDoubleOrNull() ?: throw ElementFormatException("Element field '$key' must be a number")

    private fun JsonObject.float(key: String): Float =
        primitive(key).content.toFloatOrNull() ?: throw ElementFormatException("Element field '$key' must be a number")

    private fun JsonObject.floatOr(key: String, default: Float): Float = if (key in this) float(key) else default

    private fun JsonObject.localPoint(key: String): LocalPoint = obj(key).let { LocalPoint(it.float("x"), it.float("y")) }

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
        val SHAPE_KEYS = setOf(KEY_SHAPE, KEY_GEOMETRY, KEY_STYLE, KEY_ROTATION_DEG)
        val IMAGE_KEYS = setOf(KEY_ASSET, KEY_SIZE, KEY_ROTATION_DEG, KEY_CROP)
        val NOTE_KEYS = setOf(KEY_TEXT)
    }
}
