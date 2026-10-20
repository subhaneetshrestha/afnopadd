package io.github.subhaneetshrestha.afnopadd.document.internal.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MetaDao {
    @Query("SELECT meta_value FROM meta WHERE meta_key = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun upsert(entity: MetaEntity)
}
