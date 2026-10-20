package io.github.subhaneetshrestha.afnopadd.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.subhaneetshrestha.afnopadd.tools.options.StrokeOptionsBar
import io.github.subhaneetshrestha.afnopadd.tools.options.StrokeOptionsTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrokeOptionsBarTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tappingAPresetSetsTheWidthAndSelectsIt() {
        var reported = 0f
        rule.setContent {
            var width by remember { mutableFloatStateOf(4f) }
            StrokeOptionsBar(widthPx = width, onWidthChange = { width = it; reported = it }, colorArgb = 0xFF000000, onColorClick = {})
        }

        rule.onNodeWithTag(StrokeOptionsTags.preset(4f)).assertIsSelected()
        rule.onNodeWithTag(StrokeOptionsTags.preset(16f)).performClick()
        rule.waitForIdle()

        assertEquals(16f, reported, 0f)
        rule.onNodeWithTag(StrokeOptionsTags.preset(16f)).assertIsSelected()
        rule.onNodeWithTag(StrokeOptionsTags.SLIDER).assertExists()
    }

    @Test
    fun theColourButtonOpensThePicker() {
        var opened = 0
        rule.setContent {
            StrokeOptionsBar(widthPx = 4f, onWidthChange = {}, colorArgb = 0xFFFF0000, onColorClick = { opened++ })
        }

        rule.onNodeWithTag(StrokeOptionsTags.COLOR).performClick()

        assertEquals(1, opened)
    }
}
