package com.xiaopiao.app

import android.content.Context
import androidx.room.withTransaction
import com.xiaopiao.app.capture.CaptureProcessor
import com.xiaopiao.app.data.AppDatabase
import com.xiaopiao.app.data.BudgetRepository
import com.xiaopiao.app.data.CaptureLogRepository
import com.xiaopiao.app.data.DemoSeed
import com.xiaopiao.app.data.RoomCategoryRepository
import com.xiaopiao.app.data.RoomLedgerRepository
import com.xiaopiao.app.domain.CategoryRepository
import com.xiaopiao.app.domain.asr.AsrHotwords
import com.xiaopiao.app.domain.asr.AsrModelManager
import com.xiaopiao.app.domain.asr.AsrPrefs
import com.xiaopiao.app.domain.LedgerRepository
import com.xiaopiao.app.importer.CsvImporter
import com.xiaopiao.app.wallet.AndroidCardCipher
import com.xiaopiao.app.wallet.CardStore
import com.xiaopiao.app.wallet.FileCardIo
import kotlinx.coroutines.flow.first

/** Tiny service locator. Owned by the data layer; UI only reads from it. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val account by lazy { com.xiaopiao.app.network.AccountClient(appContext) }

    /** 账本同步（docs/API-SYNC.md）：登录并验证邮箱后，本机账本与账号双向同步。 */
    val ledgerSync by lazy { com.xiaopiao.app.network.LedgerSync(appContext, account, database) }

    private val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    private val roomLedger: RoomLedgerRepository by lazy {
        RoomLedgerRepository(database.txnDao(), database.syncDao(), { block -> database.withTransaction { block() } })
    }

    val ledger: LedgerRepository get() = roomLedger

    /** 分类（tt#70/71）：名称、图标、颜色、排序的唯一来源，账单 / 记一笔 / 统计都读它。 */
    private val roomCategories: RoomCategoryRepository by lazy {
        RoomCategoryRepository(
            database.categoryDao(),
            tombstone = { database.syncDao().putTombstones(listOf(it)) },
        ) { block -> database.withTransaction { block() } }
    }
    val categories: CategoryRepository get() = roomCategories
    val captureLog: CaptureLogRepository by lazy { CaptureLogRepository(database.captureLogDao()) }

    /** 用户自设的日/周/月预算（tt#70），DataStore 存储、已花由账本实时算。 */
    val budget: BudgetRepository by lazy { BudgetRepository(appContext) }
    val circles by lazy { com.xiaopiao.app.ui.circle.CircleStore() }
    val captureProcessor: CaptureProcessor by lazy { CaptureProcessor(ledger, captureLog) }
    val csvImporter: CsvImporter by lazy { CsvImporter(appContext.contentResolver, ledger) }

    /** 端侧语音（tt 2026-10-29）：模型下载进度、选择偏好，录音不上传。 */
    val asrPrefs: AsrPrefs by lazy { AsrPrefs(appContext) }
    val asrModels: AsrModelManager by lazy { AsrModelManager(appContext) }

    /**
     * 语音热词：账本里出现最多的商户名 + 用户自建分类名，和固定预设一起写进 hotwords 文件。
     * 只在开始录音时算一次，读的是当前账本快照。
     */
    suspend fun asrHotwordSeed(): List<String> {
        val defs = runCatching { categories.defs() }.getOrDefault(emptyList())
        val txns = runCatching { ledger.observeAll().first() }.getOrDefault(emptyList())
        val merchants = txns.asSequence()
            .map { it.merchant.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .toList()
        return AsrHotwords.dynamicFor(defs, merchants)
    }

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
