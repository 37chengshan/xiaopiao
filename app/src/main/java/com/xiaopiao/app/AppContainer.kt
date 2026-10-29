package com.xiaopiao.app

import android.content.Context
import androidx.room.withTransaction
import com.xiaopiao.app.capture.CaptureProcessor
import com.xiaopiao.app.data.AppDatabase
import com.xiaopiao.app.data.CaptureLogRepository
import com.xiaopiao.app.data.DemoSeed
import com.xiaopiao.app.data.RoomCategoryRepository
import com.xiaopiao.app.data.RoomLedgerRepository
import com.xiaopiao.app.domain.CategoryRepository
import com.xiaopiao.app.domain.LedgerRepository
import com.xiaopiao.app.importer.CsvImporter
import com.xiaopiao.app.wallet.AndroidCardCipher
import com.xiaopiao.app.wallet.CardStore
import com.xiaopiao.app.wallet.FileCardIo

/** Tiny service locator. Owned by the data layer; UI only reads from it. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    private val roomLedger: RoomLedgerRepository by lazy { RoomLedgerRepository(database.txnDao()) }

    val ledger: LedgerRepository get() = roomLedger

    /** 分类（tt#70/71）：名称、图标、颜色、排序的唯一来源，账单 / 记一笔 / 统计都读它。 */
    private val roomCategories: RoomCategoryRepository by lazy {
        RoomCategoryRepository(database.categoryDao()) { block -> database.withTransaction { block() } }
    }
    val categories: CategoryRepository get() = roomCategories
    val captureLog: CaptureLogRepository by lazy { CaptureLogRepository(database.captureLogDao()) }
    val captureProcessor: CaptureProcessor by lazy { CaptureProcessor(ledger, captureLog) }
    val csvImporter: CsvImporter by lazy { CsvImporter(appContext.contentResolver, ledger) }

    /** 卡包（§5.5）：本机 JSON + Keystore 加密的卡号，数据不进备份。 */
    val cards: CardStore by lazy {
        CardStore(FileCardIo.inAppFiles(appContext), AndroidCardCipher()).also { it.load() }
    }

    /** Loads ~3 weeks of clearly-labelled demo transactions. Returns how many were new. */
    suspend fun loadDemoData(now: Long = System.currentTimeMillis()): Int =
        DemoSeed.generate(now).count { ledger.add(it) != null }

    /** Wipes every transaction and the raw capture log. */
    suspend fun clearAllData() {
        roomLedger.clearAll()
        captureLog.clear()
    }
}
