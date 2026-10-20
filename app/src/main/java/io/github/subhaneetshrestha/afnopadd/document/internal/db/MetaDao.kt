package io.github.subhaneetshrestha.afnopadd.document.internal.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MetaDao {
    @Query("SELECT meta_value FROM meta WHERE meta_key = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM meta")
    suspend fun getAll(): List<MetaEntity>

    @Upsert
    suspend fun upsert(entity: MetaEntity)

    @Upsert
    suspend fun upsertAll(entities: List<MetaEntity>)
}
