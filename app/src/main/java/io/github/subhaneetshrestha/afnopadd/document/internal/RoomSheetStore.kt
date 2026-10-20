package io.github.subhaneetshrestha.afnopadd.document.internal

import android.content.Context
import io.github.subhaneetshrestha.afnopadd.document.CommittedTransaction
import io.github.subhaneetshrestha.afnopadd.document.Element
import io.github.subhaneetshrestha.afnopadd.document.ElementId
import io.github.subhaneetshrestha.afnopadd.document.ImageElement
import io.github.subhaneetshrestha.afnopadd.document.SHEET_SCHEMA_VERSION
import io.github.subhaneetshrestha.afnopadd.document.Sheet
import io.github.subhaneetshrestha.afnopadd.document.SheetFormatException
import io.github.subhaneetshrestha.afnopadd.document.SheetId
import io.github.subhaneetshrestha.afnopadd.document.SheetInfo
import io.github.subhaneetshrestha.afnopadd.document.SheetMeta
import io.github.subhaneetshrestha.afnopadd.document.SheetStore
import io.github.subhaneetshrestha.afnopadd.document.StrokePoints
import io.github.subhaneetshrestha.afnopadd.document.Ulid
import io.github.subhaneetshrestha.afnopadd.document.internal.db.MetaEntity
import io.github.subhaneetshrestha.afnopadd.document.internal.db.SheetDatabase
import io.github.subhaneetshrestha.afnopadd.document.internal.db.openSheetDatabase
import io.github.subhaneetshrestha.afnopadd.document.internal.db.toElement
import io.github.subhaneetshrestha.afnopadd.document.internal.json.ElementFormatException
import io.github.subhaneetshrestha.afnopadd.document.internal.json.ElementJsonCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File

