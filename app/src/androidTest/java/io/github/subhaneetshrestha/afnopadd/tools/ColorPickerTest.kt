package io.github.subhaneetshrestha.afnopadd.tools

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.tools.color.ColorPalette
import io.github.subhaneetshrestha.afnopadd.tools.color.ColorPickerDialog
import io.github.subhaneetshrestha.afnopadd.tools.color.ColorPickerTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorPickerTest {

    @get:Rule
    val rule = createComposeRule()

    private val picked = mutableListOf<Long>()

    private fun show(recents: List<Long> = emptyList()) {
        rule.setContent {
            ColorPickerDialog(initial = 0xFF000000, recents = recents, onPick = { picked += it }, onDismiss = {})
        }
        rule.waitForIdle()
    }

    @Test
    fun tappingASwatchPicksIt() {
        show()
        val red = ColorPalette.SWATCHES[4]

        rule.onNodeWithTag(ColorPickerTags.swatch(red)).performClick()

        assertEquals(listOf(red), picked)
    }

    @Test
    fun recentColoursAreOfferedAndPickable() {
        show(recents = listOf(0xFF123456))

        rule.onNodeWithTag(ColorPickerTags.recent(0xFF123456)).performClick()

        assertEquals(listOf(0xFF123456), picked)
    }

    @Test
    fun aValidHexValueCanBeApplied() {
        show()

        rule.onNodeWithTag(ColorPickerTags.HEX_FIELD).performTextClearance()
        rule.onNodeWithTag(ColorPickerTags.HEX_FIELD).performTextInput("#12")
        rule.onNodeWithTag(ColorPickerTags.APPLY).assertIsNotEnabled()
        rule.onNodeWithTag(ColorPickerTags.HEX_FIELD).performTextInput("ab34")
        rule.onNodeWithTag(ColorPickerTags.APPLY).assertIsEnabled().performClick()

        assertEquals(listOf(0xFF12AB34), picked)
    }
}
