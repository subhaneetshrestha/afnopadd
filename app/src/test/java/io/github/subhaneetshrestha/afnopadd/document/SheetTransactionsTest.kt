package io.github.subhaneetshrestha.afnopadd.document

import io.github.subhaneetshrestha.afnopadd.document.internal.SheetImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SheetTransactionsTest {

    /** Captures every committed transaction in the order the sheet hands them over. */
    private class RecordingWriter : SheetWriter {
        val transactions = mutableListOf<CommittedTransaction>()
        override fun write(transaction: CommittedTransaction) {
            transactions += transaction
        }
    }

    private val writer = RecordingWriter()
    private val meta = SheetMeta(
        id = SheetId("01JAX9Q8K0TZ3M4N5P6R7S8T9V"),
        title = "Test sheet",
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    private fun newSheet(initial: List<Element> = emptyList()): Sheet = SheetImpl(
        meta = meta,
        initialElements = initial,
        writer = writer,
        ulid = Ulid(clock = { 1_700_000_000_000L }, random = Random(5)),
        clock = { 2_000L },
    )

    @Test
    fun `adding updating and deleting produce exactly those diffs`() {
        val existing = TestElements.stroke(z = FractionalIndex("a0"))
        val doomed = TestElements.shape(z = FractionalIndex("a1"))
        val sheet = newSheet(initial = listOf(existing, doomed))
        val added = TestElements.stroke(z = FractionalIndex("a2"))

        val result = sheet.transact("mixed") {
            add(added)
            update(existing.id) { (it as StrokeElement).copy(anchor = WorldPoint(5.0, 5.0)) }
            delete(doomed.id)
        }

        val committed = (result as TransactionResult.Committed).transaction
        assertEquals("mixed", committed.label)
        assertEquals(3, committed.diffs.size)
        assertEquals(ElementDiff.Added(added), committed.diffs.single { it.id == added.id })
        assertEquals(
            ElementDiff.Updated(before = existing, after = existing.copy(anchor = WorldPoint(5.0, 5.0))),
            committed.diffs.single { it.id == existing.id },
        )
        assertEquals(ElementDiff.Deleted(doomed), committed.diffs.single { it.id == doomed.id })

        val snapshot = sheet.snapshot.value
        assertEquals(setOf(existing.id, added.id), snapshot.elements.keys)
        assertEquals(WorldPoint(5.0, 5.0), snapshot.elements.getValue(existing.id).anchor)
    }

    @Test
    fun `elements in a snapshot are ordered by z`() {
        val top = TestElements.stroke(z = FractionalIndex("a2"))
        val bottom = TestElements.stroke(z = FractionalIndex("a0"))
        val middle = TestElements.shape(z = FractionalIndex("a1"))
        val sheet = newSheet(initial = listOf(top, bottom, middle))

        assertEquals(listOf(bottom.id, middle.id, top.id), sheet.snapshot.value.ordered.map { it.id })
    }

    @Test
    fun `an empty transaction is dropped`() {
        val sheet = newSheet()
        val before = sheet.snapshot.value

        val result = sheet.transact("nothing") {}

        assertSame(TransactionResult.NoChange, result)
        assertSame(before, sheet.snapshot.value)
        assertTrue(writer.transactions.isEmpty())
        assertFalse(sheet.canUndo.value)
    }

    @Test
    fun `a transaction that throws applies nothing`() {
        val sheet = newSheet()
        val before = sheet.snapshot.value

        assertThrows(IllegalStateException::class.java) {
            sheet.transact("broken") {
                add(TestElements.stroke())
                error("boom")
            }
        }

        assertSame(before, sheet.snapshot.value)
        assertTrue(writer.transactions.isEmpty())
    }

    @Test
    fun `updating or deleting an unknown element is rejected and applies nothing`() {
        val sheet = newSheet()
        val before = sheet.snapshot.value

        assertThrows(IllegalArgumentException::class.java) {
            sheet.transact("ghost") { delete(TestElements.stroke().id) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            sheet.transact("ghost") { update(TestElements.stroke().id) { it } }
        }

        assertSame(before, sheet.snapshot.value)
    }

    @Test
    fun `adding an element whose id already exists is rejected`() {
        val element = TestElements.stroke()
        val sheet = newSheet(initial = listOf(element))

        assertThrows(IllegalArgumentException::class.java) {
            sheet.transact("duplicate") { add(element) }
        }
    }

    @Test
    fun `undo then redo restores the identical snapshot`() {
        val existing = TestElements.stroke(z = FractionalIndex("a0"))
        val sheet = newSheet(initial = listOf(existing))
        val initial = sheet.snapshot.value
        val added = TestElements.stroke(z = FractionalIndex("a1"))
        sheet.transact("edit") {
            add(added)
            update(existing.id) { (it as StrokeElement).copy(anchor = WorldPoint(1.0, 1.0)) }
        }
        val edited = sheet.snapshot.value

        sheet.undo()
        assertEquals(initial.elements, sheet.snapshot.value.elements)
        assertEquals(initial.ordered, sheet.snapshot.value.ordered)

        sheet.redo()
        assertEquals(edited.elements, sheet.snapshot.value.elements)
        assertEquals(edited.ordered, sheet.snapshot.value.ordered)
    }

    @Test
    fun `undo and redo are written as inverse transactions that reference the original`() {
        val sheet = newSheet()
        val added = TestElements.stroke()
        val original = (sheet.transact("add") { add(added) } as TransactionResult.Committed).transaction

        sheet.undo()
        sheet.redo()

        assertEquals(3, writer.transactions.size)
        val undo = writer.transactions[1]
        val redo = writer.transactions[2]
        assertEquals(TransactionKind.UNDO, undo.kind)
        assertEquals(original.id, undo.inverseOf)
        assertEquals(listOf(ElementDiff.Deleted(added)), undo.diffs)
        assertEquals(TransactionKind.REDO, redo.kind)
        assertEquals(undo.id, redo.inverseOf)
        assertEquals(listOf(ElementDiff.Added(added)), redo.diffs)
    }

    @Test
    fun `undo and redo flags track a 250 transaction history capped at 200`() {
        val sheet = newSheet()
        assertFalse(sheet.canUndo.value)
        assertFalse(sheet.canRedo.value)

        var last: FractionalIndex? = null
        repeat(250) { i ->
            val z = FractionalIndex.between(last, null)
            last = z
            sheet.transact("add $i") { add(TestElements.stroke(z = z)) }
        }
        assertTrue(sheet.canUndo.value)
        assertFalse(sheet.canRedo.value)

        repeat(200) {
            assertTrue("undo should be available at step $it", sheet.canUndo.value)
            sheet.undo()
        }
        assertFalse("history is capped at 200 entries", sheet.canUndo.value)
        assertTrue(sheet.canRedo.value)
        assertEquals(50, sheet.snapshot.value.elements.size)

        repeat(200) { sheet.redo() }
        assertFalse(sheet.canRedo.value)
        assertEquals(250, sheet.snapshot.value.elements.size)
    }

    @Test
    fun `undo and redo with nothing to do are no-ops`() {
        val sheet = newSheet()

        sheet.undo()
        sheet.redo()

        assertTrue(writer.transactions.isEmpty())
    }

    @Test
    fun `a new transaction clears the redo history`() {
        val sheet = newSheet()
        sheet.transact("first") { add(TestElements.stroke(z = FractionalIndex("a0"))) }
        sheet.undo()
        assertTrue(sheet.canRedo.value)

        sheet.transact("second") { add(TestElements.stroke(z = FractionalIndex("a1"))) }

        assertFalse(sheet.canRedo.value)
    }

    @Test
    fun `diffs reach the writer in commit order with the transaction id and label`() {
        val sheet = newSheet()
        val first = TestElements.stroke(z = FractionalIndex("a0"))
        val second = TestElements.stroke(z = FractionalIndex("a1"))

        val firstResult = sheet.transact("first") { add(first) } as TransactionResult.Committed
        val secondResult = sheet.transact("second") { add(second) } as TransactionResult.Committed

        assertEquals(listOf(firstResult.transaction, secondResult.transaction), writer.transactions)
        assertEquals(listOf("first", "second"), writer.transactions.map { it.label })
        assertTrue(writer.transactions[0].id.value < writer.transactions[1].id.value)
        assertNull(writer.transactions[0].inverseOf)
        assertEquals(TransactionKind.USER, writer.transactions[0].kind)
    }

    @Test
    fun `setting the title changes meta and is undoable`() {
        val sheet = newSheet()

        sheet.transact("rename") { setTitle("Renamed") }

        assertEquals("Renamed", sheet.meta.value.title)
        assertEquals(2_000L, sheet.meta.value.updatedAt)
        assertTrue(sheet.canUndo.value)

        sheet.undo()
        assertEquals("Test sheet", sheet.meta.value.title)
    }

    @Test
    fun `a committed transaction stamps the sheet updated time`() {
        val sheet = newSheet()

        sheet.transact("add") { add(TestElements.stroke()) }

        assertEquals(2_000L, sheet.meta.value.updatedAt)
    }
}
