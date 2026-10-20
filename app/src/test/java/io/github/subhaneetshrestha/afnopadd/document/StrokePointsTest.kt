package io.github.subhaneetshrestha.afnopadd.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

class StrokePointsTest {

    private fun randomPoints(count: Int, seed: Int): StrokePoints {
        val random = Random(seed)
        return StrokePoints.of(
            x = FloatArray(count) { random.nextFloat() * 2_000f - 1_000f },
            y = FloatArray(count) { random.nextFloat() * 2_000f - 1_000f },
            pressure = FloatArray(count) { random.nextFloat() },
            tiltRad = FloatArray(count) { random.nextFloat() * 1.5f },
            orientationRad = FloatArray(count) { random.nextFloat() * 6.28f - 3.14f },
            timeMillis = FloatArray(count) { it * 8f },
        )
    }

    @Test
    fun `encoding and decoding keeps every float bit-exact`() {
        val original = randomPoints(count = 1_000, seed = 9)

        val restored = StrokePoints.decode(original.encode())

        assertEquals(1_000, restored.count)
        for (i in 0 until 1_000) {
            assertEquals(original.x(i).toRawBits(), restored.x(i).toRawBits())
            assertEquals(original.y(i).toRawBits(), restored.y(i).toRawBits())
            assertEquals(original.pressure(i).toRawBits(), restored.pressure(i).toRawBits())
            assertEquals(original.tiltRad(i).toRawBits(), restored.tiltRad(i).toRawBits())
            assertEquals(original.orientationRad(i).toRawBits(), restored.orientationRad(i).toRawBits())
            assertEquals(original.timeMillis(i).toRawBits(), restored.timeMillis(i).toRawBits())
        }
        assertEquals(original, restored)
    }

    @Test
    fun `empty strokes round trip`() {
        val restored = StrokePoints.decode(StrokePoints.EMPTY.encode())

        assertEquals(0, restored.count)
        assertEquals(StrokePoints.EMPTY, restored)
    }

    @Test
    fun `encoded size is a one byte version, a four byte count and six floats per point`() {
        val points = randomPoints(count = 10, seed = 1)

        assertEquals(1 + 4 + 10 * 6 * 4, points.encode().size)
    }

    @Test
    fun `decoding rejects an unknown format version`() {
        val bytes = randomPoints(count = 3, seed = 2).encode()
        bytes[0] = 99

        assertThrows(IllegalArgumentException::class.java) { StrokePoints.decode(bytes) }
    }

    @Test
    fun `decoding rejects truncated data`() {
        val bytes = randomPoints(count = 3, seed = 2).encode()

        assertThrows(IllegalArgumentException::class.java) { StrokePoints.decode(bytes.copyOf(bytes.size - 3)) }
    }

    @Test
    fun `columns must have equal length`() {
        assertThrows(IllegalArgumentException::class.java) {
            StrokePoints.of(
                x = FloatArray(2),
                y = FloatArray(3),
                pressure = FloatArray(2),
                tiltRad = FloatArray(2),
                orientationRad = FloatArray(2),
                timeMillis = FloatArray(2),
            )
        }
    }

    @Test
    fun `equality is by content`() {
        assertEquals(randomPoints(count = 50, seed = 5), randomPoints(count = 50, seed = 5))
        assertNotEquals(randomPoints(count = 50, seed = 5), randomPoints(count = 50, seed = 6))
        assertEquals(randomPoints(count = 50, seed = 5).hashCode(), randomPoints(count = 50, seed = 5).hashCode())
    }
}
