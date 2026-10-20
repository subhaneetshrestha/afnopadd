package io.github.subhaneetshrestha.afnopadd.document

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The input samples of one stroke in the element's frame units, stored as packed Float32 columns
 * (x, y, pressure, tilt, orientation, time). This is Afnopadd's own on-disk format, independent of
 * Jetpack Ink, so the sheet package stays readable without Ink; `canvas-engine` converts it to an
 * Ink input batch at load time.
 */
class StrokePoints private constructor(
    val count: Int,
    private val data: FloatArray,
) {
    init {
        require(data.size == count * COLUMNS) { "Expected ${count * COLUMNS} floats, got ${data.size}" }
    }

    fun x(index: Int): Float = data[index]
    fun y(index: Int): Float = data[count + index]
    fun pressure(index: Int): Float = data[2 * count + index]
    fun tiltRad(index: Int): Float = data[3 * count + index]
    fun orientationRad(index: Int): Float = data[4 * count + index]
    fun timeMillis(index: Int): Float = data[5 * count + index]

    /** Serialises to the versioned binary layout: version byte, Int32 count, then the six columns. */
    fun encode(): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + data.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(FORMAT_VERSION)
        buffer.putInt(count)
        buffer.asFloatBuffer().put(data)
        return buffer.array()
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is StrokePoints && count == other.count && data.contentEquals(other.data))

    override fun hashCode(): Int = 31 * count + data.contentHashCode()

    override fun toString(): String = "StrokePoints(count=$count)"

    companion object {
        const val FORMAT_VERSION: Byte = 1
        private const val COLUMNS = 6
        private const val HEADER_SIZE = 1 + Int.SIZE_BYTES

        val EMPTY: StrokePoints = StrokePoints(0, FloatArray(0))

        fun of(
            x: FloatArray,
            y: FloatArray,
            pressure: FloatArray,
            tiltRad: FloatArray,
            orientationRad: FloatArray,
            timeMillis: FloatArray,
        ): StrokePoints {
            val count = x.size
            require(
                y.size == count && pressure.size == count && tiltRad.size == count &&
                    orientationRad.size == count && timeMillis.size == count,
            ) { "All columns must have the same length" }
            val data = FloatArray(count * COLUMNS)
            x.copyInto(data, 0)
            y.copyInto(data, count)
            pressure.copyInto(data, 2 * count)
            tiltRad.copyInto(data, 3 * count)
            orientationRad.copyInto(data, 4 * count)
            timeMillis.copyInto(data, 5 * count)
            return StrokePoints(count, data)
        }

        fun decode(bytes: ByteArray): StrokePoints {
            require(bytes.size >= HEADER_SIZE) { "Stroke points data is truncated (${bytes.size} bytes)" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val version = buffer.get()
            require(version == FORMAT_VERSION) { "Unsupported stroke points format version $version" }
            val count = buffer.getInt()
            require(count >= 0 && bytes.size == HEADER_SIZE + count * COLUMNS * Float.SIZE_BYTES) {
                "Stroke points data is truncated or corrupt (count=$count, ${bytes.size} bytes)"
            }
            val data = FloatArray(count * COLUMNS)
            buffer.asFloatBuffer().get(data)
            return StrokePoints(count, data)
        }
    }
}
