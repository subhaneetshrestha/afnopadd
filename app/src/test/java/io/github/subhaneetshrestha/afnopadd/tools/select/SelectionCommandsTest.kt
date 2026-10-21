package io.github.subhaneetshrestha.afnopadd.tools.select

import io.github.subhaneetshrestha.afnopadd.canvas.Camera
import io.github.subhaneetshrestha.afnopadd.canvas.CameraController
import io.github.subhaneetshrestha.afnopadd.canvas.HitTestFixtures
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.ShapeKind
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.Ulid
import io.github.subhaneetshrestha.afnopadd.document.WorldPoint
import io.github.subhaneetshrestha.afnopadd.document.WorldRect
import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import io.github.subhaneetshrestha.afnopadd.tools.CommandRegistry
import io.github.subhaneetshrestha.afnopadd.tools.ToolContext
import io.github.subhaneetshrestha.afnopadd.tools.ToolStyles
import io.github.subhaneetshrestha.afnopadd.tools.presets.BuiltInPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SelectionCommandsTest {

    // Five elements stacked in z order a1 (bottom) .. a5 (top).
    private val elements = listOf(
        HitTestFixtures.line(1, WorldPoint(0.0, 0.0), WorldPoint(0.0, 100.0), z = "a1"),
        HitTestFixtures.image(2, WorldRect(40.0, 0.0, 60.0, 100.0), z = "a2"),
        HitTestFixtures.shape(3, ShapeKind.ELLIPSE, WorldRect(100.0, 0.0, 200.0, 100.0), z = "a3"),
        HitTestFixtures.line(4, WorldPoint(300.0, 0.0), WorldPoint(300.0, 100.0), z = "a4"),
        HitTestFixtures.line(5, WorldPoint(400.0, 0.0), WorldPoint(400.0, 100.0), z = "a5"),
    )
    private val ids = elements.map { it.id }
    private var transactions = 0
    private val sheet = SheetImpl(SheetMeta(SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"), "Commands", 0L, 0L), elements, writer = { transactions++ })
    private val camera = CameraController(Camera(WorldPoint(0.0, 0.0), zoom = 2.0))
    private val context = ToolContext(
        sheet, camera, ToolStyles(BuiltInPresets.defaultStyles),
        clock = { 7L }, random = Random(3), ulid = Ulid(clock = { 1_800_000_000_000L }, random = Random(4)),
    )
    private val selection = context.selection
    private val commands = CommandRegistry().apply { SelectionCommands(context).all.forEach(::register) }

    /** Ids from bottom to top. */
    private fun stackOrder(): List<ElementId> = sheet.snapshot.value.ordered.map { it.id }

    @Test
    fun `every selection command is disabled without a selection`() {
        for (id in SelectionCommands.IDS) {
            assertFalse(id, commands.get(id)!!.enabled.value)
            assertFalse(commands.invoke(id))
        }
        selection.set(setOf(ids[0]))
        assertTrue(SelectionCommands.IDS.all { commands.get(it)!!.enabled.value })
        assertEquals(0, transactions)
    }

    @Test
    fun `delete removes the selection in one undoable transaction and clears it`() {
        selection.set(setOf(ids[1], ids[3]))

        commands.invoke(SelectionCommands.DELETE)

        assertEquals(listOf(ids[0], ids[2], ids[4]), stackOrder())
        assertTrue(selection.selected.value.isEmpty())
        assertEquals(1, transactions)
        sheet.undo()
        assertEquals(ids, stackOrder())
    }

    @Test
    fun `duplicate copies the selection on top, offset, and selects the copies`() {
        selection.set(setOf(ids[0], ids[2]))

        commands.invoke(SelectionCommands.DUPLICATE)

        assertEquals(1, transactions)
        val all = sheet.snapshot.value.elements
        assertEquals(7, all.size)
        val copies = selection.selected.value.map { all.getValue(it) }
        assertEquals(2, copies.size)
        assertTrue("copies have new ids", copies.none { it.id in ids })
        // On top of everything, keeping their relative order.
        assertEquals(stackOrder().takeLast(2), copies.sortedBy { it.z }.map { it.id })
        // Offset by 16 screen pixels, which is 8 world units at 2x; the stroke's copy stays below the ellipse's.
        val (strokeCopy, ellipseCopy) = copies.sortedBy { it.z }
        assertEquals(WorldPoint(8.0, 8.0), strokeCopy.anchor)
        assertEquals(WorldPoint(108.0, 8.0), ellipseCopy.anchor)
        assertEquals(all.getValue(ids[2]).bounds.left + 8.0, ellipseCopy.bounds.left, 1e-9)
        assertEquals(1, ellipseCopy.version)
        assertEquals(7L, ellipseCopy.createdAt)
    }

    @Test
    fun `bring to front moves only the selected elements, keeping their order, above the rest`() {
        val before = sheet.snapshot.value.elements
        selection.set(setOf(ids[0], ids[2]))

        commands.invoke(SelectionCommands.BRING_TO_FRONT)

        assertEquals(listOf(ids[1], ids[3], ids[4], ids[0], ids[2]), stackOrder())
        val after = sheet.snapshot.value.elements
        for (id in listOf(ids[1], ids[3], ids[4])) assertEquals(before.getValue(id), after.getValue(id))
        for (id in listOf(ids[0], ids[2])) assertNotEquals(before.getValue(id).z, after.getValue(id).z)
        assertEquals(1, transactions)
    }

    @Test
    fun `send to back moves only the selected elements, keeping their order, below the rest`() {
        val before = sheet.snapshot.value.elements
        selection.set(setOf(ids[2], ids[4]))

        commands.invoke(SelectionCommands.SEND_TO_BACK)

        assertEquals(listOf(ids[2], ids[4], ids[0], ids[1], ids[3]), stackOrder())
        for (id in listOf(ids[0], ids[1], ids[3])) assertEquals(before.getValue(id), sheet.snapshot.value.elements.getValue(id))
        sheet.undo()
        assertEquals(ids, stackOrder())
    }

    @Test
    fun `elements that vanished from the sheet, for instance by undo, are dropped from the selection`() {
        selection.set(setOf(ids[0], ids[1]))
        sheet.transact("Delete") { delete(ids[0]) }

        selection.retainOnly(sheet.snapshot.value.elements.keys)

        assertEquals(setOf(ids[1]), selection.selected.value)
    }
}
