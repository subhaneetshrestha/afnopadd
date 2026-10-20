package io.github.subhaneetshrestha.afnopadd.tools.color

import kotlin.math.abs
import kotlin.math.roundToInt

/** The sixteen built-in swatches: neutrals first, then the hue wheel, then brown. */
object ColorPalette {
    val SWATCHES: List<Long> = listOf(
        0xFF000000, 0xFF616161, 0xFF9E9E9E, 0xFFFFFFFF,
        0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF7CB342,
        0xFF43A047, 0xFF00897B, 0xFF039BE5, 0xFF1E88E5,
        0xFF3949AB, 0xFF8E24AA, 0xFFD81B60, 0xFF6D4C41,
    )
}

object HexColor {

    /** Accepts `RRGGBB` or `AARRGGBB`, with or without `#`; six digits mean fully opaque. */
    fun parse(text: String): Long? {
        val digits = text.trim().removePrefix("#")
        if (digits.length != 6 && digits.length != 8) return null
        if (!digits.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
        val value = digits.toLong(16)
        return if (digits.length == 6) value or 0xFF000000 else value
    }

    fun format(argb: Long): String =
        if (argb ushr 24 == 0xFFL) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb and 0xFFFFFFFF)
}

/** Hue in degrees, saturation and value in 0..1; pure Kotlin so it is unit-testable off-device. */
data class Hsv(val hue: Float, val saturation: Float, val value: Float) {

    fun toArgb(alpha: Int = 0xFF): Long {
        val c = value * saturation
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val x = c * (1 - abs(h % 2 - 1))
        val (r1, g1, b1) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = value - c
        fun channel(v: Float) = ((v + m) * 255f).roundToInt().coerceIn(0, 255).toLong()
        return (alpha.toLong() shl 24) or (channel(r1) shl 16) or (channel(g1) shl 8) or channel(b1)
    }

    companion object {
        fun fromArgb(argb: Long): Hsv {
            val r = ((argb shr 16) and 0xFF) / 255f
            val g = ((argb shr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min
            val hue = when {
                delta == 0f -> 0f
                max == r -> 60f * (((g - b) / delta) % 6f)
                max == g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }.let { if (it < 0f) it + 360f else it }
            return Hsv(hue, if (max == 0f) 0f else delta / max, max)
        }
    }
}

/** Most recently used colours: newest first, no duplicates, at most [MAX]. */
object RecentColors {
    const val MAX = 8

    fun add(current: List<Long>, color: Long): List<Long> = (listOf(color) + current.filter { it != color }).take(MAX)

    fun encode(colors: List<Long>): String = colors.joinToString(",") { HexColor.format(it) }

    fun decode(text: String): List<Long> = text.split(',').mapNotNull { HexColor.parse(it) }.take(MAX)
}
