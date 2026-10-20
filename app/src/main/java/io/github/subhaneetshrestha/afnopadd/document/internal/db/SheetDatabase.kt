package io.github.subhaneetshrestha.afnopadd.document.internal.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The per-sheet database (`sheets/<ulid>/sheet.db`). Version 1 is still unreleased, so tables are
 * added to it directly until the first sharing release freezes the schema; after that, every
 * change is a migration with a golden-file test.
 */
@Database(entities = [MetaEntity::class], version = 1, exportSchema = true)
abstract class SheetDatabase : RoomDatabase() {
    abstract fun metaDao(): MetaDao
}
