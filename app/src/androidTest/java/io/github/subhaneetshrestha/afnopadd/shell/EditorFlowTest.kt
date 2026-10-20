package io.github.subhaneetshrestha.afnopadd.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.R
import io.github.subhaneetshrestha.afnopadd.document.StrokeElement
import io.github.subhaneetshrestha.afnopadd.document.internal.RoomSheetStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The walking skeleton: open the editor, draw with the pen, leave, and the stroke is on disk. */
@RunWith(AndroidJUnit4::class)
class EditorFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun startFromAnEmptySheet() {
        placeholderSheetDir(context).deleteRecursively()
    }

    private fun openEditorAndDraw() {
        composeRule.onNodeWithTag(ShellTags.OPEN_EDITOR).performClick()
        composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).performTouchInput {
            down(Offset(200f, 400f))
            moveTo(Offset(300f, 450f))
            moveTo(Offset(400f, 500f))
            up()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { sheetHasStrokes() }
    }

    private fun sheetHasStrokes(): Boolean = runBlocking {
        val dir = placeholderSheetDir(context)
        if (!dir.isDirectory) return@runBlocking false
        val sheet = RoomSheetStore(context).open(dir)
        try {
            sheet.snapshot.value.elements.values.any { it is StrokeElement }
        } finally {
            sheet.close()
        }
    }

    private fun leaveEditor() {
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag(ShellTags.LIBRARY_SCREEN).assertIsDisplayed()
        composeRule.waitForIdle()
    }

    @Test
    fun aPenStrokeIsPersistedAfterLeavingTheEditor() {
        openEditorAndDraw()
        leaveEditor()

        val sheet = runBlocking { RoomSheetStore(context).open(placeholderSheetDir(context)) }
        try {
            val strokes = sheet.snapshot.value.elements.values.filterIsInstance<StrokeElement>()
            assertEquals(1, strokes.size)
            assertTrue(strokes.single().points.count >= 2)
        } finally {
            runBlocking { sheet.close() }
        }
    }

    @Test
    fun undoRemovesTheStrokeBeforeLeaving() {
        openEditorAndDraw()
        composeRule.onNodeWithContentDescription(context.getString(R.string.command_undo)).performClick()
        composeRule.waitForIdle()
        leaveEditor()

        val sheet = runBlocking { RoomSheetStore(context).open(placeholderSheetDir(context)) }
        try {
            assertTrue(sheet.snapshot.value.elements.isEmpty())
        } finally {
            runBlocking { sheet.close() }
        }
    }
}
