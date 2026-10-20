package io.github.subhaneetshrestha.afnopadd.library.internal

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import java.io.File

@Entity(tableName = "sheet")
data class SheetEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "dir_name") val dirName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
    @ColumnInfo(name = "thumb_path") val thumbPath: String?,
    @ColumnInfo(name = "element_count") val elementCount: Int,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM sheet ORDER BY last_opened_at IS NULL, last_opened_at DESC, updated_at DESC")
    fun observeAll(): Flow<List<SheetEntity>>

    @Query("SELECT * FROM sheet WHERE last_opened_at IS NOT NULL ORDER BY last_opened_at DESC LIMIT 1")
    fun observeLastOpened(): Flow<SheetEntity?>

    @Query("SELECT * FROM sheet")
    suspend fun getAll(): List<SheetEntity>

    @Query("SELECT * FROM sheet WHERE id = :id")
    suspend fun get(id: String): SheetEntity?

    @Upsert
    suspend fun upsert(entity: SheetEntity)

    @Query("DELETE FROM sheet WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE sheet SET last_opened_at = :openedAt WHERE id = :id")
    suspend fun markOpened(id: String, openedAt: Long)
}

/** `files/library.db`: the index of all sheets. Losing it loses nothing; [RoomSheetLibrary.reconcile] rebuilds it. */
@Database(entities = [SheetEntity::class], version = 1, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}

internal fun openLibraryDatabase(context: Context, file: File): LibraryDatabase =
    Room.databaseBuilder(context, LibraryDatabase::class.java, file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
