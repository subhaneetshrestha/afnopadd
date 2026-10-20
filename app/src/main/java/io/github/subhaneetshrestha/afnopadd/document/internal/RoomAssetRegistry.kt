package io.github.subhaneetshrestha.afnopadd.document.internal

import io.github.subhaneetshrestha.afnopadd.document.AssetId
import io.github.subhaneetshrestha.afnopadd.document.internal.db.AssetDao
import io.github.subhaneetshrestha.afnopadd.document.internal.db.AssetEntity

internal class RoomAssetRegistry(private val dao: AssetDao) : AssetRegistry {

    override suspend fun register(record: AssetRecord) = dao.upsert(
        AssetEntity(
            id = record.id.sha256Hex,
            mime = record.mime,
            width = record.width,
            height = record.height,
            byteSize = record.byteSize,
            createdAt = record.createdAt,
        ),
    )

    override suspend fun all(): List<AssetRecord> = dao.getAll().map {
        AssetRecord(AssetId(it.id), it.mime, it.width, it.height, it.byteSize, it.createdAt)
    }

    override suspend fun remove(ids: Collection<AssetId>) {
        if (ids.isNotEmpty()) dao.deleteByIds(ids.map { it.sha256Hex })
    }
}
