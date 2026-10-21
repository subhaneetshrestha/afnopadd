package io.github.subhaneetshrestha.afnopadd.tools

import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInPresets
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInTools
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolStylesPersistenceTest {

    @Test
    fun `styles round trip through their stored form`() {
        val styles = mapOf(BuiltInTools.PEN to ToolStyle(0xFF2244CC, 7f), BuiltInTools.RECTANGLE to ToolStyle(0x80FF0000, 0.5f))

        assertEquals(styles, ToolStylesCodec.decode(ToolStylesCodec.encode(styles)))
    }

    @Test
    fun `an unreadable value restores nothing`() {
        assertEquals(emptyMap<String, ToolStyle>(), ToolStylesCodec.decode("not json"))
        assertEquals(emptyMap<String, ToolStyle>(), ToolStylesCodec.decode("""{"tool:pen": {"colorArgb": "red"}}"""))
    }

    @Test
    fun `stored widths are brought back inside the allowed range and unknown fields are ignored`() {
        val decoded = ToolStylesCodec.decode("""{"tool:pen": {"colorArgb": 4278190080, "widthPx": 900.0, "texture": "chalk"}}""")

        assertEquals(ToolStyle(0xFF000000, ToolStyles.MAX_WIDTH_PX), decoded.getValue("tool:pen"))
    }

    @Test
    fun `restoring lays saved styles over the defaults`() {
        val styles = ToolStyles(BuiltInPresets.defaultStyles)

        styles.restore(mapOf(BuiltInTools.PEN to ToolStyle(0xFF00FF00, 9f)))

        assertEquals(ToolStyle(0xFF00FF00, 9f), styles.styleFor(BuiltInTools.PEN))
        assertEquals(BuiltInPresets.defaultStyles.getValue(BuiltInTools.HIGHLIGHTER), styles.styleFor(BuiltInTools.HIGHLIGHTER))
    }

    @Test
    fun `resetting returns every tool to its default`() {
        val styles = ToolStyles(BuiltInPresets.defaultStyles)
        styles.update(BuiltInTools.PEN) { it.copy(widthPx = 12f) }

        styles.reset()

        assertEquals(BuiltInPresets.defaultStyles, styles.styles.value)
    }
}
