package io.github.subhaneetshrestha.afnopadd.library.internal

import android.util.Log
import io.github.subhaneetshrestha.afnopadd.document.SheetFormatException
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetStore
import io.github.subhaneetshrestha.afnopadd.document.Ulid
import io.github.subhaneetshrestha.afnopadd.library.SheetLibrary
import io.github.subhaneetshrestha.afnopadd.library.SheetSummary
import io.github.subhaneetshrestha.afnopadd.library.SheetTitles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [SheetLibrary] over a Room index. Every sheet lives in `root/<sheet id>/`; deleting moves the
 * directory to `trash/<sheet id>/` with a timestamp marker so it can be restored and later purged.
 * Mutations are serialised so the index and the directories never race each other.
 */
internal class RoomSheetLibrary(
    private val store: SheetStore,
    database: LibraryDatabase,
    private val root: File,
    private val trash: File,
    private val defaultTitlePrefix: String = "Sheet",
    private val copyTitle: (String) -> String = { "Copy of $it" },
    private val clock: () -> Long = System::currentTimeMillis,
    private val ulid: Ulid = Ulid.Default,
) : SheetLibrary {

    private val dao = database.libraryDao()
    private val mutex = Mutex()
    private val trashed = HashMap<String, SheetEntity>()

    override val sheets: Flow<List<SheetSummary>> = dao.observeAll().map { rows -> rows.map { it.toSummary() } }

    override val lastOpened: Flow<SheetSummary?> = dao.observeLastOpened().map { it?.toSummary() }

    override suspend fun createSheet(title: String?): SheetSummary = mutex.withLock {
        val resolvedTitle = if (title != null) {
            SheetTitles.normalize(title) ?: throw IllegalArgumentException("Invalid sheet title '$title'")
        } else {
            SheetTitles.nextDefault(defaultTitlePrefix, dao.getAll().map { it.title })
        }
        val id = SheetId(ulid.next())
        val dir = File(root, id.value)
        val sheet = store.create(dir, resolvedTitle, id)
        val meta = sheet.meta.value
        sheet.close()
        val entity = SheetEntity(
            id = id.value,
            title = meta.title,
            dirName = dir.name,
            createdAt = meta.createdAt,
            updatedAt = meta.updatedAt,
            lastOpenedAt = null,
            thumbPath = null,
            elementCount = 0,
        )
        dao.upsert(entity)
        entity.toSummary()
    }

    override suspend fun markOpened(id: SheetId) = mutex.withLock { dao.markOpened(id.value, clock()) }

    override suspend fun rename(id: SheetId, title: String) = mutex.withLock {
        val normalized = SheetTitles.normalize(title) ?: throw IllegalArgumentException("Invalid sheet title '$title'")
        val row = requireRow(id)
        val sheet = store.open(File(root, row.dirName))
        val updatedAt = try {
            sheet.transact("Rename sheet") { setTitle(normalized) }
            sheet.meta.value.updatedAt
        } finally {
            sheet.close()
        }
        dao.upsert(row.copy(title = normalized, updatedAt = updatedAt))
    }

    override suspend fun duplicate(id: SheetId): SheetSummary = mutex.withLock {
        val row = requireRow(id)
        val copyId = SheetId(ulid.next())
        val target = File(root, copyId.value)
        val meta = store.duplicate(File(root, row.dirName), target, copyId, copyTitle(row.title))
        // The index row may be stale (the editor changes sheets without telling the library), so count the copy itself.
        val elementCount = store.inspect(target).elementCount
        val entity = SheetEntity(
            id = copyId.value,
            title = meta.title,
            dirName = target.name,
            createdAt = meta.createdAt,
            updatedAt = meta.updatedAt,
            lastOpenedAt = null,
            thumbPath = thumbPathFor(target.name),
            elementCount = elementCount,
        )
        dao.upsert(entity)
        entity.toSummary()
    }

    override suspend fun delete(id: SheetId) = mutex.withLock {
        val row = dao.get(id.value) ?: return@withLock
        val target = File(trash, row.dirName)
        withContext(Dispatchers.IO) {
            check(trash.isDirectory || trash.mkdirs()) { "Could not create trash directory $trash" }
            if (target.exists()) target.deleteRecursively()
            if (!File(root, row.dirName).renameTo(target)) throw IOException("Could not move sheet ${row.id} to the trash")
            File(target, TRASHED_AT_MARKER).writeText(clock().toString())
        }
        dao.delete(row.id)
        trashed[row.id] = row
    }

    override suspend fun restore(id: SheetId): Boolean = mutex.withLock {
        val row = trashed.remove(id.value) ?: return@withLock false
        val source = File(trash, row.dirName)
        val restored = withContext(Dispatchers.IO) {
            if (!source.isDirectory) return@withContext false
            File(source, TRASHED_AT_MARKER).delete()
            source.renameTo(File(root, row.dirName))
        }
        if (restored) dao.upsert(row)
        restored
    }

    override suspend fun refresh(id: SheetId) = mutex.withLock {
        val row = dao.get(id.value) ?: return@withLock
        val info = store.inspect(File(root, row.dirName))
        dao.upsert(row.copy(title = info.meta.title, updatedAt = info.meta.updatedAt, elementCount = info.elementCount, thumbPath = thumbPathFor(row.dirName)))
    }

    override suspend fun register(dir: File): SheetSummary = mutex.withLock { registerLocked(dir) }

    override suspend fun reconcile(): Unit = mutex.withLock {
        val rows = dao.getAll()
        val dirs = withContext(Dispatchers.IO) {
            root.listFiles { file -> file.isDirectory && File(file, SheetStore.DATABASE_FILE_NAME).isFile }.orEmpty().toList()
        }
        val present = dirs.map { it.name }.toSet()
        rows.filter { it.dirName !in present }.forEach { dao.delete(it.id) }
        val known = rows.map { it.dirName }.toSet()
        for (dir in dirs.filter { it.name !in known }) {
            try {
                registerLocked(dir)
            } catch (e: SheetFormatException) {
                Log.w(TAG, "Skipping unreadable sheet directory ${dir.name}", e)
            }
        }
        purgeTrash()
    }

    private suspend fun registerLocked(dir: File): SheetSummary {
        require(dir.canonicalFile.parentFile == root.canonicalFile) { "$dir is not inside the library root $root" }
        val info = store.inspect(dir)
        val existing = dao.get(info.meta.id.value)
        val entity = SheetEntity(
            id = info.meta.id.value,
            title = info.meta.title,
            dirName = dir.name,
            createdAt = info.meta.createdAt,
            updatedAt = info.meta.updatedAt,
            lastOpenedAt = existing?.lastOpenedAt,
            thumbPath = thumbPathFor(dir.name),
            elementCount = info.elementCount,
        )
        dao.upsert(entity)
        return entity.toSummary()
    }

    private suspend fun purgeTrash(): Unit = withContext(Dispatchers.IO) {
        val cutoff = clock() - TRASH_RETENTION_MILLIS
        trash.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val trashedAt = File(dir, TRASHED_AT_MARKER).takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull() ?: 0L
            if (trashedAt < cutoff) {
                dir.deleteRecursively()
                trashed.remove(dir.name)
            }
        }
    }

    /** Relative path of the sheet's thumbnail if one has been written. */
    private fun thumbPathFor(dirName: String): String? =
        "$dirName/${SheetLibrary.THUMBNAIL_FILE_NAME}".takeIf { File(root, it).isFile }

    private suspend fun requireRow(id: SheetId): SheetEntity =
        dao.get(id.value) ?: throw IllegalArgumentException("No sheet ${id.value} in the library")

    private fun SheetEntity.toSummary() = SheetSummary(
        id = SheetId(id),
        title = title,
        dir = File(root, dirName),
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
        thumbnail = thumbPath?.let { File(root, it) }?.takeIf { it.isFile },
        elementCount = elementCount,
    )

    private companion object {
        const val TAG = "SheetLibrary"
        const val TRASHED_AT_MARKER = ".trashed-at"
        const val TRASH_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
