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
