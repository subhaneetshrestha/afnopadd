package io.github.subhaneetshrestha.afnopadd.document

import kotlinx.serialization.Serializable

/**
 * Z-order key: a string that sorts lexicographically. New keys can always be generated between
 * two existing ones, so reordering never renumbers other elements and concurrent inserts from two
 * forks of a sheet stay mergeable.
 */
@JvmInline
@Serializable
value class FractionalIndex(val key: String) : Comparable<FractionalIndex> {

    override fun compareTo(other: FractionalIndex): Int = key.compareTo(other.key)

    companion object {
        fun first(): FractionalIndex = FractionalIndex(FractionalIndexing.generateKeyBetween(null, null))

        /** A key strictly between [before] and [after]; `null` means no bound on that side. */
        fun between(before: FractionalIndex?, after: FractionalIndex?): FractionalIndex =
            FractionalIndex(FractionalIndexing.generateKeyBetween(before?.key, after?.key))
    }
}

/**
 * Base-62 fractional indexing with a variable-length integer part (first character encodes the
 * length) and a fractional tail, following the scheme popularised by Figma and the
 * `fractional-indexing` library. Appending or prepending only increments the integer part, so
 * sequential keys stay short; inserting between neighbours bisects the fractional tail.
 */
internal object FractionalIndexing {

    private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val INTEGER_ZERO = "a0"
    private val SMALLEST_INTEGER = "A" + "0".repeat(26)

    fun generateKeyBetween(a: String?, b: String?): String {
        if (a != null) validateOrderKey(a)
        if (b != null) validateOrderKey(b)
        require(a == null || b == null || a < b) { "'$a' must sort before '$b'" }

        if (a == null) {
            if (b == null) return INTEGER_ZERO
            val ib = integerPart(b)
            val fb = b.substring(ib.length)
            if (ib == SMALLEST_INTEGER) return ib + midpoint("", fb)
            if (ib < b) return ib
            return decrementInteger(ib) ?: error("Cannot generate a key before '$b'")
        }
        if (b == null) {
            val ia = integerPart(a)
            val fa = a.substring(ia.length)
            return incrementInteger(ia) ?: (ia + midpoint(fa, null))
        }
        val ia = integerPart(a)
        val fa = a.substring(ia.length)
        val ib = integerPart(b)
        val fb = b.substring(ib.length)
        if (ia == ib) return ia + midpoint(fa, fb)
        val incremented = incrementInteger(ia) ?: error("Cannot generate a key after '$a'")
        return if (incremented < b) incremented else ia + midpoint(fa, null)
    }

    /** Shortest string strictly between [a] and [b] over [DIGITS]; `null` [b] means no upper bound. */
    private fun midpoint(a: String, b: String?): String {
        require(b == null || a < b) { "'$a' must sort before '$b'" }
        require(!a.endsWith('0') && (b == null || !b.endsWith('0'))) { "Trailing zero in '$a' / '$b'" }
        if (b != null) {
            var n = 0
            while (n < b.length && (a.getOrNull(n) ?: '0') == b[n]) n++
            if (n > 0) return b.substring(0, n) + midpoint(a.drop(n), b.substring(n))
        }
        val digitA = if (a.isNotEmpty()) DIGITS.indexOf(a[0]) else 0
        val digitB = if (b != null) DIGITS.indexOf(b[0]) else DIGITS.length
        if (digitB - digitA > 1) {
            val midDigit = Math.round(0.5 * (digitA + digitB)).toInt()
            return DIGITS[midDigit].toString()
        }
        if (b != null && b.length > 1) return b.substring(0, 1)
        return DIGITS[digitA] + midpoint(a.drop(1), null)
    }

    private fun integerLength(head: Char): Int = when (head) {
        in 'a'..'z' -> head - 'a' + 2
        in 'A'..'Z' -> 'Z' - head + 2
        else -> throw IllegalArgumentException("Invalid order key head '$head'")
    }

    private fun integerPart(key: String): String {
        val length = integerLength(key[0])
        require(length <= key.length) { "Invalid order key '$key'" }
        return key.substring(0, length)
    }

    private fun validateOrderKey(key: String) {
        require(key.isNotEmpty() && key != SMALLEST_INTEGER) { "Invalid order key '$key'" }
        val integer = integerPart(key)
        val fraction = key.substring(integer.length)
        require(!fraction.endsWith('0')) { "Invalid order key '$key'" }
    }

    private fun validateInteger(integer: String) {
        require(integer.length == integerLength(integer[0])) { "Invalid integer part '$integer'" }
    }

    private fun incrementInteger(integer: String): String? {
        validateInteger(integer)
        val head = integer[0]
        val digits = integer.substring(1).toCharArray()
        var carry = true
        var i = digits.size - 1
        while (carry && i >= 0) {
            val next = DIGITS.indexOf(digits[i]) + 1
            if (next == DIGITS.length) {
                digits[i] = '0'
            } else {
                digits[i] = DIGITS[next]
                carry = false
            }
            i--
        }
        if (!carry) return head + String(digits)
        if (head == 'Z') return "a0"
        if (head == 'z') return null
        val nextHead = head + 1
        val zeros = if (nextHead > 'a') digits.size + 1 else digits.size - 1
        return nextHead + "0".repeat(zeros)
    }

    private fun decrementInteger(integer: String): String? {
        validateInteger(integer)
        val head = integer[0]
        val digits = integer.substring(1).toCharArray()
        val lastDigit = DIGITS.last()
        var borrow = true
        var i = digits.size - 1
        while (borrow && i >= 0) {
            val previous = DIGITS.indexOf(digits[i]) - 1
            if (previous == -1) {
                digits[i] = lastDigit
            } else {
                digits[i] = DIGITS[previous]
                borrow = false
            }
            i--
        }
        if (!borrow) return head + String(digits)
        if (head == 'a') return "Z$lastDigit"
        if (head == 'A') return null
        val previousHead = head - 1
        val count = if (previousHead < 'Z') digits.size + 1 else digits.size - 1
        return previousHead + lastDigit.toString().repeat(count)
    }
}
