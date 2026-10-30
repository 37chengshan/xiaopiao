package com.xiaopiao.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TxnDao {
    @Query("SELECT * FROM txns ORDER BY occurredAt DESC, id DESC")
    fun observeAll(): Flow<List<TxnEntity>>

    @Query("SELECT * FROM txns WHERE occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt DESC, id DESC")
    fun observeRange(from: Long, to: Long): Flow<List<TxnEntity>>

    /** Returns -1 when the unique dedupeKey index rejects the row. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TxnEntity): Long

    @Update
    suspend fun update(entity: TxnEntity)

    @Query("SELECT * FROM txns WHERE id = :id")
    suspend fun get(id: Long): TxnEntity?

    @Query("DELETE FROM txns WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM txns")
    suspend fun deleteAll()

    @Query("SELECT * FROM txns WHERE dedupeKey IS NULL OR dedupeKey NOT LIKE 'demo|%'")
    suspend fun syncable(): List<TxnEntity>
}

/** Account sync bookkeeping (docs/API-SYNC.md). Demo rows (`dedupeKey` `demo|…`) never leave the device. */
@Dao
interface SyncDao {
    @Query("SELECT * FROM txns WHERE dirty = 1 AND (dedupeKey IS NULL OR dedupeKey NOT LIKE 'demo|%')")
    suspend fun dirtyTxns(): List<TxnEntity>

    @Query("SELECT * FROM categories WHERE dirty = 1")
    suspend fun dirtyCategories(): List<CategoryEntity>

    @Query("SELECT * FROM sync_tombstones")
    suspend fun tombstones(): List<TombstoneEntity>

    /** Fires whenever there is something to push, so a write is synced within seconds. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM txns WHERE dirty = 1 AND (dedupeKey IS NULL OR dedupeKey NOT LIKE 'demo|%'))" +
            " + (SELECT COUNT(*) FROM categories WHERE dirty = 1) + (SELECT COUNT(*) FROM sync_tombstones)",
    )
    fun observePending(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTombstones(rows: List<TombstoneEntity>)

    /** The `updatedAt` guard keeps a row dirty when it was edited again while the push was in flight. */
    @Query("UPDATE txns SET dirty = 0 WHERE syncId = :syncId AND updatedAt = :updatedAt")
    suspend fun txnPushed(syncId: String, updatedAt: Long)

    @Query("UPDATE categories SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun categoryPushed(id: String, updatedAt: Long)

    @Query("DELETE FROM sync_tombstones WHERE kind = :kind AND syncId = :syncId AND updatedAt = :updatedAt")
    suspend fun tombstonePushed(kind: String, syncId: String, updatedAt: Long)

    @Query("SELECT * FROM txns WHERE syncId = :syncId")
    suspend fun txnBySyncId(syncId: String): TxnEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTxn(entity: TxnEntity): Long

    @Update
    suspend fun updateTxn(entity: TxnEntity)

    @Query("DELETE FROM txns WHERE syncId = :syncId")
    suspend fun deleteTxn(syncId: String)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun category(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCategory(entity: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id AND builtIn = 0")
    suspend fun deleteCategory(id: String)

    /** A different account signed in: everything this device has becomes that account's to upload. */
    @Query("UPDATE txns SET dirty = 1")
    suspend fun markAllTxnsDirty()

    @Query("UPDATE categories SET dirty = 1 WHERE updatedAt > 0")
    suspend fun markTouchedCategoriesDirty()
}

@Dao
interface CaptureLogDao {
    @Query("SELECT * FROM capture_log ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CaptureLogEntity>>

    @Insert
    suspend fun insert(entity: CaptureLogEntity): Long

    @Query("DELETE FROM capture_log WHERE id NOT IN (SELECT id FROM capture_log ORDER BY id DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("DELETE FROM capture_log")
    suspend fun deleteAll()
}
