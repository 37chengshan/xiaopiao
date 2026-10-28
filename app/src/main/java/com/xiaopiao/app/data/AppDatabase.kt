package com.xiaopiao.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TxnEntity::class, CaptureLogEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun captureLogDao(): CaptureLogDao

    companion object {
        /** v2 adds `cardLast4` so a payment can be attributed to the wallet card it was made on (§5.5). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE txns ADD COLUMN cardLast4 TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "xiaopiao.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
