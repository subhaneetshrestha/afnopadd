package io.github.subhaneetshrestha.afnopadd.tools.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorLogicTest {

    @Test
    fun `hex parses six and eight digit forms with or without a hash`() {
        assertEquals(0xFFFF5722, HexColor.parse("#FF5722"))
        assertEquals(0xFFFF5722, HexColor.parse(" ff5722 "))
        assertEquals(0x80FF5722, HexColor.parse("#80FF5722"))
    }

    @Test
    fun `invalid hex is rejected`() {
        assertNull(HexColor.parse(""))
        assertNull(HexColor.parse("#12345"))
        assertNull(HexColor.parse("#GG0000"))
        assertNull(HexColor.parse("#1234567890"))
    }

    @Test
    fun `hex formats opaque colours as six digits and translucent ones as eight`() {
        assertEquals("#FF5722", HexColor.format(0xFFFF5722))
        assertEquals("#80FF5722", HexColor.format(0x80FF5722))
    }

    @Test
    fun `hsv converts primary colours both ways`() {
        assertEquals(Hsv(0f, 1f, 1f), Hsv.fromArgb(0xFFFF0000))
        assertEquals(0xFF00FF00, Hsv(120f, 1f, 1f).toArgb())
        assertEquals(0xFF0000FF, Hsv(240f, 1f, 1f).toArgb())
        assertEquals(0xFF000000, Hsv(77f, 0.3f, 0f).toArgb())
    }

    @Test
    fun `every palette colour survives an hsv round trip`() {
        for (color in ColorPalette.SWATCHES) {
            assertEquals(HexColor.format(color), HexColor.format(Hsv.fromArgb(color).toArgb()))
        }
    }

    @Test
    fun `the palette has sixteen distinct opaque colours`() {
        assertEquals(16, ColorPalette.SWATCHES.toSet().size)
        assertEquals(true, ColorPalette.SWATCHES.all { it ushr 24 == 0xFFL })
    }

    @Test
    fun `recent colours keep the newest first, without duplicates, eight at most`() {
        var recents = emptyList<Long>()
        for (color in listOf(1L, 2L, 3L, 2L)) recents = RecentColors.add(recents, color)
        assertEquals(listOf(2L, 3L, 1L), recents)

        for (color in 10L..20L) recents = RecentColors.add(recents, color)
        assertEquals((20L downTo 13L).toList(), recents)
    }

    @Test
    fun `recent colours round trip through their stored form`() {
        val colors = listOf(0xFFFF5722, 0xFF000000, 0x80123456)

        assertEquals(colors, RecentColors.decode(RecentColors.encode(colors)))
        assertEquals(emptyList<Long>(), RecentColors.decode("garbage,,#zz"))
    }
}
