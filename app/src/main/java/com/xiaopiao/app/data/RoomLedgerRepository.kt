package com.xiaopiao.app.data

import com.xiaopiao.app.domain.LedgerRepository
import com.xiaopiao.app.domain.Txn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLedgerRepository(private val dao: TxnDao) : LedgerRepository {
    override fun observeAll(): Flow<List<Txn>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeRange(fromInclusive: Long, toExclusive: Long): Flow<List<Txn>> =
        dao.observeRange(fromInclusive, toExclusive).map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(txn: Txn): Long? =
        dao.insert(txn.copy(id = 0).toEntity()).takeIf { it != -1L }

    override suspend fun update(txn: Txn) = dao.update(txn.toEntity())

    override suspend fun delete(id: Long): Txn? {
        val existing = dao.get(id)?.toDomain()
        if (existing != null) dao.delete(id)
        return existing
    }

    suspend fun clearAll() = dao.deleteAll()
}
