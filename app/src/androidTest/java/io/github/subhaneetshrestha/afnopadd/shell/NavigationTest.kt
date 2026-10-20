package io.github.subhaneetshrestha.afnopadd.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shell's contract: launch on the library, navigate to the editor, back returns, recreation keeps the destination. */
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
        composeRule.onNodeWithTag(ShellTags.OPEN_EDITOR).performClick()
        composeRule.onNodeWithTag(ShellTags.EDITOR_SCREEN).assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithTag(ShellTags.LIBRARY_SCREEN).assertIsDisplayed()
    }

    @Test
    fun editorSurvivesActivityRecreation() {
        composeRule.onNodeWithTag(ShellTags.OPEN_EDITOR).performClick()
        composeRule.onNodeWithTag(ShellTags.EDITOR_SCREEN).assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag(ShellTags.EDITOR_SCREEN).assertIsDisplayed()
    }
}