/** [SheetStore] backed by one Room database per sheet directory. */
internal class RoomSheetStore(
    private val context: Context,
    private val ulid: Ulid = Ulid.Default,
    private val clock: () -> Long = System::currentTimeMillis,
    private val codec: ElementJsonCodec = ElementJsonCodec(),
    /** Test seam: runs inside each database transaction before it commits. */
    private val beforeCommit: suspend (CommittedTransaction) -> Unit = {},
) : SheetStore {

    override suspend fun create(dir: File, title: String, id: SheetId?): Sheet {
        val file = File(dir, SheetStore.DATABASE_FILE_NAME)
        require(!file.exists()) { "A sheet already exists at $file" }
        check(dir.isDirectory || dir.mkdirs()) { "Could not create sheet directory $dir" }
        val database = openSheetDatabase(context, file)
        try {
            val now = clock()
            val meta = SheetMeta(id = id ?: SheetId(ulid.next()), title = title, createdAt = now, updatedAt = now)
            database.metaDao().upsertAll(
                listOf(
                    MetaEntity(META_SCHEMA_VERSION, meta.schemaVersion.toString()),
                    MetaEntity(META_SHEET_ID, meta.id.value),
                    MetaEntity(META_TITLE, meta.title),
                    MetaEntity(META_CREATED_AT, meta.createdAt.toString()),
                    MetaEntity(META_UPDATED_AT, meta.updatedAt.toString()),
                ),
            )
            return newSheet(meta, emptyList(), database, dir)
        } catch (e: Throwable) {
            database.close()
            throw e
        }
    }

    override suspend fun open(dir: File): Sheet {
        val file = File(dir, SheetStore.DATABASE_FILE_NAME)
        if (!file.isFile) throw SheetFormatException("No sheet database at $file")
        val database = openSheetDatabase(context, file)
        try {
            val meta = readMeta(database)
            val pointEntities = database.strokePointsDao().getAll()
            val elementEntities = database.elementDao().getLive()
            // Decoding dominates open time for large sheets, so it is spread across cores in chunks.
            val elements = withContext(Dispatchers.Default) {
                val points = pointEntities.chunked(DECODE_CHUNK_SIZE).map { chunk ->
                    async { chunk.map { ElementId(it.elementId) to decodePoints(it.elementId, it.blob) } }
                }.awaitAll().flatten().toMap()
                elementEntities.chunked(DECODE_CHUNK_SIZE).map { chunk ->
                    async {
                        chunk.map { entity ->
                            try {
                                entity.toElement(codec) { id -> points[id] ?: StrokePoints.EMPTY }
                            } catch (e: ElementFormatException) {
                                throw SheetFormatException("Sheet ${meta.id.value} has a corrupt element ${entity.id}", e)
                            }
                        }
                    }
                }.awaitAll().flatten()
            }
            return newSheet(meta, elements, database, dir)
        } catch (e: Throwable) {
            database.close()
            throw e
        }
    }

    override suspend fun inspect(dir: File): SheetInfo {
        val file = File(dir, SheetStore.DATABASE_FILE_NAME)
        if (!file.isFile) throw SheetFormatException("No sheet database at $file")
        val database = openSheetDatabase(context, file)
        try {
            return SheetInfo(readMeta(database), database.elementDao().countLive().toInt())
        } finally {
            database.close()
        }
    }

    override suspend fun duplicate(source: File, target: File, id: SheetId, title: String): SheetMeta {
        if (!File(source, SheetStore.DATABASE_FILE_NAME).isFile) throw SheetFormatException("No sheet database in $source")
        require(!target.exists()) { "Duplicate target $target already exists" }
        try {
            withContext(Dispatchers.IO) { source.copyRecursively(target) }
            val database = openSheetDatabase(context, File(target, SheetStore.DATABASE_FILE_NAME))
            try {
                val now = clock().toString()
                database.metaDao().upsertAll(
                    listOf(
                        MetaEntity(META_SHEET_ID, id.value),
                        MetaEntity(META_TITLE, title),
                        MetaEntity(META_CREATED_AT, now),
                        MetaEntity(META_UPDATED_AT, now),
                    ),
                )
                return readMeta(database)
            } finally {
                database.close()
            }
        } catch (e: Throwable) {
            target.deleteRecursively()
            throw e
        }
    }

    private fun newSheet(meta: SheetMeta, elements: List<Element>, database: SheetDatabase, dir: File): Sheet {
        val writer = RoomSheetWriter(database, codec, beforeCommit)
        val assets = FileAssetStore(File(dir, ASSETS_DIRECTORY), RoomAssetRegistry(database.assetDao()), clock)
        return SheetImpl(
            meta = meta,
            initialElements = elements,
            writer = writer,
            ulid = ulid,
            clock = clock,
            assets = assets,
            onFlush = { writer.flush() },
            onClose = { snapshot ->
                try {
                    writer.close()
                } finally {
                    try {
                        database.sheetWriteDao().compactOps(OPS_TO_KEEP)
                        database.sheetWriteDao().purgeTombstones()
                        val referenced = snapshot.elements.values.filterIsInstance<ImageElement>().map { it.asset }.toSet()
                        assets.collectGarbage(referenced)
                    } finally {
                        database.close()
                    }
                }
            },
        )
    }

    private suspend fun readMeta(database: SheetDatabase): SheetMeta {
        val values = database.metaDao().getAll().associate { it.key to it.value }
        fun required(key: String): String = values[key] ?: throw SheetFormatException("Sheet metadata is missing '$key'")
        val schemaVersion = required(META_SCHEMA_VERSION).toIntOrNull()
            ?: throw SheetFormatException("Sheet schema version '${values[META_SCHEMA_VERSION]}' is not a number")
        if (schemaVersion > SHEET_SCHEMA_VERSION) {
            throw SheetFormatException(
                "Sheet schema version $schemaVersion is newer than this app supports ($SHEET_SCHEMA_VERSION)",
            )
        }
        return try {
            SheetMeta(
                id = SheetId(required(META_SHEET_ID)),
                title = required(META_TITLE),
                createdAt = required(META_CREATED_AT).toLong(),
                updatedAt = required(META_UPDATED_AT).toLong(),
                schemaVersion = schemaVersion,
            )
        } catch (e: IllegalArgumentException) {
            throw SheetFormatException("Sheet metadata is corrupt: ${e.message}", e)
        }
    }

    private fun decodePoints(elementId: String, blob: ByteArray): StrokePoints = try {
        StrokePoints.decode(blob)
    } catch (e: IllegalArgumentException) {
        throw SheetFormatException("Stroke points for element $elementId are corrupt", e)
    }

    internal companion object {
        const val ASSETS_DIRECTORY = "assets"
        const val OPS_TO_KEEP = 1_000
        const val DECODE_CHUNK_SIZE = 256
        const val META_SCHEMA_VERSION = "schema_version"
        const val META_SHEET_ID = "sheet_id"
        const val META_TITLE = "title"
        const val META_CREATED_AT = "created_at"
        const val META_UPDATED_AT = "updated_at"
    }
}
