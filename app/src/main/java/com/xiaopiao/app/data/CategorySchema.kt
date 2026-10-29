package com.xiaopiao.app.data

import com.xiaopiao.app.domain.CategoryDefaults

/**
 * The v3 category schema as plain statements, so the migration can be executed by Room and asserted
 * by a JVM test at the same time (task acceptance: 「Room 迁移」 without an instrumented run).
 */
internal object CategorySchema {
    /** Additive only: every existing row keeps a value, so no data is rewritten or dropped. */
    val txnColumnAdds: List<String> = listOf(
        "ALTER TABLE txns ADD COLUMN categoryId TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE txns ADD COLUMN channel TEXT NOT NULL DEFAULT ''",
    )

    const val CREATE_TABLE: String =
        "CREATE TABLE IF NOT EXISTS `categories` (" +
            "`id` TEXT NOT NULL, `label` TEXT NOT NULL, `iconKey` TEXT NOT NULL, " +
            "`hue` TEXT NOT NULL, `direction` TEXT, `builtIn` INTEGER NOT NULL, " +
            "`hidden` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    /** One row per built-in, in [CategoryDefaults.all] order; built-ins can be hidden but never edited. */
    fun seedRows(): List<List<Any?>> = CategoryDefaults.all.map { def ->
        listOf(def.id, def.label, def.iconKey, def.hue, def.sortOrder)
    }

    const val SEED_SQL: String =
        "INSERT OR IGNORE INTO `categories` " +
            "(`id`,`label`,`iconKey`,`hue`,`direction`,`builtIn`,`hidden`,`sortOrder`) " +
            "VALUES (?,?,?,?,NULL,1,0,?)"
}
