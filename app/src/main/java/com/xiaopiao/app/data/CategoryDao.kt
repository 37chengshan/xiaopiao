package com.xiaopiao.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, label ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, label ASC")
    suspend fun all(): List<CategoryEntity>

    /** Seeding never overwrites a row the user may have edited. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entities: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOne(entity: CategoryEntity): Long

    @Update
    suspend fun update(entity: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories WHERE id = :id")
    suspend fun count(id: String): Int

    @Query("DELETE FROM categories WHERE id = :id AND builtIn = 0")
    suspend fun deleteCustom(id: String)

    @Query("UPDATE categories SET sortOrder = :order WHERE id = :id")
    suspend fun setOrder(id: String, order: Int)

    @Query("UPDATE categories SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: String, hidden: Boolean)

    /** Deleting a custom category folds its rows into the target (其他) instead of losing them. */
    @Query("UPDATE txns SET categoryId = :to WHERE categoryId = :from")
    suspend fun remapTxns(from: String, to: String)

    @Query("SELECT COUNT(*) FROM txns WHERE categoryId = :id")
    suspend fun countTxns(id: String): Int
}
