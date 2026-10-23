package io.github.subhaneetshrestha.afnopadd.library

import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.Ulid
import io.github.subhaneetshrestha.afnopadd.PerfBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(41))
    private val events = mutableListOf<String>()

    private fun summary(title: String, opened: Long? = null) = SheetSummary(
        id = SheetId(ulid.next()),
        title = title,
        dir = File("/nonexistent"),
        createdAt = 1_000L,
        updatedAt = 2_000L,
        lastOpenedAt = opened,
        thumbnail = null,
        elementCount = 0,
    )

    private fun show(sheets: List<SheetSummary>, lastOpened: SheetSummary? = null) {
        rule.setContent {
            LibraryScreen(
                sheets = sheets,
                lastOpened = lastOpened,
                onNewSheet = { events += "new" },
                onOpenSheet = { events += "open:${it.value}" },
                onRename = { id, title -> events += "rename:${id.value}:$title" },
                onDuplicate = { events += "duplicate:${it.value}" },
                onDelete = { events += "delete:${it.value}" },
                onRestore = { events += "restore:${it.value}" },
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun anEmptyLibraryOffersOnlyANewSheet() {
        show(emptyList())

        rule.onNodeWithTag(LibraryTags.CONTINUE).assertDoesNotExist()
        rule.onNodeWithTag(LibraryTags.NEW_SHEET).assertIsDisplayed().performClick()

        assertEquals(listOf("new"), events)
    }

    @Test
    fun continueShowsTheLastOpenedSheetAndOpensIt() {
        val last = summary("Garden ideas", opened = 5_000L)
        show(listOf(last, summary("Sheet 1")), lastOpened = last)

        rule.onNodeWithTag(LibraryTags.CONTINUE).assertIsDisplayed()
        rule.onNode(hasText("Garden ideas") and hasAnyAncestor(hasTestTag(LibraryTags.CONTINUE)), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(LibraryTags.CONTINUE).performClick()

        assertEquals(listOf("open:${last.id.value}"), events)
    }

    @Test
    fun tappingASheetInTheGridOpensIt() {
        val sheet = summary("Sheet 1")
        show(listOf(sheet))

        rule.onNodeWithTag(LibraryTags.sheet(sheet.id)).performClick()

        assertEquals(listOf("open:${sheet.id.value}"), events)
    }

    @Test
    fun renameValidatesTheTitleBeforeConfirming() {
        val sheet = summary("Sheet 1")
        show(listOf(sheet))

        rule.onNodeWithTag(LibraryTags.sheet(sheet.id)).performTouchInput { longClick() }
        rule.onNodeWithTag(LibraryTags.MENU_RENAME).performClick()
        rule.onNodeWithTag(LibraryTags.RENAME_FIELD).performTextClearance()
        rule.onNodeWithTag(LibraryTags.DIALOG_CONFIRM).assertIsNotEnabled()
        rule.onNodeWithTag(LibraryTags.RENAME_FIELD).performTextInput("x".repeat(81))
        rule.onNodeWithTag(LibraryTags.DIALOG_CONFIRM).assertIsNotEnabled()
        rule.onNodeWithTag(LibraryTags.RENAME_FIELD).performTextClearance()
        rule.onNodeWithTag(LibraryTags.RENAME_FIELD).performTextInput("  Plans ")
        rule.onNodeWithTag(LibraryTags.DIALOG_CONFIRM).assertIsEnabled().performClick()

        assertEquals(listOf("rename:${sheet.id.value}:Plans"), events)
    }

    @Test
    fun duplicateRunsFromTheMenu() {
        val sheet = summary("Sheet 1")
        show(listOf(sheet))

        rule.onNodeWithTag(LibraryTags.sheet(sheet.id)).performTouchInput { longClick() }
        rule.onNodeWithTag(LibraryTags.MENU_DUPLICATE).performClick()

        assertEquals(listOf("duplicate:${sheet.id.value}"), events)
    }

    @Test
    fun deleteAsksForConfirmationThenOffersUndo() {
        val sheet = summary("Sheet 1")
        show(listOf(sheet))

        rule.onNodeWithTag(LibraryTags.sheet(sheet.id)).performTouchInput { longClick() }
        rule.onNodeWithTag(LibraryTags.MENU_DELETE).performClick()
        assertTrue("nothing is deleted before confirming", events.isEmpty())
        rule.onNodeWithTag(LibraryTags.DIALOG_CONFIRM).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(LibraryTags.UNDO).performClick()
        rule.waitForIdle()

        assertEquals(listOf("delete:${sheet.id.value}", "restore:${sheet.id.value}"), events)
    }

    @Test
    fun fiveHundredSheetsRenderQuicklyAndScroll() {
        val sheets = List(500) { summary("Sheet ${it + 1}") }

        val startedAt = System.nanoTime()
        show(sheets, lastOpened = sheets.first())
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        Log.i("LibraryScreenTest", "500 sheets rendered in $elapsedMillis ms")
        rule.onNodeWithTag(LibraryTags.GRID).performScrollToIndex(LibraryTags.HEADER_ITEMS + 499)
        rule.onNodeWithTag(LibraryTags.sheet(sheets.last().id)).assertIsDisplayed()

        assertTrue("render took $elapsedMillis ms", elapsedMillis < PerfBudget.millis(500L))
    }
}
