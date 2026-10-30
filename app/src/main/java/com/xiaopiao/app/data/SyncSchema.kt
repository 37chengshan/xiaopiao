package com.xiaopiao.app.data

/**
 * The v4 sync columns (docs/API-SYNC.md) as plain statements, so the migration can be executed by Room
 * and asserted by a JVM test, like [CategorySchema].
 */
internal object SyncSchema {
    private const val NOW_MS = "(CAST(strftime('%s','now') AS INTEGER) * 1000)"

    const val CREATE_TOMBSTONES: String =
        "CREATE TABLE IF NOT EXISTS `sync_tombstones` (" +
            "`kind` TEXT NOT NULL, `syncId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "`body` TEXT NOT NULL, PRIMARY KEY(`kind`, `syncId`))"

    val statements: List<String> = listOf(
        "ALTER TABLE txns ADD COLUMN syncId TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE txns ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE txns ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0",
        // Every existing row gets an id the server can key on and is queued for its first push.
        "UPDATE txns SET syncId = lower(hex(randomblob(16))), updatedAt = $NOW_MS, dirty = 1",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_txns_syncId` ON `txns` (`syncId`)",
        "ALTER TABLE categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE categories ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0",
        "UPDATE categories SET updatedAt = $NOW_MS, dirty = 1",
        CREATE_TOMBSTONES,
    )
}
