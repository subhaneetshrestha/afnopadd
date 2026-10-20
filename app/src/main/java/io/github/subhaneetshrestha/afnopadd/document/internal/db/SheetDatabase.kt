package io.github.subhaneetshrestha.afnopadd.document.internal.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * The per-sheet database (`sheets/<ulid>/sheet.db`). Version 1 is still unreleased, so tables are
 * added to it directly until the first sharing release freezes the schema; after that, every
 * change is a migration with a golden-file test.
 */
@Database(
    entities = [MetaEntity::class, ElementEntity::class, StrokePointsEntity::class, AssetEntity::class, OpEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SheetDatabase : RoomDatabase() {
    abstract fun metaDao(): MetaDao
    abstract fun elementDao(): ElementDao
    abstract fun strokePointsDao(): StrokePointsDao
    abstract fun assetDao(): AssetDao
    abstract fun opDao(): OpDao
    abstract fun sheetWriteDao(): SheetWriteDao
}

/**
 * Opens (creating if needed) a sheet database with the bundled SQLite in write-ahead-logging mode.
 * `synchronous=NORMAL` is the standard WAL setting: every committed gesture survives an app crash,
 * and only a power loss could lose the last few, which the spec accepts.
 */
internal fun openSheetDatabase(context: Context, file: File): SheetDatabase =
    Room.databaseBuilder(context, SheetDatabase::class.java, file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(
            object : RoomDatabase.Callback() {
                override fun onOpen(connection: SQLiteConnection) {
                    connection.execSQL("PRAGMA synchronous=NORMAL")
                }
            },
        )
        .build()
