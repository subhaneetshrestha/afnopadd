package io.github.subhaneetshrestha.afnopadd.document

import io.github.subhaneetshrestha.afnopadd.document.internal.json.ElementFormatException
import io.github.subhaneetshrestha.afnopadd.document.internal.json.ElementJsonCodec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementJsonCodecTest {

    private val codec = ElementJsonCodec()

    private val strokeId = ElementId("01JAX9Q8K0TZ3M4N5P6R7S8T9V")
    private val shapeId = ElementId("01JAX9Q8K0TZ3M4N5P6R7S8TA0")
    private val imageId = ElementId("01JAX9Q8K0TZ3M4N5P6R7S8TA1")

    private val points = StrokePoints.of(
        x = floatArrayOf(0f, 1f, 2f),
        y = floatArrayOf(0f, 1f, 0.5f),
        pressure = floatArrayOf(0.2f, 0.8f, 0.5f),
        tiltRad = floatArrayOf(0f, 0f, 0f),
        orientationRad = floatArrayOf(0f, 0f, 0f),
        timeMillis = floatArrayOf(0f, 8f, 16f),
    )

    private fun stroke() = StrokeElement(
        id = strokeId,
        anchor = WorldPoint(10.5, -3.0),
        frameLevel = 1,
        z = FractionalIndex("a0"),
        bounds = WorldRect(10.5, -3.0, 12.5, -2.0),
        version = 1,
        versionNonce = 42,
        createdAt = 1_000L,
        updatedAt = 2_000L,
        brush = BrushSpec(familyId = "ink:pressure-pen", size = 4f, colorArgb = 0xFF112233, opacity = 1f, epsilon = 0.025f),
        points = points,
    )

    private fun shape() = ShapeElement(
        id = shapeId,
        anchor = WorldPoint(0.0, 0.0),
        frameLevel = 0,
        z = FractionalIndex("a1"),
        bounds = WorldRect(0.0, 0.0, 100.0, 50.0),
        version = 3,
        versionNonce = 7,
        createdAt = 1_000L,
        updatedAt = 3_000L,
        shape = ShapeKind.ARROW,
        geometry = ShapeGeometry(start = LocalPoint(0f, 0f), end = LocalPoint(100f, 50f)),
        style = ShapeStyle(strokeWidth = 2f, colorArgb = 0xFF000000, opacity = 0.9f),
    )

    private fun image() = ImageElement(
        id = imageId,
        anchor = WorldPoint(-500.0, 250.0),
        frameLevel = -1,
        z = FractionalIndex("a2"),
        bounds = WorldRect(-500.0, 250.0, -100.0, 550.0),
        version = 1,
        versionNonce = 1,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        asset = AssetId("a".repeat(64)),
        size = LocalSize(width = 400f, height = 300f),
        rotationDeg = 15f,
        crop = null,
    )

    @Test
    fun `stroke element round trips without its points`() {
        val json = codec.encode(stroke())

        assertEquals("stroke", json["kind"]?.jsonPrimitive?.content)
        assertNull(json["points"])
        assertEquals(stroke(), codec.decode(json) { points })
    }

    @Test
    fun `shape element round trips`() {
        val json = codec.encode(shape())

        assertEquals("shape", json["kind"]?.jsonPrimitive?.content)
        assertEquals(shape(), codec.decode(json) { StrokePoints.EMPTY })
    }

    @Test
    fun `image element round trips including a null crop`() {
        val json = codec.encode(image())

        assertEquals("image", json["kind"]?.jsonPrimitive?.content)
        assertEquals(image(), codec.decode(json) { StrokePoints.EMPTY })
    }

    @Test
    fun `unknown fields on a known kind are kept through a round trip`() {
        val future = buildJsonObject { put("x", 1) }
        val json = JsonObject(codec.encode(stroke()) + ("future" to future))

        val decoded = codec.decode(json) { points }

        assertEquals(future, decoded.extras["future"])
        assertEquals(json, codec.encode(decoded))
    }

    @Test
    fun `unknown kinds are preserved verbatim`() {
        val json = JsonObject(
            codec.encode(shape()) + mapOf(
                "kind" to kotlinx.serialization.json.JsonPrimitive("hologram"),
                "beam" to kotlinx.serialization.json.JsonPrimitive(3),
            ),
        )

        val decoded = codec.decode(json) { StrokePoints.EMPTY }

        assertTrue(decoded is UnknownElement)
        assertEquals("hologram", (decoded as UnknownElement).kind)
        assertEquals(shapeId, decoded.id)
        assertEquals(json, codec.encode(decoded))
    }

    @Test
    fun `missing common fields are a format error, not a crash`() {
        val json = buildJsonObject { put("kind", "stroke") }

        assertThrows(ElementFormatException::class.java) { codec.decode(json) { StrokePoints.EMPTY } }
    }

    @Test
    fun `payload encoding carries only kind-specific fields and extras`() {
        val future = buildJsonObject { put("x", 1) }
        val element = stroke().copy(extras = future.let { JsonObject(mapOf("future" to it)) })

        val payload = codec.encodePayload(element)

        assertEquals(setOf("brush", "future"), payload.keys)
    }

    @Test
    fun `each kind round trips through its payload and common fields`() {
        for (element in listOf(stroke(), shape(), image())) {
            val payload = codec.encodePayload(element)
            val common = CommonFields.of(element)

            val decoded = codec.decodePayload(common, codec.kindOf(element), payload) { points }

            assertEquals(element, decoded)
        }
    }

    @Test
    fun `payload decoding keeps unknown fields and unknown kinds`() {
        val payload = JsonObject(codec.encodePayload(shape()) + ("beam" to kotlinx.serialization.json.JsonPrimitive(3)))
        val common = CommonFields.of(shape())

        val decoded = codec.decodePayload(common, "hologram", payload) { StrokePoints.EMPTY }

        assertTrue(decoded is UnknownElement)
        assertEquals(shapeId, decoded.id)
        assertEquals(payload, decoded.extras)
        assertEquals(payload, codec.encodePayload(decoded))
    }

    @Test
    fun `a stroke points supplier is only consulted for strokes`() {
        var consulted = 0

        codec.decode(codec.encode(shape())) { consulted++; StrokePoints.EMPTY }
        codec.decode(codec.encode(stroke())) { consulted++; points }

        assertEquals(1, consulted)
    }
}
