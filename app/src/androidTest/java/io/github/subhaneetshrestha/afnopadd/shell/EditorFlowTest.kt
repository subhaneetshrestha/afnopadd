package io.github.subhaneetshrestha.afnopadd.shell

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextEquals
import io.github.subhaneetshrestha.afnopadd.shell.settings.Settings
import io.github.subhaneetshrestha.afnopadd.tools.ZoomReadoutTags
import io.github.subhaneetshrestha.afnopadd.tools.commands.ZoomCommands
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Before
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    /** Settings and tool styles live in the app's real preferences file; every test starts and ends with the defaults (a finger draws, in black). */
    @Before
    @After
    fun resetSettings() {
        runBlocking { container.settings.update { Settings() } }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { container.toolStyles.reset() }
    }

    private fun camera(sheet: SheetSummary) = container.editorSessions.get(sheet.id.value)!!.camera.camera.value

    private fun newSheet(): SheetSummary {
        composeRule.onNodeWithTag(LibraryTags.NEW_SHEET).performClick()
        awaitCanvas()
        composeRule.waitUntil(timeoutMillis = 5_000) { lastOpened() != null && container.editorSessions.get(lastOpened()!!.id.value) != null }
        return lastOpened()!!
    }

    /** A stylus drag sent through the window, since Compose's test input only makes finger events. */
    private fun stylusDrag(from: Offset, to: Offset) {
        val origin = composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).fetchSemanticsNode().positionOnScreen
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS })
        fun send(action: Int, at: Offset, time: Long) {
            val coords = arrayOf(MotionEvent.PointerCoords().apply { x = origin.x + at.x; y = origin.y + at.y; pressure = 1f; size = 1f })
            val event = MotionEvent.obtain(downTime, time, action, 1, properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, from, downTime)
        for (i in 1..10) send(MotionEvent.ACTION_MOVE, from + (to - from) * (i / 10f), downTime + i * 16L)
        send(MotionEvent.ACTION_UP, to, downTime + 176L)
        composeRule.waitForIdle()
    }

    private fun fingerDrag() {
        composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).performTouchInput {
            down(Offset(200f, 400f))
            repeat(10) { moveBy(Offset(20f, 10f)) }
            up()
        }
        composeRule.waitForIdle()
    }

    private fun strokeCount(sheet: SheetSummary): Int = runBlocking { container.sheetStore.inspect(sheet.dir).elementCount }

    /** Waits for the editor to finish opening its sheet; on a cold start that takes longer than one idle pass. */
    private fun awaitCanvas() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(ShellTags.EDITOR_CANVAS).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).assertIsDisplayed()
    }

    private fun createSheetAndDraw(): SheetSummary {
        val before = lastOpened()?.id
        composeRule.onNodeWithTag(LibraryTags.NEW_SHEET).performClick()
        awaitCanvas()
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
        awaitCanvas()
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
        awaitCanvas()
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
    fun aColourPickedFromThePaletteIsUsedForTheNextStroke() {
        val red = io.github.subhaneetshrestha.afnopadd.tools.color.ColorPalette.SWATCHES[4]
        composeRule.onNodeWithTag(LibraryTags.NEW_SHEET).performClick()
        awaitCanvas()
        composeRule.waitUntil(timeoutMillis = 5_000) { lastOpened() != null }
        val sheet = lastOpened()!!
        composeRule.onNodeWithTag(io.github.subhaneetshrestha.afnopadd.tools.options.StrokeOptionsTags.COLOR).performClick()
        composeRule.onNodeWithTag(io.github.subhaneetshrestha.afnopadd.tools.color.ColorPickerTags.swatch(red)).performClick()
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
            assertEquals(red, stroke.brush.colorArgb)
        } finally {
            runBlocking { opened.close() }
        }
        assertEquals(red, runBlocking { container.recentColors.colors.first() }.first())
    }

    @Test
    fun theEraserRemovesATouchedStrokeAndUndoBringsItBack() {
        val sheet = createSheetAndDraw()
        composeRule.onNodeWithTag(io.github.subhaneetshrestha.afnopadd.tools.ToolbarTags.item("tool:eraser")).performClick()
        composeRule.waitForIdle()
        // Miss first: a swipe well away from the stroke erases nothing.
        composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).performTouchInput {
            down(Offset(600f, 150f))
            moveTo(Offset(650f, 200f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(1, strokeCount(sheet))

        // The stroke runs from (200, 400) to (400, 500); cross it at x = 300.
        composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).performTouchInput {
            down(Offset(300f, 350f))
            moveTo(Offset(300f, 450f))
            moveTo(Offset(300f, 550f))
            up()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { strokeCount(sheet) == 0 }

        composeRule.onNodeWithContentDescription(context.getString(R.string.command_undo)).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { strokeCount(sheet) == 1 }
        leaveEditor()
    }

    /** Resizing Ink's wet-stroke view can crash its render thread (ADR 0003), so tool switches must not. */
    @Test
    fun switchingToolsNeverResizesTheCanvas() {
        composeRule.onNodeWithTag(LibraryTags.NEW_SHEET).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(ShellTags.EDITOR_CANVAS).fetchSemanticsNodes().isNotEmpty()
        }
        val withPen =composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).fetchSemanticsNode().boundsInRoot

        // The eraser has no stroke options, so its options bar disappears.
        composeRule.onNodeWithTag(io.github.subhaneetshrestha.afnopadd.tools.ToolbarTags.item("tool:eraser")).performClick()
        composeRule.waitForIdle()
        assertEquals(withPen, composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).fetchSemanticsNode().boundsInRoot)

        composeRule.onNodeWithTag(io.github.subhaneetshrestha.afnopadd.tools.ToolbarTags.item("tool:pen")).performClick()
        composeRule.waitForIdle()
        assertEquals(withPen, composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).fetchSemanticsNode().boundsInRoot)
    }

    /**
     * The stroke is in the sheet the moment the pen lifts, so leaving straight away cannot lose it,
     * and leaving straight away no longer crashes Ink 1.0.0 (its view stays attached, ADR 0003).
     */
    @Test
    fun aStrokeIsInTheSheetAsSoonAsThePenLifts() {
        composeRule.onNodeWithTag(LibraryTags.NEW_SHEET).performClick()
        awaitCanvas()
        composeRule.waitUntil(timeoutMillis = 5_000) { lastOpened() != null }
        val sheet = lastOpened()!!

        composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).performTouchInput {
            down(Offset(200f, 400f))
            moveTo(Offset(400f, 500f))
            up()
        }
        // Committed on pen-up itself, not after Ink hands the stroke over (that takes ~400 ms).
        composeRule.runOnIdle {
            assertEquals(1, container.editorSessions.get(sheet.id.value)!!.sheet.snapshot.value.elements.size)
        }
        leaveEditor()

        composeRule.waitUntil(timeoutMillis = 10_000) { strokeCount(sheet) == 1 }
    }

    @Test
    fun theRectangleToolDrawsOneRectangle() {
        composeRule.onNodeWithTag(LibraryTags.NEW_SHEET).performClick()
        awaitCanvas()
        composeRule.waitUntil(timeoutMillis = 5_000) { lastOpened() != null }
        val sheet = lastOpened()!!
        // The toolbar scrolls sideways on narrow screens, so bring the button into view first.
        composeRule.onNodeWithTag(io.github.subhaneetshrestha.afnopadd.tools.ToolbarTags.item("tool:rect")).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ShellTags.EDITOR_CANVAS).performTouchInput {
            down(Offset(200f, 400f))
            moveTo(Offset(300f, 450f))
            moveTo(Offset(400f, 500f))
            up()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) { strokeCount(sheet) == 1 }
        leaveEditor()

        val opened = runBlocking { container.sheetStore.open(sheet.dir) }
        try {
            val shape = opened.snapshot.value.elements.values.single() as io.github.subhaneetshrestha.afnopadd.document.ShapeElement
            assertEquals(io.github.subhaneetshrestha.afnopadd.document.ShapeKind.RECTANGLE, shape.shape)
            assertEquals(0xFF000000, shape.style.colorArgb)
        } finally {
            runBlocking { opened.close() }
        }
    }

    @Test
    fun theHandToolPansWithoutChangingTheSheet() {
        val sheet = newSheet()
        composeRule.onNodeWithTag(io.github.subhaneetshrestha.afnopadd.tools.ToolbarTags.item("tool:hand")).performScrollTo().performClick()
        composeRule.waitForIdle()
        val before = camera(sheet)

        fingerDrag()
        stylusDrag(Offset(200f, 300f), Offset(300f, 300f))

        assertNotEquals(before.center, camera(sheet).center)
        assertEquals(before.zoom, camera(sheet).zoom, 1e-9)
        assertEquals(0, container.editorSessions.get(sheet.id.value)!!.sheet.snapshot.value.elements.size)
        assertEquals(false, container.editorSessions.get(sheet.id.value)!!.sheet.canUndo.value)
        leaveEditor()
    }

    @Test
    fun theZoomReadoutZoomsInAndFitsTheContent() {
        val sheet = createSheetAndDraw()
        composeRule.onNodeWithTag(ZoomReadoutTags.READOUT).performScrollTo().assertTextEquals("100%").performClick()
        composeRule.onNodeWithTag(ZoomReadoutTags.item(ZoomCommands.ZOOM_IN)).performClick()
        // The step animates over 150 ms and ends exactly on 1.5x.
        composeRule.waitUntil(timeoutMillis = 2_000) { camera(sheet).zoom == 1.5 }
        composeRule.onNodeWithTag(ZoomReadoutTags.READOUT).assertTextEquals("150%")

        composeRule.onNodeWithTag(ZoomReadoutTags.READOUT).performClick()
        composeRule.onNodeWithTag(ZoomReadoutTags.item(ZoomCommands.ZOOM_FIT)).performClick()
        // A 200 x 100 px stroke fills the canvas width minus the padding: well above 1.5x.
        composeRule.waitUntil(timeoutMillis = 2_000) { camera(sheet).zoom > 2.0 }
        leaveEditor()
    }

    /** Finger mode AUTO: a finger draws until a stylus touches the canvas; from then on a finger pans. */
    @Test
    fun aFingerDrawsUntilAStylusIsSeenThenPans() {
        val sheet = newSheet()
        fingerDrag()
        composeRule.waitUntil(timeoutMillis = 10_000) { strokeCount(sheet) == 1 }

        stylusDrag(Offset(200f, 600f), Offset(400f, 650f))
        composeRule.waitUntil(timeoutMillis = 10_000) { strokeCount(sheet) == 2 }
        composeRule.waitUntil(timeoutMillis = 5_000) { runBlocking { container.settings.settings.first() }.stylusSeen }
        val before = camera(sheet)

        fingerDrag()

        assertNotEquals("a finger now pans", before.center, camera(sheet).center)
        assertEquals("and draws nothing", 2, container.editorSessions.get(sheet.id.value)!!.sheet.snapshot.value.elements.size)
        leaveEditor()
    }

    @Test
    fun forcingFingerDrawingOverridesASeenStylus() {
        runBlocking { container.settings.update { it.copy(stylusSeen = true, fingerMode = io.github.subhaneetshrestha.afnopadd.tools.FingerMode.DRAW) } }
        val sheet = newSheet()

        fingerDrag()

        composeRule.waitUntil(timeoutMillis = 10_000) { strokeCount(sheet) == 1 }
        leaveEditor()
    }

    @Test
    fun undoRemovesTheStrokeBeforeLeaving() {
        val sheet = createSheetAndDraw()
        composeRule.onNodeWithContentDescription(context.getString(R.string.command_undo)).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) { strokeCount(sheet) == 0 }
        leaveEditor()

        assertEquals(0, strokeCount(sheet))
    }
}
