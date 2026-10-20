package io.github.subhaneetshrestha.afnopadd.shell

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.document.SheetStore
import io.github.subhaneetshrestha.afnopadd.library.LibraryTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Leaving the editor, even while the sheet is loading, must release the sheet's database. (The
 * narrower race this guards in spirit, leaving between the open finishing and the screen showing
 * the sheet, is too short to hit reliably; `EditorScreen` closes from the opening coroutine to rule it out.)
 */
@RunWith(AndroidJUnit4::class)
class EditorMemoryTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** Sheet databases this process holds open, read from its own file descriptor table. */
    private fun openSheetDatabases(): Int = File("/proc/self/fd").listFiles().orEmpty().count { fd ->
        runCatching { fd.canonicalPath }.getOrNull()?.endsWith("/${SheetStore.DATABASE_FILE_NAME}") == true
    }

    @Test
    fun leavingTheEditorWhileItLoadsClosesTheSheet() {
        repeat(10) {
            composeRule.onNodeWithTag(LibraryTags.NEW_SHEET).performClick()
            // Back as soon as the editor route is showing, usually while the sheet is still opening.
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag(ShellTags.LIBRARY_SCREEN).fetchSemanticsNodes().isEmpty()
            }
            composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithTag(ShellTags.LIBRARY_SCREEN).fetchSemanticsNodes().isNotEmpty()
            }
        }

        // Closing runs on the app scope after the screen leaves; give it time to finish.
        composeRule.waitUntil(timeoutMillis = 10_000) { openSheetDatabases() == 0 }
        assertEquals(0, openSheetDatabases())
    }
}
