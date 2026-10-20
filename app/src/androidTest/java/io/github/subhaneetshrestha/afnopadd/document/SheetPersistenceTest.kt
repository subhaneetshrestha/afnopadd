package io.github.subhaneetshrestha.afnopadd.document

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.document.internal.RoomSheetStore
import io.github.subhaneetshrestha.afnopadd.document.internal.db.SheetDatabase
import io.github.subhaneetshrestha.afnopadd.document.internal.db.openSheetDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Transactions reach the database atomically, survive reopen, and the sheet cleans up on close. */
@RunWith(AndroidJUnit4::class)
class SheetPersistenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File

    @Before
    fun setUp() {
        root = File(context.cacheDir, "persistence-test-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun store(beforeCommit: suspend (CommittedTransaction) -> Unit = {}) =
        RoomSheetStore(context, beforeCommit = beforeCommit)

    private suspend fun <T> withDatabase(dir: File, block: suspend (SheetDatabase) -> T): T {
        val database = openSheetDatabase(context, File(dir, SheetStore.DATABASE_FILE_NAME))
        try {
            return block(database)
        } finally {
            database.close()
        }
    }

    private fun strokes(count: Int, pointCount: Int): List<StrokeElement> {
        var last: FractionalIndex? = null
        return List(count) { i ->
            val z = FractionalIndex.between(last, null)
            last = z
            DeviceFixtures.stroke(z = z, pointCount = pointCount, seed = i)
        }
    }

    @Test
    fun fiveThousandStrokeTransactionsSurviveReopenWithOneOpRowPerDiff() = runBlocking {
        val dir = File(root, "many")
        val sheet = store().create(dir, title = "Many")
        // Flushing in batches keeps the writer backlog bounded on a memory-starved emulator; the
        // transaction count and the one-op-per-diff invariant are unchanged.
        strokes(count = 5_000, pointCount = 30).chunked(500).forEach { batch ->
            batch.forEach { stroke -> sheet.transact("add") { add(stroke) } }
            sheet.flush()
        }
        val expected = sheet.snapshot.value.elements

        withDatabase(dir) { database -> assertEquals(5_000L, database.opDao().count()) }
        sheet.close()

        val reopened = store().open(dir)
        try {
            assertEquals(expected, reopened.snapshot.value.elements)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun updatesDeletesUndoAndTitlePersist() = runBlocking {
        val dir = File(root, "edits")
        val sheet = store().create(dir, title = "Edits")
        val a = DeviceFixtures.stroke(z = FractionalIndex("a0"), seed = 1)
        val b = DeviceFixtures.shape(z = FractionalIndex("a1"))
        sheet.transact("add") { add(a); add(b) }
        sheet.transact("move") { update(a.id) { (it as StrokeElement).copy(anchor = WorldPoint(9.0, 9.0)) } }
        val deletion = sheet.transact("delete") { delete(b.id) } as TransactionResult.Committed
        sheet.transact("rename") { setTitle("Edited title") }
        sheet.undo() // restores the rename
        sheet.undo() // restores b
        val expectedElements = sheet.snapshot.value.elements
        val expectedMeta = sheet.meta.value
        sheet.close()

        val reopened = store().open(dir)
        try {
            assertEquals(expectedElements, reopened.snapshot.value.elements)
            assertEquals(WorldPoint(9.0, 9.0), reopened.snapshot.value.elements.getValue(a.id).anchor)
            assertTrue(reopened.snapshot.value.elements.containsKey(b.id))
            assertEquals(expectedMeta, reopened.meta.value)
            assertEquals("Edits", reopened.meta.value.title)
        } finally {
            reopened.close()
        }
        withDatabase(dir) { database ->
            val ops = database.opDao().getAll()
            val undoOfDeletion = ops.single { it.inverseOf == deletion.transaction.id.value }
            assertEquals("UNDO", undoOfDeletion.kind)
            assertEquals("add", undoOfDeletion.op)
            assertEquals(b.id.value, undoOfDeletion.elementId)
            assertNull(ops.first { it.op == "title" }.elementId)
        }
    }

    @Test
    fun aFailingWriteRollsBackAndLaterTransactionsStillPersist() = runBlocking {
        val dir = File(root, "faulty")
        val sheet = store(beforeCommit = { if (it.label == "second") error("injected disk failure") }).create(dir, title = "Faulty")
        val first = DeviceFixtures.stroke(z = FractionalIndex("a0"), seed = 1)
        val second = DeviceFixtures.stroke(z = FractionalIndex("a1"), seed = 2)
        val third = DeviceFixtures.stroke(z = FractionalIndex("a2"), seed = 3)
        sheet.transact("first") { add(first) }
        sheet.transact("second") { add(second) }
        sheet.transact("third") { add(third) }

        try {
            sheet.flush()
            fail("flush should report the injected failure")
        } catch (expected: IllegalStateException) {
            assertEquals("injected disk failure", expected.message)
        }
        sheet.flush() // the error is reported once
        sheet.close()

        val reopened = store().open(dir)
        try {
            assertEquals(setOf(first.id, third.id), reopened.snapshot.value.elements.keys)
        } finally {
            reopened.close()
        }
        withDatabase(dir) { database -> assertEquals(2L, database.opDao().count()) }
    }

    @Test
    fun aThousandPointStrokeCommitsQuickly() = runBlocking {
        val dir = File(root, "latency")
        val sheet = store().create(dir, title = "Latency")
        val samples = strokes(count = 50, pointCount = 1_000).map { stroke ->
            val startedAt = System.nanoTime()
            sheet.transact("add") { add(stroke) }
            sheet.flush()
            (System.nanoTime() - startedAt) / 1_000_000.0
        }
        sheet.close()
        val median = samples.sorted()[samples.size / 2]

        Log.i("SheetPersistenceTest", "1,000-point stroke commit median ${"%.1f".format(median)} ms (max ${"%.1f".format(samples.max())} ms)")
        assertTrue("median commit took $median ms", median < 30.0)
    }

    @Test
    fun closeCompactsOpsAndPurgesTombstones() = runBlocking {
        val dir = File(root, "compact")
        val sheet = store().create(dir, title = "Compact")
        val all = strokes(count = 1_200, pointCount = 5)
        all.forEach { stroke -> sheet.transact("add") { add(stroke) } }
        all.take(10).forEach { stroke -> sheet.transact("delete") { delete(stroke.id) } }
        sheet.close()

        withDatabase(dir) { database ->
            assertEquals(1_000L, database.opDao().count())
            assertEquals(0L, database.elementDao().countTombstones())
            assertEquals(1_190, database.strokePointsDao().getAll().size)
        }
    }

    @Test
    fun assetsAreRecordedDedupedAndCollectedOnClose() = runBlocking {
        val dir = File(root, "assets")
        val sheet = store().create(dir, title = "Assets")
        val keptBytes = ByteArray(2_048) { 1 }
        val kept = sheet.assets.put(keptBytes.inputStream(), mime = "image/png", width = 32, height = 32)
        val duplicate = sheet.assets.put(keptBytes.inputStream(), mime = "image/png", width = 32, height = 32)
        val dropped = sheet.assets.put(ByteArray(2_048) { 2 }.inputStream(), mime = "image/png")
        assertEquals(kept, duplicate)
        sheet.transact("place image") {
            add(
                ImageElement(
                    id = ElementId(DeviceFixtures.ulid.next()),
                    anchor = WorldPoint(0.0, 0.0),
                    frameLevel = 0,
                    z = FractionalIndex.first(),
                    bounds = WorldRect(0.0, 0.0, 32.0, 32.0),
                    version = 1,
                    versionNonce = 1,
                    createdAt = 1L,
                    updatedAt = 1L,
                    asset = kept,
                    size = LocalSize(32f, 32f),
                    rotationDeg = 0f,
                    crop = null,
                ),
            )
        }
        val keptFile = sheet.assets.file(kept)
        val droppedFile = sheet.assets.file(dropped)
        sheet.close()

        assertTrue(keptFile.isFile)
        assertFalse(droppedFile.exists())
        withDatabase(dir) { database -> assertEquals(listOf(kept.sha256Hex), database.assetDao().getAll().map { it.id }) }
        val reopened = store().open(dir)
        try {
            assertTrue(reopened.assets.file(kept).isFile)
        } finally {
            reopened.close()
        }
    }
}
