package com.xiaopiao.app.data

import com.xiaopiao.app.domain.LedgerRepository
import com.xiaopiao.app.domain.Txn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Every write stamps `updatedAt` and marks the row dirty; deletes leave a tombstone, so the account sync
 * (docs/API-SYNC.md) can replay them. Nothing here talks to the network.
 */
class RoomLedgerRepository(
    private val dao: TxnDao,
    private val sync: SyncDao,
    private val transaction: suspend (suspend () -> Unit) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) : LedgerRepository {
    override fun observeAll(): Flow<List<Txn>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeRange(fromInclusive: Long, toExclusive: Long): Flow<List<Txn>> =
        dao.observeRange(fromInclusive, toExclusive).map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(txn: Txn): Long? =
        dao.insert(
            txn.copy(id = 0).toEntity().copy(syncId = SyncCodec.newSyncId(), updatedAt = clock(), dirty = true),
        ).takeIf { it != -1L }

    override suspend fun update(txn: Txn) {
        val old = dao.get(txn.id) ?: return
        dao.update(txn.toEntity().copy(syncId = old.syncId, updatedAt = after(old.updatedAt), dirty = true))
    }

    override suspend fun delete(id: Long): Txn? {
        val existing = dao.get(id) ?: return null
        transaction {
            dao.delete(id)
            if (existing.dedupeKey?.startsWith("demo|") != true) {
                sync.putTombstones(listOf(SyncCodec.tombstone(existing, after(existing.updatedAt))))
            }
        }
        return existing.toDomain()
    }

    /**
     * An edit must beat the version it was made on, even when this phone's clock runs behind the device
     * that wrote that version — otherwise the server would call the edit stale.
     */
    private fun after(seen: Long) = maxOf(clock(), seen + 1)

    /** 「清空我的全部数据」 means everywhere: synced rows go out as tombstones. */
    suspend fun clearAll() = transaction {
        sync.putTombstones(dao.syncable().map { SyncCodec.tombstone(it, after(it.updatedAt)) })
        dao.deleteAll()
    }
}
