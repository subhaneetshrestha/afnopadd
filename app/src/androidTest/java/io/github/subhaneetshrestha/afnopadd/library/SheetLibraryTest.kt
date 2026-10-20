package io.github.subhaneetshrestha.afnopadd.library

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.subhaneetshrestha.afnopadd.document.DeviceFixtures
import io.github.subhaneetshrestha.afnopadd.document.FractionalIndex
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.internal.RoomSheetStore
import io.github.subhaneetshrestha.afnopadd.library.internal.LibraryDatabase
import io.github.subhaneetshrestha.afnopadd.library.internal.RoomSheetLibrary
import io.github.subhaneetshrestha.afnopadd.library.internal.openLibraryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import io.github.subhaneetshrestha.afnopadd.PerfBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SheetLibraryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = RoomSheetStore(context)
    private lateinit var base: File
    private lateinit var database: LibraryDatabase
    private var now = 1_000_000L

    private fun library() = RoomSheetLibrary(
        store = store,
        database = database,
        root = File(base, "sheets"),
        trash = File(base, "trash"),
        clock = { now++ },
    )

    @Before
    fun setUp() {
        base = File(context.cacheDir, "library-test-${System.nanoTime()}").apply { mkdirs() }
        database = openLibraryDatabase(context, File(base, "library.db"))
    }

    @After
    fun tearDown() {
        database.close()
        base.deleteRecursively()
    }

    @Test
    fun createAssignsDefaultTitlesAndListsMostRecentlyOpenedFirst(): Unit = runBlocking {
        val library = library()
        val first = library.createSheet()
        val second = library.createSheet()

        assertEquals("Sheet 1", first.title)
        assertEquals("Sheet 2", second.title)
        assertTrue(File(first.dir, "sheet.db").isFile)
        assertEquals(first.id.value, first.dir.name)
        assertEquals(listOf(second.id, first.id), library.sheets.first().map { it.id })
        assertNull(library.lastOpened.first())

        library.markOpened(first.id)

        assertEquals(listOf(first.id, second.id), library.sheets.first().map { it.id })
        assertEquals(first.id, library.lastOpened.first()?.id)
    }

    @Test
    fun reconcileQuarantinesAnUnreadableSheetAndKeepsTheRest(): Unit = runBlocking {
        val library = library()
        val healthy = library.createSheet("Healthy")
        // What a process killed mid-copy leaves behind: a directory whose database is cut short.
        val broken = File(base, "sheets/01JAX9Q8K0TZ3M4N5P6R7S8T9V").apply { mkdirs() }
        File(broken, "sheet.db").writeBytes(File(healthy.dir, "sheet.db").readBytes().copyOf(512))

        library().reconcile()

        assertEquals(listOf(healthy.id), library.sheets.first().map { it.id })
        assertFalse("the broken directory is still in the library root", broken.exists())
        assertTrue("the broken directory was not kept for recovery", File(base, "quarantine/${broken.name}/sheet.db").isFile)
    }

    @Test
    fun aDeleteWaitsForTheEditorToFinishClosingTheSheet(): Unit = runBlocking {
        val library = library()
        val sheet = library.createSheet("Closing")
        val closeMayFinish = kotlinx.coroutines.CompletableDeferred<Unit>()
        var thumbnailWritten = false

        val closing = launch(Dispatchers.IO) {
            library.closeEditing(sheet.id) { dir ->
                closeMayFinish.await()
                // The editor still writes into the sheet's directory here.
                File(dir, SheetLibrary.THUMBNAIL_FILE_NAME).writeBytes(byteArrayOf(1, 2, 3))
                thumbnailWritten = true
            }
        }
        while (!closing.isActive) kotlinx.coroutines.delay(5)
        kotlinx.coroutines.delay(100)
        val deleting = launch(Dispatchers.IO) { library.delete(sheet.id) }
        kotlinx.coroutines.delay(200)
        assertTrue("the delete ran while the editor was still closing", sheet.dir.isDirectory && !deleting.isCompleted)

        closeMayFinish.complete(Unit)
        closing.join()
        deleting.join()

        assertTrue(thumbnailWritten)
        assertFalse(sheet.dir.exists())
        assertTrue(File(base, "trash/${sheet.id.value}/${SheetLibrary.THUMBNAIL_FILE_NAME}").isFile)
    }

    @Test
    fun explicitTitlesAreValidated(): Unit = runBlocking {
        val library = library()

        assertEquals("Plans", library.createSheet("  Plans ").title)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { library.createSheet("   ") } }
    }

    @Test
    fun renameUpdatesTheIndexAndTheSheetItself(): Unit = runBlocking {
        val library = library()
        val sheet = library.createSheet()

        library.rename(sheet.id, "Garden ideas")

        assertEquals("Garden ideas", library.sheets.first().single().title)
        assertEquals("Garden ideas", store.inspect(sheet.dir).meta.title)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { library.rename(sheet.id, "") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { library.rename(sheet.id, "x".repeat(81)) } }
    }

    @Test
    fun duplicateCopiesFiveThousandStrokesQuicklyWithANewIdentity(): Unit = runBlocking {
        val library = library()
        val original = library.createSheet()
        val sheet = store.open(original.dir)
        var last: FractionalIndex? = null
        val strokes = List(5_000) { i ->
            val z = FractionalIndex.between(last, null)
            last = z
            DeviceFixtures.stroke(z = z, pointCount = 30, seed = i)
        }
        sheet.transact("seed") { strokes.forEach { add(it) } }
        sheet.close()

        val startedAt = System.nanoTime()
        val copy = library.duplicate(original.id)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        Log.i("SheetLibraryTest", "Duplicated 5,000 strokes in $elapsedMillis ms")

        assertNotEquals(original.id, copy.id)
        assertEquals("Copy of Sheet 1", copy.title)
        assertEquals(5_000, copy.elementCount)
        assertTrue("duplicate took $elapsedMillis ms", elapsedMillis < PerfBudget.millis(2_000L))
        val reopened = store.open(copy.dir)
        try {
            assertEquals(copy.id, reopened.meta.value.id)
            assertEquals(strokes.associateBy { it.id }, reopened.snapshot.value.elements)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun deleteMovesToTrashRestoreBringsItBackAndPurgeFreesItAfterSevenDays(): Unit = runBlocking {
        val library = library()
        val kept = library.createSheet()
        val doomed = library.createSheet()
        library.markOpened(doomed.id)

        library.delete(doomed.id)

        assertEquals(listOf(kept.id), library.sheets.first().map { it.id })
        assertFalse(doomed.dir.exists())
        assertTrue(library.restore(doomed.id))
        assertTrue(doomed.dir.isDirectory)
        assertEquals(doomed.id, library.lastOpened.first()?.id)

        library.delete(doomed.id)
        val trashed = File(File(base, "trash"), doomed.id.value)
        assertTrue(trashed.isDirectory)
        library.reconcile()
        assertTrue("purged too early", trashed.isDirectory)
        now += 8L * 24 * 60 * 60 * 1000
        library.reconcile()
        assertFalse("trash should be purged after seven days", trashed.exists())
        assertFalse(library.restore(SheetId(doomed.id.value)) && doomed.dir.exists())
    }

    @Test
    fun reconcileAddsUnknownDirectoriesAndDropsMissingOnes(): Unit = runBlocking {
        val library = library()
        val vanished = library.createSheet()
        val strayId = SheetId(DeviceFixtures.ulid.next())
        val strayDir = File(File(base, "sheets"), strayId.value)
        store.create(strayDir, title = "Imported elsewhere", id = strayId).close()
        vanished.dir.deleteRecursively()

        library.reconcile()

        val sheets = library.sheets.first()
        assertEquals(listOf(strayId), sheets.map { it.id })
        assertEquals("Imported elsewhere", sheets.single().title)
    }
}
