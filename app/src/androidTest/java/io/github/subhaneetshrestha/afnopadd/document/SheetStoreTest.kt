package io.github.subhaneetshrestha.afnopadd.document

import android.util.Log
import io.github.subhaneetshrestha.afnopadd.document.internal.RoomSheetStore
import io.github.subhaneetshrestha.afnopadd.document.internal.db.MetaEntity
import io.github.subhaneetshrestha.afnopadd.document.internal.db.SheetDatabase
import io.github.subhaneetshrestha.afnopadd.document.internal.db.openSheetDatabase
import io.github.subhaneetshrestha.afnopadd.document.internal.db.toElementEntity
import io.github.subhaneetshrestha.afnopadd.document.internal.db.toStrokePointsEntity
import io.github.subhaneetshrestha.afnopadd.document.internal.json.ElementJsonCodec
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** `SheetStore.create` and `open` against the real per-sheet database on a device. */
@RunWith(AndroidJUnit4::class)
class SheetStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val codec = ElementJsonCodec()
    private lateinit var root: File
    private lateinit var store: SheetStore

    @Before
    fun setUp() {
        root = File(context.cacheDir, "sheet-store-test-${System.nanoTime()}").apply { mkdirs() }
        store = RoomSheetStore(context)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private suspend fun <T> withSeededDatabase(dir: File, block: suspend (SheetDatabase) -> T): T {
        val database = openSheetDatabase(context, File(dir, SheetStore.DATABASE_FILE_NAME))
        try {
            return block(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun createMakesADatabaseWithSchemaVersionOneAndTheTitle() = runBlocking {
        val dir = File(root, "new-sheet")

        val sheet = store.create(dir, title = "First sheet")
        val createdMeta = sheet.meta.value
        sheet.close()

        assertTrue(File(dir, SheetStore.DATABASE_FILE_NAME).exists())
        assertEquals(SHEET_SCHEMA_VERSION, createdMeta.schemaVersion)
        assertEquals("First sheet", createdMeta.title)
        assertTrue(Ulid.isValid(createdMeta.id.value))

        val reopened = store.open(dir)
        try {
            assertEquals(createdMeta, reopened.meta.value)
            assertTrue(reopened.snapshot.value.elements.isEmpty())
        } finally {
            reopened.close()
        }
    }

    @Test
    fun openReturnsElementsWrittenThroughTheDaosInZOrder() = runBlocking {
        val dir = File(root, "seeded")
        store.create(dir, title = "Seeded").close()
        val top = DeviceFixtures.stroke(z = FractionalIndex("a2"), seed = 1)
        val bottom = DeviceFixtures.stroke(z = FractionalIndex("a0"), seed = 2)
        val middle = DeviceFixtures.shape(z = FractionalIndex("a1"))
        withSeededDatabase(dir) { database ->
            database.elementDao().upsertAll(listOf(top, bottom, middle).map { it.toElementEntity(codec) })
            database.strokePointsDao().upsertAll(listOf(top, bottom).map { it.toStrokePointsEntity() })
        }

        val sheet = store.open(dir)
        try {
            assertEquals(listOf(bottom.id, middle.id, top.id), sheet.snapshot.value.ordered.map { it.id })
            assertEquals(top, sheet.snapshot.value.elements[top.id])
            assertEquals(middle, sheet.snapshot.value.elements[middle.id])
        } finally {
            sheet.close()
        }
    }

    @Test
    fun openIgnoresTombstonedElements() = runBlocking {
        val dir = File(root, "tombstones")
        store.create(dir, title = "Tombstones").close()
        val live = DeviceFixtures.shape(z = FractionalIndex("a0"))
        val dead = DeviceFixtures.shape(z = FractionalIndex("a1"))
        withSeededDatabase(dir) { database ->
            database.elementDao().upsertAll(
                listOf(live.toElementEntity(codec), dead.toElementEntity(codec).copy(deleted = true)),
            )
        }

        val sheet = store.open(dir)
        try {
            assertEquals(setOf(live.id), sheet.snapshot.value.elements.keys)
        } finally {
            sheet.close()
        }
    }

    @Test
    fun openRejectsANewerSchemaVersion() = runBlocking {
        val dir = File(root, "future")
        store.create(dir, title = "Future").close()
        withSeededDatabase(dir) { database ->
            database.metaDao().upsert(MetaEntity(key = "schema_version", value = "999"))
        }

        try {
            store.open(dir).close()
            fail("expected SheetFormatException")
        } catch (expected: SheetFormatException) {
            assertTrue(expected.message.orEmpty().contains("999"))
        }
    }

    @Test
    fun openingFiveThousandStrokesTakesUnderASecond() = runBlocking {
        val dir = File(root, "large")
        store.create(dir, title = "Large").close()
        var last: FractionalIndex? = null
        val strokes = List(5_000) { i ->
            val z = FractionalIndex.between(last, null)
            last = z
            DeviceFixtures.stroke(z = z, pointCount = 150, seed = i)
        }
        withSeededDatabase(dir) { database ->
            database.elementDao().upsertAll(strokes.map { it.toElementEntity(codec) })
            database.strokePointsDao().upsertAll(strokes.map { it.toStrokePointsEntity() })
        }

        val startedAt = System.nanoTime()
        val sheet = store.open(dir)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        try {
            Log.i("SheetStoreTest", "Opened 5,000 strokes in $elapsedMillis ms")
            assertEquals(5_000, sheet.snapshot.value.elements.size)
            assertEquals(150, (sheet.snapshot.value.elements.getValue(strokes[0].id) as StrokeElement).points.count)
            assertTrue("open took $elapsedMillis ms", elapsedMillis < 1_000)
        } finally {
            sheet.close()
        }
    }
}
