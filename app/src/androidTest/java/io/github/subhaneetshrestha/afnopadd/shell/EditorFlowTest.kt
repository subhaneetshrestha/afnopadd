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
import io.github.subhaneetshrestha.afnopadd.library.LibraryTags
import io.github.subhaneetshrestha.afnopadd.library.SheetSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The menu-to-editor flow: create a sheet, draw, leave, and continue where you left off. */
@RunWith(AndroidJUnit4::class)
class EditorFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as AfnopaddApp).container

    private fun lastOpened(): SheetSummary? = runBlocking { container.library.lastOpened.first() }

    private fun strokeCount(sheet: SheetSummary): Int = runBlocking { container.sheetStore.inspect(sheet.dir).elementCount }

    private fun createSheetAndDraw(): SheetSummary {
        val before = lastOpened()?.id
        composeRule.onNodeWithTag(LibraryTags.NEW_SHEET).performClick()
        composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) { lastOpened()?.id.let { it != null && it != before } }
        val sheet = lastOpened()!!
        composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).performTouchInput {
            down(Offset(200f, 400f))
            moveTo(Offset(300f, 450f))
            moveTo(Offset(400f, 500f))
            up()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { strokeCount(sheet) == 1 }
        return sheet
    }

    private fun leaveEditor() {
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithTag(ShellTags.LIBRARY_SCREEN).assertIsDisplayed()
        composeRule.waitForIdle()
    }

    @Test
    fun aNewSheetKeepsItsStrokeAndBecomesContinue() {
        val sheet = createSheetAndDraw()
        leaveEditor()

        assertEquals(1, strokeCount(sheet))
        composeRule.onNodeWithTag(LibraryTags.CONTINUE).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { container.library.sheets.first() }.first { it.id == sheet.id }.elementCount == 1
        }
        composeRule.onNodeWithTag(LibraryTags.CONTINUE).performClick()
        composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).assertIsDisplayed()
        assertEquals(sheet.id, lastOpened()?.id)
    }

    @Test
    fun leavingTheEditorWritesAThumbnailTheMenuCanShow() {
        val sheet = createSheetAndDraw()
        leaveEditor()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { container.library.sheets.first() }.first { it.id == sheet.id }.thumbnail?.isFile == true
        }
        val thumbnail = runBlocking { container.library.sheets.first() }.first { it.id == sheet.id }.thumbnail!!
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(thumbnail.absolutePath, bounds)
        assertEquals(512, maxOf(bounds.outWidth, bounds.outHeight))
    }

    @Test
    fun thePencilDrawsWithItsPreset() {
        composeRule.onNodeWithTag(LibraryTags.NEW_SHEET).performClick()
        composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).assertIsDisplayed()
        composeRule.onNodeWithTag(io.github.subhaneetshrestha.afnopadd.tools.ToolbarTags.item("tool:pencil")).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { lastOpened() != null }
        val sheet = lastOpened()!!
        composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).performTouchInput {
            down(Offset(200f, 400f))
            moveTo(Offset(400f, 500f))
            up()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { strokeCount(sheet) == 1 }
        leaveEditor()

        val opened = runBlocking { container.sheetStore.open(sheet.dir) }
        try {
            val stroke = opened.snapshot.value.elements.values.single() as io.github.subhaneetshrestha.afnopadd.document.StrokeElement
            assertEquals("ink:pressure-pen", stroke.brush.familyId)
            assertEquals(0.6f, stroke.brush.opacity, 1e-6f)
        } finally {
            runBlocking { opened.close() }
        }
    }

    @Test
    fun undoRemovesTheStrokeBeforeLeaving() {
        val sheet = createSheetAndDraw()
        composeRule.onNodeWithContentDescription(context.getString(R.string.command_undo)).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { strokeCount(sheet) == 0 }
        leaveEditor()

        assertEquals(0, strokeCount(sheet))
    }
}
