package com.xiaopiao.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.xiaopiao.app.XiaopiaoApp
import com.xiaopiao.app.domain.BudgetAlerts
import com.xiaopiao.app.domain.BudgetMath
import com.xiaopiao.app.domain.BudgetPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * 预算提醒（tt#70）：日/周/月预算用到 80% 与 100% 时各提醒一次。
 * 数字全部现算，通知里只陈述「已经花了多少 / 预算是多少」。
 */
object BudgetNotifier {
    /** 每档只发一次；已发的 key 里带周期起点，跨周期自然重来。 */
    suspend fun check(context: Context, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext as? XiaopiaoApp ?: return
        val repo = app.container.budget
        val txns = app.container.ledger.observeAll().first()
        val settings = repo.settings()
        if (settings.isEmpty) return
        val alerts = BudgetAlerts.pending(settings, txns, now, repo.notified())
        alerts.forEach { alert ->
            Notifications.post(
                context,
                Notifications.CHANNEL_BUDGET,
                Notifications.ID_BUDGET_BASE + (alert.key.hashCode() and 0xFF),
                alert.title,
                alert.body,
                ReminderIntents.OPEN_BUDGET,
            )
        }
        if (alerts.isNotEmpty()) {
            // 保留最近两个月的 key 就够了：更早的周期已经不会再次触发。
            val pruneBefore = BudgetMath.startOf(BudgetPeriod.Month, now) - 62L * 24 * 60 * 60 * 1000
            repo.markNotified(alerts.flatMap { it.keysToMark }.toSet(), pruneBefore)
        }
    }
}

/** 15 分钟一次的后台检查（前台启动时也会立刻检查一次，见 XiaopiaoApp）。 */
class BudgetWatchWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        runCatching { runBlocking { BudgetNotifier.check(applicationContext) } }
        return Result.success()
    }
}

object BudgetWatchScheduler {
    private const val UNIQUE = "budget_watch"

    /** 幂等：应用启动与统计页打开时都可以调用。 */
    fun ensure(context: Context) {
        val request = PeriodicWorkRequestBuilder<BudgetWatchWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE, ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }
}
