package com.xiaopiao.app.data

import com.xiaopiao.app.domain.CategoryDefaults
import com.xiaopiao.app.domain.CategoryHue
import com.xiaopiao.app.domain.Direction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CRUD + reorder + hide + delete-with-merge, exercised through the real [RoomCategoryRepository]
 * against an in-memory DAO (no Android, no Room runtime needed).
 */
class CategoryRepositoryTest {

    private fun repo(dao: FakeCategoryDao = FakeCategoryDao()) =
        dao to RoomCategoryRepository(dao) { block -> block() }

    @Test
    fun `an empty table is seeded with the thirteen built-ins in order`() = runTest {
        val (_, repo) = repo()
        val defs = repo.defs()
        assertEquals(13, defs.size)
        assertEquals(CategoryDefaults.all.map { it.id }, defs.map { it.id })
        assertTrue(defs.all { it.builtIn && !it.hidden })
    }

    @Test
    fun `create appends a custom category that shows up in the flow`() = runTest {
        val (dao, repo) = repo()
        val created = repo.create("猫粮", "pets", CategoryHue.PINK, Direction.OUT, now = 1_000L)
        assertEquals("custom:1000", created.id)
        assertEquals(13, created.sortOrder)

        val defs = repo.defs()
        assertEquals(14, defs.size)
        assertEquals("猫粮", defs.last().label)
        assertEquals(CategoryHue.PINK.key, defs.last().hue)
        assertEquals(Direction.OUT, defs.last().direction)
        assertEquals(14, dao.all().size)
    }

    @Test
    fun `two categories created in the same millisecond get different ids`() = runTest {
        val (_, repo) = repo()
        val first = repo.create("A", "cake", CategoryHue.ORANGE, null, now = 5L)
        val second = repo.create("B", "cake", CategoryHue.BLUE, null, now = 5L)
        assertTrue(first.id != second.id)
        assertTrue(repo.defs().any { it.id == second.id })
    }

    @Test
    fun `editing a custom row renames and recolours it`() = runTest {
        val (_, repo) = repo()
        val created = repo.create("猫粮", "pets", CategoryHue.PINK, null, now = 9L)
        repo.update(created.copy(label = "猫零食", hue = CategoryHue.TEAL.key, iconKey = "cake"))
        val updated = repo.defs().first { it.id == created.id }
        assertEquals("猫零食", updated.label)
        assertEquals(CategoryHue.TEAL.key, updated.hue)
        assertEquals("cake", updated.iconKey)
    }

    @Test
    fun `editing a built-in cannot rename it, only hide it`() = runTest {
        val (_, repo) = repo()
        val meal = repo.defs().first { it.id == "meal" }
        repo.update(meal.copy(label = "改名", hidden = true))
        val after = repo.defs().first { it.id == "meal" }
        assertEquals("吃饭", after.label)
        assertTrue(after.hidden)
    }

    @Test
    fun `reorder rewrites sortOrder for the whole list`() = runTest {
        val (_, repo) = repo()
        val ids = repo.defs().map { it.id }
        val shuffled = listOf(ids[2], ids[0]) + ids.drop(1).filterNot { it == ids[2] }
        repo.reorder(shuffled)
        assertEquals(shuffled, repo.defs().map { it.id })
    }

    @Test
    fun `hiding a category keeps it resolvable but out of the pickers`() = runTest {
        val (_, repo) = repo()
        repo.setHidden("fun", true)
        val defs = repo.defs()
        assertTrue(defs.filterNot { it.hidden }.none { it.id == "fun" })
        assertNotNull(defs.firstOrNull { it.id == "fun" })
    }

    @Test
    fun `deleting a custom category merges its rows into 其他`() = runTest {
        val (dao, repo) = repo()
        val created = repo.create("猫粮", "pets", CategoryHue.PINK, null, now = 77L)
        dao.txns += FakeCategoryDao.TxnRow(1L, created.id)
        dao.txns += FakeCategoryDao.TxnRow(2L, created.id)
        assertEquals(2, repo.countTxns(created.id))

        repo.deleteCustom(created.id)

        assertEquals(0, repo.countTxns(created.id))
        assertTrue(repo.defs().none { it.id == created.id })
        assertEquals(listOf(CategoryDefaults.OTHER_ID, CategoryDefaults.OTHER_ID), dao.txns.map { it.categoryId })
    }

    @Test
    fun `deleting a built-in is refused by the dao guard`() = runTest {
        val (dao, repo) = repo()
        repo.defs()
        repo.deleteCustom("meal")
        assertNotNull(dao.all().firstOrNull { it.id == "meal" })
    }
}

/** In-memory [CategoryDao] plus the txn table the merge touches. */
private class FakeCategoryDao : CategoryDao {
    data class TxnRow(val id: Long, val categoryId: String)

    private val rows = mutableListOf<CategoryEntity>()
    private val flow = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val txns = mutableListOf<TxnRow>()

    private fun publish() {
        rows.sortWith(compareBy({ it.sortOrder }, { it.label }))
        flow.value = rows.toList()
    }

    override fun observeAll(): Flow<List<CategoryEntity>> = flow
    override suspend fun all(): List<CategoryEntity> = rows.toList()

    override suspend fun insertIgnore(entities: List<CategoryEntity>) {
        entities.forEach { if (rows.none { row -> row.id == it.id }) rows += it }
        publish()
    }

    override suspend fun insertOne(entity: CategoryEntity): Long {
        rows += entity
        publish()
        return rows.size.toLong()
    }

    override suspend fun update(entity: CategoryEntity) {
        val i = rows.indexOfFirst { it.id == entity.id }
        if (i >= 0) rows[i] = entity
        publish()
    }

    override suspend fun count(id: String): Int = rows.count { it.id == id }

    override suspend fun deleteCustom(id: String) {
        rows.removeAll { it.id == id && !it.builtIn }
        publish()
    }

    override suspend fun setOrder(id: String, order: Int) {
        val i = rows.indexOfFirst { it.id == id }
        if (i >= 0) rows[i] = rows[i].copy(sortOrder = order)
        publish()
    }

    override suspend fun setHidden(id: String, hidden: Boolean) {
        val i = rows.indexOfFirst { it.id == id }
        if (i >= 0) rows[i] = rows[i].copy(hidden = hidden)
        publish()
    }

    override suspend fun remapTxns(from: String, to: String) {
        txns.replaceAll { if (it.categoryId == from) it.copy(categoryId = to) else it }
    }

    override suspend fun countTxns(id: String): Int = txns.count { it.categoryId == id }
}
