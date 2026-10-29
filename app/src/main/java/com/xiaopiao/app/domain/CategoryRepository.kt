package com.xiaopiao.app.domain

import kotlinx.coroutines.flow.Flow

/**
 * The one source for category names, icons and colours (tt#70/71). Every screen — ledger, record
 * sheet, stats, receipt — resolves through this so a recoloured or renamed category changes in one
 * place. Rows are ordered by `sortOrder`; hidden ones are excluded from pickers but still resolve.
 */
interface CategoryRepository {
    fun observeDefs(): Flow<List<CategoryDef>>

    suspend fun defs(): List<CategoryDef>

    /** Creates a custom category; returns the row that was written. */
    suspend fun create(
        label: String,
        iconKey: String,
        hue: CategoryHue,
        direction: Direction?,
        now: Long = System.currentTimeMillis(),
    ): CategoryDef

    suspend fun update(def: CategoryDef)

    /** Reorders everything to the given id order (drag & drop writes the whole list once). */
    suspend fun reorder(idsInOrder: List<String>)

    suspend fun setHidden(id: String, hidden: Boolean)

    /** How many rows point at [id]; the delete confirm needs it to promise 「归到 其他」 honestly. */
    suspend fun countTxns(id: String): Int

    /** Deletes a custom category and folds its rows into 其他. Built-ins are refused at the call site. */
    suspend fun deleteCustom(id: String, mergeInto: String = CategoryDefaults.OTHER_ID)
}
