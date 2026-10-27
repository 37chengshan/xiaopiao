package com.xiaopiao.app

import android.content.Context
import com.xiaopiao.app.capture.CaptureProcessor
import com.xiaopiao.app.data.AppDatabase
import com.xiaopiao.app.data.CaptureLogRepository
import com.xiaopiao.app.data.DemoSeed
import com.xiaopiao.app.data.RoomLedgerRepository
import com.xiaopiao.app.domain.LedgerRepository
import com.xiaopiao.app.importer.CsvImporter

/** Tiny service locator. Owned by the data layer; UI only reads from it. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    private val roomLedger: RoomLedgerRepository by lazy { RoomLedgerRepository(database.txnDao()) }

    val ledger: LedgerRepository get() = roomLedger
    val captureLog: CaptureLogRepository by lazy { CaptureLogRepository(database.captureLogDao()) }
    val captureProcessor: CaptureProcessor by lazy { CaptureProcessor(ledger, captureLog) }
    val csvImporter: CsvImporter by lazy { CsvImporter(appContext.contentResolver, ledger) }

    /** Loads ~3 weeks of clearly-labelled demo transactions. Returns how many were new. */
    suspend fun loadDemoData(now: Long = System.currentTimeMillis()): Int =
        DemoSeed.generate(now).count { ledger.add(it) != null }

    /** Wipes every transaction and the raw capture log. */
    suspend fun clearAllData() {
        roomLedger.clearAll()
        captureLog.clear()
    }
}
