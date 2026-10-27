package com.xiaopiao.app.data

import com.xiaopiao.app.domain.LedgerRepository
import com.xiaopiao.app.domain.Txn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Placeholder until the Room implementation lands. */
class InMemoryLedgerRepository(seed: List<Txn> = emptyList()) : LedgerRepository {
    private val state = MutableStateFlow(seed.mapIndexed { i, t -> t.copy(id = i + 1L) })
    private var nextId = seed.size + 1L

    override fun observeAll(): Flow<List<Txn>> = state.map { list -> list.sortedByDescending { it.occurredAt } }

    override fun observeRange(fromInclusive: Long, toExclusive: Long): Flow<List<Txn>> =
        observeAll().map { list -> list.filter { it.occurredAt in fromInclusive until toExclusive } }

    override suspend fun add(txn: Txn): Long? {
        if (txn.dedupeKey.isNotEmpty() && state.value.any { it.dedupeKey == txn.dedupeKey }) return null
        val id = nextId++
        state.update { it + txn.copy(id = id) }
        return id
    }

    override suspend fun update(txn: Txn) = state.update { list -> list.map { if (it.id == txn.id) txn else it } }

    override suspend fun delete(id: Long): Txn? {
        val found = state.value.firstOrNull { it.id == id }
        state.update { list -> list.filterNot { it.id == id } }
        return found
    }
}
