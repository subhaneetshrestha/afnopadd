package io.github.subhaneetshrestha.afnopadd.tools

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.R
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The toolbar is rendered from a spec: tools select, commands run and follow their enabled state, unknown refs vanish. */
@RunWith(AndroidJUnit4::class)
class ToolbarHostTest {

    @get:Rule
    val rule = createComposeRule()

    private val registry = ToolRegistry().apply {
        register(
            object : Tool {
                override val id = "tool:pen"
                override val labelRes = R.string.tool_pen
                override val iconRes = R.drawable.ic_tool_pen
                override val capabilities = setOf(ToolCapability.STROKE)
                override fun createController(context: ToolContext): ToolController = throw UnsupportedOperationException()
            },
        )
    }
    private val undoEnabled = MutableStateFlow(false)
    private var undoRuns = 0
    private val commands = CommandRegistry().apply {
        register(Command("cmd:undo", R.string.command_undo, R.drawable.ic_undo, undoEnabled) { undoRuns++ })
    }
    private val state = ToolbarState(initialToolId = "tool:pen")
    private val spec = ToolbarSpec(
        schemaVersion = 1,
        source = "test",
        toolbars = listOf(
            Toolbar(
                id = "main",
                placement = Placement.BOTTOM,
                items = listOf(ToolbarItem.Ref("tool:pen"), ToolbarItem.Separator, ToolbarItem.Ref("cmd:undo"), ToolbarItem.Ref("tool:hologram")),
            ),
        ),
    )

    private fun show() {
        rule.setContent { ToolbarHost(spec = spec, registry = registry, commands = commands, state = state) }
        rule.waitForIdle()
    }

    @Test
    fun toolButtonsRenderFromTheSpecAndSelect() {
        show()
        val pen = rule.onNodeWithTag(ToolbarTags.item("tool:pen"))
        pen.assertIsSelected()

        rule.onNodeWithTag(ToolbarTags.item("tool:hologram")).assertDoesNotExist()
        assertEquals("tool:pen", state.activeToolId.value)
    }

    @Test
    fun commandButtonsFollowTheirEnabledStateAndRun() {
        show()
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.command_undo)
        val undo = rule.onNodeWithContentDescription(label)
        undo.assertIsNotEnabled()

        rule.runOnUiThread { undoEnabled.value = true }
        rule.waitForIdle()
        undo.assertIsEnabled()
        undo.performClick()
        rule.waitForIdle()

        assertEquals(1, undoRuns)
    }
}
