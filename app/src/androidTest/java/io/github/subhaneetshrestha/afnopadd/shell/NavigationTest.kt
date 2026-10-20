package io.github.subhaneetshrestha.afnopadd.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.library.LibraryTags
import io.github.subhaneetshrestha.afnopadd.tools.ToolbarTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shell's contract: launch on the library, open the editor, back returns, recreation keeps the destination. */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsTheLibrary() {
        composeRule.onNodeWithTag(ShellTags.LIBRARY_SCREEN).assertIsDisplayed()
    }

    @Test
    fun openingTheEditorThenPressingBackReturnsToTheLibrary() {
        composeRule.onNodeWithTag(LibraryTags.NEW_SHEET).performClick()
        composeRule.onNodeWithTag(ShellTags.EDITOR_SCREEN).assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithTag(ShellTags.LIBRARY_SCREEN).assertIsDisplayed()
    }

    @Test
    fun editorSurvivesActivityRecreation() {
        composeRule.onNodeWithTag(LibraryTags.NEW_SHEET).performClick()
        composeRule.onNodeWithTag(ShellTags.EDITOR_SCREEN).assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(ShellTags.EDITOR_SCREEN).assertIsDisplayed()
    }

    /** SPEC-app-shell acceptance 2: rotating keeps the sheet (with its undo history), the camera and the tool. */
    @Test
    fun recreationKeepsTheOpenSheetItsCameraAndTheTool() {
        val sessions = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AfnopaddApp).container.editorSessions
        composeRule.onNodeWithTag(LibraryTags.NEW_SHEET).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { composeRule.onAllNodesWithTag(ShellTags.EDITOR_CANVAS).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).performTouchInput {
            down(Offset(200f, 400f))
            moveTo(Offset(400f, 500f))
            up()
        }
        val session = sessions.open.values.single()
        composeRule.waitUntil(timeoutMillis = 10_000) { session.sheet.snapshot.value.elements.size == 1 }
        val moved = Camera(WorldPoint(123.0, -45.0), zoom = 3.5)
        composeRule.runOnUiThread { session.camera.set(moved) }
        composeRule.onNodeWithTag(ToolbarTags.item("tool:pencil")).performClick()
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 10_000) { composeRule.onAllNodesWithTag(ShellTags.EDITOR_CANVAS).fetchSemanticsNodes().isNotEmpty() }

        val after = sessions.open.values.single()
        assertSame("the sheet was closed and reopened", session.sheet, after.sheet)
        assertTrue("undo history was lost", after.sheet.canUndo.value)
        assertEquals(moved, after.camera.camera.value)
        composeRule.onNodeWithTag(ToolbarTags.item("tool:pencil")).assertIsOn()
    }
}
