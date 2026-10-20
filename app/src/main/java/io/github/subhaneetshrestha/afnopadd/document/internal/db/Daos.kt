package io.github.subhaneetshrestha.afnopadd.document.internal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ElementDao {
    @Query("SELECT * FROM element WHERE deleted = 0")
    suspend fun getLive(): List<ElementEntity>

    @Upsert
    suspend fun upsertAll(entities: List<ElementEntity>)
}

@Dao
interface StrokePointsDao {
    @Query("SELECT * FROM stroke_points")
    suspend fun getAll(): List<StrokePointsEntity>

    @Upsert
    suspend fun upsertAll(entities: List<StrokePointsEntity>)
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM asset")
    suspend fun getAll(): List<AssetEntity>

    @Upsert
    suspend fun upsert(entity: AssetEntity)
}

@Dao
interface OpDao {
    @Insert
    suspend fun insertAll(entities: List<OpEntity>)

    @Query("SELECT COUNT(*) FROM op")
    suspend fun count(): Long
}
