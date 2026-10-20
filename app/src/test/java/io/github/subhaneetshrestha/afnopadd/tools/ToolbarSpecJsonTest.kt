package io.github.subhaneetshrestha.afnopadd.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ToolbarSpecJsonTest {

    @Test
    fun `parses the built-in default layout`() {
        val text = File("src/main/assets/tool_layout_default.json").readText()

        val spec = ToolbarSpecJson.parse(text)

        assertEquals(1, spec.schemaVersion)
        val main = spec.toolbars.single()
        assertEquals("main", main.id)
        assertEquals(Placement.BOTTOM, main.placement)
        assertTrue(ToolbarItem.Ref("tool:pen") in main.items)
        assertTrue(ToolbarItem.Ref("cmd:undo") in main.items)
        assertTrue(ToolbarItem.Ref("cmd:redo") in main.items)
    }

    @Test
    fun `parses refs separators and groups and keeps unknown refs`() {
        val spec = ToolbarSpecJson.parse(
            """
            {"schemaVersion":1,"source":"user","toolbars":[{"id":"main","placement":"left","items":[
              {"ref":"tool:pen"},{"separator":true},
              {"group":{"id":"shapes","label":"Shapes","items":[{"ref":"tool:line"},{"ref":"tool:hologram"}]}},
              {"ref":"cmd:undo"}]}]}
            """.trimIndent(),
        )

        val main = spec.toolbars.single()
        assertEquals("user", spec.source)
        assertEquals(Placement.LEFT, main.placement)
        assertEquals(
            listOf(
                ToolbarItem.Ref("tool:pen"),
                ToolbarItem.Separator,
                ToolbarItem.Group("shapes", "Shapes", listOf(ToolbarItem.Ref("tool:line"), ToolbarItem.Ref("tool:hologram"))),
                ToolbarItem.Ref("cmd:undo"),
            ),
            main.items,
        )
    }

    @Test
    fun `unknown keys and unknown item shapes are tolerated`() {
        val spec = ToolbarSpecJson.parse(
            """{"schemaVersion":1,"future":{"x":1},"toolbars":[{"id":"main","placement":"bottom","extra":2,"items":[{"ref":"tool:pen","hint":"h"},{"widget":"clock"}]}]}""",
        )

        assertEquals(listOf(ToolbarItem.Ref("tool:pen")), spec.toolbars.single().items)
    }

    @Test
    fun `a newer schema version is rejected`() {
        assertThrows(ToolbarSpecException::class.java) {
            ToolbarSpecJson.parse("""{"schemaVersion":99,"toolbars":[]}""")
        }
    }

    @Test
    fun `malformed json is a spec error not a crash`() {
        assertThrows(ToolbarSpecException::class.java) { ToolbarSpecJson.parse("{not json") }
        assertThrows(ToolbarSpecException::class.java) { ToolbarSpecJson.parse("""{"schemaVersion":1}""") }
    }

    @Test
    fun `a spec round trips through encode`() {
        val original = ToolbarSpec(
            schemaVersion = 1,
            source = "test",
            toolbars = listOf(
                Toolbar(
                    id = "main",
                    placement = Placement.RIGHT,
                    items = listOf(ToolbarItem.Ref("tool:pen"), ToolbarItem.Separator, ToolbarItem.Group("g", "G", listOf(ToolbarItem.Ref("cmd:undo")))),
                ),
            ),
        )

        assertEquals(original, ToolbarSpecJson.parse(ToolbarSpecJson.encode(original)))
    }
}
