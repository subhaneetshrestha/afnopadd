package io.github.subhaneetshrestha.afnopadd.images

import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.HitTestFixtures
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.LocalRect
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import io.github.subhaneetshrestha.afnopadd.images.commands.ImageCommands
import io.github.subhaneetshrestha.afnopadd.tools.CommandRegistry
import io.github.subhaneetshrestha.afnopadd.tools.ToolContext
import io.github.subhaneetshrestha.afnopadd.tools.ToolStyles
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInPresets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCommandsTest {

    // Three images out of order along x, at different heights and sizes, and a stroke.
    private val a = HitTestFixtures.image(1, WorldRect(300.0, 0.0, 400.0, 50.0), z = "a1")
    private val b = HitTestFixtures.image(2, WorldRect(0.0, 100.0, 60.0, 160.0), z = "a2")
    private val c = HitTestFixtures.image(3, WorldRect(100.0, -40.0, 140.0, 0.0), z = "a3")
    private val stroke = HitTestFixtures.line(4, WorldPoint(0.0, 300.0), WorldPoint(50.0, 300.0), z = "a4")
    private var transactions = 0
    private val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Board", 0L, 0L), listOf(a, b, c, stroke), writer = { transactions++ })
    private val camera = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 2.0))
    private val context = ToolContext(sheet, camera, ToolStyles(BuiltInPresets.defaultStyles), clock = { 5L }, density = 1f)
    private var cropRequested: ImageElement? = null
    // Unconfined, so the commands' enabled states follow the selection and the sheet at once.
    private val commands = CommandRegistry().apply {
        ImageCommands(context, CoroutineScope(Dispatchers.Unconfined), onCrop = { cropRequested = it }).all.forEach(::register)
    }

    private fun element(id: io.github.subhaneetshrestha.afnopadd.document.ElementId) = sheet.snapshot.value.elements.getValue(id)

    @Test
    fun `arrange in row lines the selected images up left to right with 16 dp gaps in one transaction`() {
        context.selection.set(setOf(a.id, b.id, c.id, stroke.id))

        assertTrue(commands.invoke(ImageCommands.ARRANGE_ROW))

        assertEquals(1, transactions)
        // Ordered by where they were (b, c, a); the first stays put and the others follow it,
        // centred on its middle, 16 dp apart, which is 8 world units at 2x.
        val (first, second, third) = listOf(b.id, c.id, a.id).map { element(it).bounds }
        assertEquals(b.bounds, first)
        assertEquals(WorldRect(68.0, 110.0, 108.0, 150.0), second)
        assertEquals(WorldRect(116.0, 105.0, 216.0, 155.0), third)
        assertEquals("the stroke is not an image and stays", stroke, element(stroke.id))
        assertEquals(2, element(c.id).version)
        sheet.undo()
        assertEquals(a, element(a.id))
    }

    @Test
    fun `arrange in row needs two selected images`() {
        context.selection.set(setOf(a.id, stroke.id))
        assertFalse(commands.get(ImageCommands.ARRANGE_ROW)!!.enabled.value)

        context.selection.set(setOf(a.id, b.id))
        assertTrue(commands.get(ImageCommands.ARRANGE_ROW)!!.enabled.value)
    }

    @Test
    fun `crop is offered for exactly one selected image, and reset only once it is cropped`() {
        context.selection.set(setOf(a.id))
        assertTrue(commands.get(ImageCommands.CROP)!!.enabled.value)
        assertFalse(commands.get(ImageCommands.RESET_CROP)!!.enabled.value)

        commands.invoke(ImageCommands.CROP)
        assertEquals(a.id, cropRequested?.id)

        context.selection.set(setOf(a.id, b.id))
        assertFalse(commands.get(ImageCommands.CROP)!!.enabled.value)
    }

    @Test
    fun `reset crop shows the whole image again in one undoable transaction`() {
        sheet.transact("Crop") { update(a.id) { (it as ImageElement).copy(crop = LocalRect(10f, 10f, 50f, 40f)) } }
        context.selection.set(setOf(a.id))
        transactions = 0
        assertTrue(commands.get(ImageCommands.RESET_CROP)!!.enabled.value)

        commands.invoke(ImageCommands.RESET_CROP)

        val reset = element(a.id) as ImageElement
        assertEquals(null, reset.crop)
        assertEquals(a.bounds, reset.bounds)
        assertEquals(1, transactions)
    }
}
