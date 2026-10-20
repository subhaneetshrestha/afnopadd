package io.github.subhaneetshrestha.afnopadd.tools

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistriesTest {

    private fun tool(id: String) = object : Tool {
        override val id = id
        override val labelRes = 0
        override val iconRes = 0
        override val capabilities = setOf(ToolCapability.STROKE)
        override fun createController(context: ToolContext): ToolController = throw UnsupportedOperationException()
    }

    @Test
    fun `tools are looked up by id in registration order`() {
        val registry = ToolRegistry()

        registry.register(tool("tool:pen"))
        registry.register(tool("tool:eraser"))

        assertEquals(listOf("tool:pen", "tool:eraser"), registry.all.map { it.id })
        assertEquals("tool:eraser", registry.get("tool:eraser")?.id)
        assertNull(registry.get("tool:hologram"))
    }

    @Test
    fun `registering the same tool id twice is a programming error`() {
        val registry = ToolRegistry()
        registry.register(tool("tool:pen"))

        assertThrows(IllegalArgumentException::class.java) { registry.register(tool("tool:pen")) }
    }

    @Test
    fun `commands run when invoked and report whether they exist`() {
        val registry = CommandRegistry()
        var runs = 0
        registry.register(Command(id = "cmd:undo", labelRes = 0, iconRes = 0, enabled = MutableStateFlow(true)) { runs++ })

        assertTrue(registry.invoke("cmd:undo"))
        assertFalse(registry.invoke("cmd:missing"))
        assertEquals(1, runs)
    }

    @Test
    fun `disabled commands do not run`() {
        val registry = CommandRegistry()
        var runs = 0
        registry.register(Command(id = "cmd:redo", labelRes = 0, iconRes = 0, enabled = MutableStateFlow(false)) { runs++ })

        assertFalse(registry.invoke("cmd:redo"))
        assertEquals(0, runs)
    }
}
