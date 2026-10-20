package io.github.subhaneetshrestha.afnopadd.document.internal

import io.github.subhaneetshrestha.afnopadd.document.AssetId

internal data class AssetRecord(
    val id: AssetId,
    val mime: String,
    val width: Int,
    val height: Int,
    val byteSize: Long,
    val createdAt: Long,
)

/** Where asset metadata lives (the `asset` table in production, memory in tests); files live in [FileAssetStore]. */
internal interface AssetRegistry {
    suspend fun register(record: AssetRecord)
    suspend fun all(): List<AssetRecord>
    suspend fun remove(ids: Collection<AssetId>)
}
