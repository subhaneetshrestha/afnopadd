package io.github.subhaneetshrestha.afnopadd.document.internal.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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
}

/** Opens (creating if needed) a sheet database with the bundled SQLite in write-ahead-logging mode. */
internal fun openSheetDatabase(context: Context, file: File): SheetDatabase =
    Room.databaseBuilder(context, SheetDatabase::class.java, file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
