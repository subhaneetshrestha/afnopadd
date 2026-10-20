package io.github.subhaneetshrestha.afnopadd.document.internal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface ElementDao {
    @Query("SELECT * FROM element WHERE deleted = 0")
    suspend fun getLive(): List<ElementEntity>

    @Query("SELECT COUNT(*) FROM element WHERE deleted = 1")
    suspend fun countTombstones(): Long

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

    @Query("DELETE FROM asset WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface OpDao {
    @Insert
    suspend fun insertAll(entities: List<OpEntity>)

    @Query("SELECT COUNT(*) FROM op")
    suspend fun count(): Long

    @Query("SELECT * FROM op ORDER BY seq")
    suspend fun getAll(): List<OpEntity>
}

/** Multi-table writes that must be atomic: one committed gesture, and the clean-up that runs on close. */
@Dao
abstract class SheetWriteDao(private val database: SheetDatabase) {

    /**
     * Applies one committed transaction. [beforeCommit] runs inside the database transaction so
     * tests can inject a failure and prove the whole write rolls back.
     */
    @Transaction
    open suspend fun write(
        elements: List<ElementEntity>,
        points: List<StrokePointsEntity>,
        ops: List<OpEntity>,
        meta: List<MetaEntity>,
        beforeCommit: suspend () -> Unit,
    ) {
        database.elementDao().upsertAll(elements)
        database.strokePointsDao().upsertAll(points)
        database.opDao().insertAll(ops)
        database.metaDao().upsertAll(meta)
        beforeCommit()
    }

    /** Keeps only the newest [keep] op rows. */
    @Query("DELETE FROM op WHERE seq <= (SELECT seq FROM op ORDER BY seq DESC LIMIT 1 OFFSET :keep)")
    abstract suspend fun compactOps(keep: Int)

    /** Removes tombstoned elements; their stroke points go with them through the foreign key. */
    @Query("DELETE FROM element WHERE deleted = 1")
    abstract suspend fun purgeTombstones()
}
