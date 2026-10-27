package com.xiaopiao.app.domain

import kotlinx.coroutines.flow.Flow

interface LedgerRepository {
    fun observeAll(): Flow<List<Txn>>
    fun observeRange(fromInclusive: Long, toExclusive: Long): Flow<List<Txn>>

    /** Inserts unless a txn with the same non-empty dedupeKey exists. Returns new id, or null when deduped. */
    suspend fun add(txn: Txn): Long?
    suspend fun update(txn: Txn)
    suspend fun delete(id: Long): Txn?
}
