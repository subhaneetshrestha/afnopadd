package io.github.subhaneetshrestha.afnopadd.document

import kotlinx.serialization.Serializable
import kotlin.random.Random

@JvmInline
@Serializable
value class ElementId(val value: String) {
    init {
        require(Ulid.isValid(value)) { "ElementId must be a 26-character ULID, got '$value'" }
    }
}

@JvmInline
@Serializable
value class SheetId(val value: String) {
    init {
        require(Ulid.isValid(value)) { "SheetId must be a 26-character ULID, got '$value'" }
    }
}

/** Content-addressed asset identity: the lowercase hex SHA-256 of the stored bytes. */
@JvmInline
@Serializable
value class AssetId(val sha256Hex: String) {
    init {
        require(sha256Hex.length == 64 && sha256Hex.all { it in '0'..'9' || it in 'a'..'f' }) {
            "AssetId must be 64 lowercase hex characters, got '$sha256Hex'"
        }
    }
}

/**
 * ULID generator: a 48-bit millisecond timestamp followed by 80 random bits, Crockford base32.
 * Ids created within the same millisecond increment the random part, so they stay strictly
 * increasing, which keeps element and sheet ids sortable by creation time.
 */
class Ulid(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    private var lastTimestamp = -1L
    private var randomHigh = 0L // upper 16 of the 80 random bits
    private var randomLow = 0L // lower 64 of the 80 random bits

    @Synchronized
    fun next(): String {
        val now = clock()
        if (now == lastTimestamp) {
            randomLow++
            if (randomLow == 0L) randomHigh = (randomHigh + 1) and RANDOM_HIGH_MASK
        } else {
            lastTimestamp = now
            randomHigh = random.nextLong() and RANDOM_HIGH_MASK
            randomLow = random.nextLong()
        }
        return encode(now, randomHigh, randomLow)
    }

    companion object {
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val LENGTH = 26
        private const val TIME_CHARS = 10
        private const val RANDOM_HIGH_MASK = 0xFFFFL
        private val decodeTable = IntArray(128) { -1 }.also { table ->
            ALPHABET.forEachIndexed { index, char -> table[char.code] = index }
        }

        /** Shared generator for production code; tests inject their own clock and randomness. */
        val Default = Ulid()

        fun isValid(value: String): Boolean =
            value.length == LENGTH &&
                value[0] <= '7' &&
                value.all { it.code < decodeTable.size && decodeTable[it.code] >= 0 }

        fun timestampOf(value: String): Long {
            require(isValid(value)) { "'$value' is not a ULID" }
            var timestamp = 0L
            for (i in 0 until TIME_CHARS) {
                timestamp = (timestamp shl 5) or decodeTable[value[i].code].toLong()
            }
            return timestamp
        }

        private fun encode(timestamp: Long, randomHigh: Long, randomLow: Long): String {
            val chars = CharArray(LENGTH)
            var time = timestamp
            for (i in TIME_CHARS - 1 downTo 0) {
                chars[i] = ALPHABET[(time and 31L).toInt()]
                time = time ushr 5
            }
            var low = randomLow
            var high = randomHigh
            for (i in LENGTH - 1 downTo TIME_CHARS) {
                chars[i] = ALPHABET[(low and 31L).toInt()]
                low = (low ushr 5) or (high shl 59)
                high = high ushr 5
            }
            return String(chars)
        }
    }
}
