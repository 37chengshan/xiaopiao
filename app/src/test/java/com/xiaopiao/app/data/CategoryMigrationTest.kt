package com.xiaopiao.app.data

import com.xiaopiao.app.domain.CategoryDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v3 migration is additive (task acceptance: 「Room 迁移」). Asserts the statements, since a real
 * Room migration test needs an instrumented run; the emulator pass re-checks it end to end with a
 * v2 database file.
 */
class CategoryMigrationTest {

    @Test
    fun `txn columns are added with defaults so old rows keep their data`() {
        assertEquals(2, CategorySchema.txnColumnAdds.size)
        CategorySchema.txnColumnAdds.forEach { sql ->
            assertTrue(sql, sql.startsWith("ALTER TABLE txns ADD COLUMN"))
            assertTrue(sql, sql.contains("NOT NULL DEFAULT ''"))
            assertTrue(sql, sql.contains("categoryId") || sql.contains("channel"))
        }
    }

    @Test
    fun `category table matches the entity shape`() {
        val sql = CategorySchema.CREATE_TABLE
        listOf("id", "label", "iconKey", "hue", "direction", "builtIn", "hidden", "sortOrder").forEach {
            assertTrue("missing column $it", sql.contains("`$it`"))
        }
        assertTrue(sql.contains("PRIMARY KEY(`id`)"))
        // direction is the only nullable column (null = offered for both directions).
        assertTrue(sql.contains("`direction` TEXT,"))
        assertTrue(sql.contains("IF NOT EXISTS"))
    }

    @Test
    fun `seeding writes every built-in with its default look`() {
        val rows = CategorySchema.seedRows()
        assertEquals(CategoryDefaults.all.size, rows.size)
        assertEquals(CategoryDefaults.all.map { it.id }, rows.map { it[0] })
        assertEquals(CategoryDefaults.all.map { it.label }, rows.map { it[1] })
        assertEquals(CategoryDefaults.all.map { it.iconKey }, rows.map { it[2] })
        assertEquals(CategoryDefaults.all.map { it.hue }, rows.map { it[3] })
        assertEquals(CategoryDefaults.all.map { it.sortOrder.toLong() }, rows.map { (it[4] as Int).toLong() })
    }

    @Test
    fun `the migration never drops or rewrites existing table data`() {
        val statements = CategorySchema.txnColumnAdds + CategorySchema.CREATE_TABLE + CategorySchema.SEED_SQL
        statements.forEach { sql ->
            val upper = sql.uppercase()
            listOf("DROP ", "DELETE FROM", "TRUNCATE", "ALTER TABLE txns RENAME").forEach { forbidden ->
                assertTrue("migration must not contain $forbidden: $sql", !upper.contains(forbidden))
            }
        }
        assertTrue(CategorySchema.SEED_SQL.startsWith("INSERT OR IGNORE"))
    }
}
