package com.xiaopiao.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TxnEntity::class, CaptureLogEntity::class, CategoryEntity::class, TombstoneEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun captureLogDao(): CaptureLogDao
    abstract fun categoryDao(): CategoryDao
    abstract fun syncDao(): SyncDao

    companion object {
        /** v2 adds `cardLast4` so a payment can be attributed to the wallet card it was made on (§5.5). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE txns ADD COLUMN cardLast4 TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v3 makes categories data (tt#70/71): the `categories` table arrives seeded with the 13
         * built-ins, and rows gain `categoryId` + `channel`. Both new columns default to '' so every
         * existing row keeps its legacy enum category and shows 「自动记账」 until capture fills the channel.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                CategorySchema.txnColumnAdds.forEach { db.execSQL(it) }
                db.execSQL(CategorySchema.CREATE_TABLE)
                CategorySchema.seedRows().forEach { row -> db.execSQL(CategorySchema.SEED_SQL, row.toTypedArray()) }
            }
        }

        /** v4 adds account sync (docs/API-SYNC.md): a stable `syncId`, `updatedAt` / `dirty`, and tombstones. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SyncSchema.statements.forEach { db.execSQL(it) }
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "xiaopiao.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
