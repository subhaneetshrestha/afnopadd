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

    private fun layout(items: String, schemaVersion: String = "1") =
        """{"schemaVersion":$schemaVersion,"toolbars":[{"id":"main","items":[$items]}]}"""

    private fun nestedGroups(depth: Int): String {
        var items = """{"ref":"tool:pen"}"""
        repeat(depth) { items = """{"group":{"id":"g$it","items":[$items]}}""" }
        return layout(items)
    }

    @Test
    fun `groups nested within the limit parse`() {
        val spec = ToolbarSpecJson.parse(nestedGroups(ToolbarSpecJson.MAX_GROUP_DEPTH))

        assertTrue(spec.toolbars.single().items.single() is ToolbarItem.Group)
    }

    @Test
    fun `deeply nested groups are rejected instead of overflowing the stack`() {
        assertThrows(ToolbarSpecException::class.java) { ToolbarSpecJson.parse(nestedGroups(ToolbarSpecJson.MAX_GROUP_DEPTH + 1)) }
        // An imported file of a few tens of kilobytes used to throw StackOverflowError here.
        assertThrows(ToolbarSpecException::class.java) { ToolbarSpecJson.parse(nestedGroups(5_000)) }
    }

    @Test
    fun `oversized layouts, item lists and ids are rejected`() {
        assertThrows(ToolbarSpecException::class.java) {
            ToolbarSpecJson.parse(layout("""{"ref":"tool:pen"}""") + " ".repeat(ToolbarSpecJson.MAX_LAYOUT_CHARS))
        }
        assertThrows(ToolbarSpecException::class.java) {
            ToolbarSpecJson.parse(layout(List(ToolbarSpecJson.MAX_ITEMS + 1) { """{"ref":"tool:pen"}""" }.joinToString(",")))
        }
        assertThrows(ToolbarSpecException::class.java) {
            ToolbarSpecJson.parse(layout("""{"ref":"tool:${"x".repeat(ToolbarSpecJson.MAX_ID_LENGTH)}"}"""))
        }
    }

    @Test
    fun `a schema version below one is rejected`() {
        assertThrows(ToolbarSpecException::class.java) { ToolbarSpecJson.parse(layout("", schemaVersion = "0")) }
        assertThrows(ToolbarSpecException::class.java) { ToolbarSpecJson.parse(layout("", schemaVersion = "-3")) }
        assertThrows(ToolbarSpecException::class.java) { ToolbarSpecJson.parse(layout("", schemaVersion = "\"1\"")) }
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
