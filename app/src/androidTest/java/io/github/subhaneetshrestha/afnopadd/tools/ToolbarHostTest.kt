package io.github.subhaneetshrestha.afnopadd.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import io.github.subhaneetshrestha.afnopadd.tools.commands.ZoomCommands
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.R
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The toolbar is rendered from a spec: tools select, commands run and follow their enabled state, unknown refs vanish. */
@RunWith(AndroidJUnit4::class)
class ToolbarHostTest {

    @get:Rule
    val rule = createComposeRule()

    private fun fakeTool(toolId: String, label: Int, icon: Int) = object : Tool {
        override val id = toolId
        override val labelRes = label
        override val iconRes = icon
        override val capabilities = setOf(ToolCapability.STROKE)
        override fun createController(context: ToolContext): ToolController = throw UnsupportedOperationException()
    }

    private val registry = ToolRegistry().apply {
        register(fakeTool("tool:pen", R.string.tool_pen, R.drawable.ic_tool_pen))
        register(fakeTool("tool:pencil", R.string.tool_pencil, R.drawable.ic_tool_pencil))
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
    fun tappingAnotherToolActivatesIt() {
        val twoTools = spec.copy(toolbars = listOf(spec.toolbars.single().copy(items = listOf(ToolbarItem.Ref("tool:pen"), ToolbarItem.Ref("tool:pencil")))))
        rule.setContent { ToolbarHost(spec = twoTools, registry = registry, commands = commands, state = state) }

        rule.onNodeWithTag(ToolbarTags.item("tool:pencil")).performClick()
        rule.waitForIdle()

        assertEquals("tool:pencil", state.activeToolId.value)
        rule.onNodeWithTag(ToolbarTags.item("tool:pencil")).assertIsSelected()
    }

    /** A layout asking for a side toolbar must not stretch over the canvas (side rails are not built yet). */
    @Test
    fun aSidePlacedToolbarStaysToolbarSized() {
        val left = spec.copy(toolbars = listOf(spec.toolbars.single().copy(placement = Placement.LEFT)))
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                ToolbarHost(spec = left, registry = registry, commands = commands, state = state, modifier = Modifier.testTag("host"))
            }
        }

        val bounds = rule.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue("toolbar is ${bounds.height / density} dp tall", bounds.height / density < 120f)
        rule.onNodeWithTag(ToolbarTags.item("tool:pen")).assertIsDisplayed()
    }

    @Test
    fun widgetRefsRenderWhatTheHostSuppliesAndUnknownWidgetsVanish() {
        val withWidgets = spec.copy(
            toolbars = listOf(spec.toolbars.single().copy(items = listOf(ToolbarItem.Ref("widget:zoom"), ToolbarItem.Ref("widget:weather")))),
        )
        rule.setContent {
            ToolbarHost(
                spec = withWidgets, registry = registry, commands = commands, state = state,
                widgets = mapOf("widget:zoom" to { Box(Modifier.testTag("zoom-widget")) }),
            )
        }

        rule.onNodeWithTag("zoom-widget").assertExists()
    }

    @Test
    fun theZoomReadoutShowsTheZoomAndRunsTheChosenCommand() {
        var zoomedIn = 0
        val zoomCommands = CommandRegistry().apply {
            for (id in listOf(ZoomCommands.ZOOM_IN, ZoomCommands.ZOOM_OUT, ZoomCommands.ZOOM_FIT, ZoomCommands.ZOOM_100)) {
                register(Command(id, R.string.command_zoom_in, R.drawable.ic_zoom_in, MutableStateFlow(true)) { if (id == ZoomCommands.ZOOM_IN) zoomedIn++ })
            }
        }
        var zoom by mutableDoubleStateOf(1.5)
        rule.setContent { ZoomReadout(zoom, zoomCommands) }

        rule.onNodeWithTag(ZoomReadoutTags.READOUT).assertTextEquals("150%")
        rule.runOnIdle { zoom = 25.0 }
        rule.onNodeWithTag(ZoomReadoutTags.READOUT).assertTextEquals("25×")

        rule.onNodeWithTag(ZoomReadoutTags.READOUT).performClick()
        rule.onNodeWithTag(ZoomReadoutTags.item(ZoomCommands.ZOOM_IN)).performClick()
        rule.waitForIdle()

        assertEquals(1, zoomedIn)
        rule.onNodeWithTag(ZoomReadoutTags.item(ZoomCommands.ZOOM_IN)).assertDoesNotExist()
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
