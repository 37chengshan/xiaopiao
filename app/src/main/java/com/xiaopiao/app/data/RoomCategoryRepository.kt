package com.xiaopiao.app.data

import com.xiaopiao.app.domain.CategoryDefaults
import com.xiaopiao.app.domain.CategoryDef
import com.xiaopiao.app.domain.CategoryHue
import com.xiaopiao.app.domain.CategoryRepository
import com.xiaopiao.app.domain.Direction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Room-backed categories. Built-ins are seeded once by the v3 migration; [seedMissing] heals a row that
 * an older install lost, without ever overwriting a label the user changed.
 */
class RoomCategoryRepository(
    private val dao: CategoryDao,
    /** Runs [block] in one transaction; injected so unit tests can pass `{ block -> block() }`. */
    private val transaction: suspend (suspend () -> Unit) -> Unit,
) : CategoryRepository {

    override fun observeDefs(): Flow<List<CategoryDef>> =
        dao.observeAll().onStart { seedMissing() }.map { rows -> rows.map { it.toDomain() } }

    override suspend fun defs(): List<CategoryDef> {
        seedMissing()
        return dao.all().map { it.toDomain() }
    }

    private suspend fun seedMissing() {
        val existing = dao.all().map { it.id }.toSet()
        val missing = CategoryDefaults.all.filterNot { it.id in existing }
        if (missing.isNotEmpty()) dao.insertIgnore(missing.map { it.toEntity() })
    }

    override suspend fun create(
        label: String,
        iconKey: String,
        hue: CategoryHue,
        direction: Direction?,
        now: Long,
    ): CategoryDef {
        seedMissing()
        val order = (dao.all().maxOfOrNull { it.sortOrder } ?: -1) + 1
        var def = CategoryDefaults.custom(
            label = label, iconKey = iconKey, hue = hue, direction = direction, now = now, sortOrder = order,
        )
        // Two creations inside the same millisecond would otherwise fight over the primary key.
        var bump = 0
        while (dao.count(def.id) > 0) {
            bump += 1
            def = def.copy(id = CategoryDefaults.CUSTOM_PREFIX + (now + bump))
        }
        dao.insertOne(def.toEntity())
        return def
    }

    override suspend fun update(def: CategoryDef) {
        seedMissing()
        if (!def.builtIn) {
            dao.update(def.copy(label = def.label.trim().take(CategoryDefaults.MAX_LABEL)).toEntity())
        } else {
            // Built-in rows keep their name / icon / colour: only visibility belongs to the user.
            dao.setHidden(def.id, def.hidden)
        }
    }

    override suspend fun reorder(idsInOrder: List<String>) {
        seedMissing()
        transaction {
            idsInOrder.forEachIndexed { index, id -> dao.setOrder(id, index) }
        }
    }

    override suspend fun setHidden(id: String, hidden: Boolean) {
        seedMissing()
        dao.setHidden(id, hidden)
    }

    override suspend fun countTxns(id: String): Int = dao.countTxns(id)

    override suspend fun deleteCustom(id: String, mergeInto: String) {
        seedMissing()
        transaction {
            dao.remapTxns(id, mergeInto)
            dao.deleteCustom(id)
        }
    }
}

/** In-memory categories, for previews and unit tests (no Android, no Room). */
class InMemoryCategoryRepository(initial: List<CategoryDef> = CategoryDefaults.all) : CategoryRepository {
    private val rows = initial.toMutableList()
    private val flow = kotlinx.coroutines.flow.MutableStateFlow(rows.toList())

    override fun observeDefs(): Flow<List<CategoryDef>> = flow
    override suspend fun defs(): List<CategoryDef> = rows.toList()

    override suspend fun create(
        label: String,
        iconKey: String,
        hue: CategoryHue,
        direction: Direction?,
        now: Long,
    ): CategoryDef {
        val def = CategoryDefaults.custom(label, iconKey, hue, direction, now, (rows.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        rows += def
        flow.value = rows.toList()
        return def
    }

    override suspend fun update(def: CategoryDef) {
        val i = rows.indexOfFirst { it.id == def.id }
        if (i >= 0) {
            rows[i] = if (def.builtIn) rows[i].copy(hidden = def.hidden) else def
            flow.value = rows.toList()
        }
    }

    override suspend fun reorder(idsInOrder: List<String>) {
        idsInOrder.forEachIndexed { index, id ->
            val i = rows.indexOfFirst { it.id == id }
            if (i >= 0) rows[i] = rows[i].copy(sortOrder = index)
        }
        rows.sortBy { it.sortOrder }
        flow.value = rows.toList()
    }

    override suspend fun setHidden(id: String, hidden: Boolean) {
        val i = rows.indexOfFirst { it.id == id }
        if (i >= 0) {
            rows[i] = rows[i].copy(hidden = hidden)
            flow.value = rows.toList()
        }
    }

    /** Test seam: how many rows point at [id]. */
    var txnCounts: Map<String, Int> = emptyMap()

    override suspend fun countTxns(id: String): Int = txnCounts[id] ?: 0

    override suspend fun deleteCustom(id: String, mergeInto: String) {
        rows.removeAll { it.id == id && !it.builtIn }
        flow.value = rows.toList()
    }
}
