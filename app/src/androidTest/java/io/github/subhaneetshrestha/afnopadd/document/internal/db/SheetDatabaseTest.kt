package io.github.subhaneetshrestha.afnopadd.document.internal.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Proves Room, KSP code generation, and the bundled SQLite driver work together on a device.
 * The meta table is the first real table of the per-sheet database.
 */
@RunWith(AndroidJUnit4::class)
class SheetDatabaseTest {

    private lateinit var dbFile: File
    private lateinit var database: SheetDatabase

    @Before
    fun openDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        dbFile = File(context.cacheDir, "sheet-database-test-${System.nanoTime()}.db")
        database = Room.databaseBuilder(context, SheetDatabase::class.java, dbFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
        dbFile.delete()
    }

    @Test
    fun metaValueRoundTripsThroughBundledSqlite() = runBlocking {
        database.metaDao().upsert(MetaEntity(key = "schema_version", value = "1"))

        assertEquals("1", database.metaDao().get("schema_version"))
    }

    @Test
    fun missingMetaKeyReturnsNull() = runBlocking {
        assertNull(database.metaDao().get("absent"))
    }
}
