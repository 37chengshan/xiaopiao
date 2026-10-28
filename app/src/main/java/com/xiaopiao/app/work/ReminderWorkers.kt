package com.xiaopiao.app.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.xiaopiao.app.XiaopiaoApp
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.insights.InsightsPrefs
import com.xiaopiao.app.insights.SubscriptionDetector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain [Worker]s on purpose: each posts its notification and then re-enqueues itself as the very last
 * step (REPLACE), so there is no suspension point where the chain could be cancelled half-way.
 */
class DailyReceiptWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Notifications.post(
            applicationContext, Notifications.CHANNEL_DAILY, Notifications.ID_DAILY,
            "今日小票已出炉 🧾", "来看看今天的头衔", ReminderIntents.OPEN_TODAY,
        )
        ReminderScheduler.schedule(applicationContext, ReminderScheduler.Kind.DAILY, replace = true)
        return Result.success()
    }
}

class WeeklyAwardsWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        Notifications.post(
            applicationContext, Notifications.CHANNEL_WEEKLY, Notifications.ID_WEEKLY,
            "本周颁奖典礼开始啦 🏆", "来领这一周的头衔和小报告", ReminderIntents.OPEN_WEEKLY,
        )
        ReminderScheduler.schedule(applicationContext, ReminderScheduler.Kind.WEEKLY, replace = true)
        return Result.success()
    }
}

/** Runs every morning; warns 3 days before a detected subscription is expected to charge. */
class SubscriptionWatchWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        try {
            remind()
        } finally {
            ReminderScheduler.schedule(applicationContext, ReminderScheduler.Kind.SUBSCRIPTION, replace = true)
        }
        return Result.success()
    }

    private fun remind() {
        val app = applicationContext as? XiaopiaoApp ?: return
        val now = System.currentTimeMillis()
        val (history, cancelled) = runBlocking {
            app.container.ledger.observeAll().first() to InsightsPrefs(applicationContext).cancelled()
        }
        val fmt = SimpleDateFormat("M月d日", Locale.CHINA)
        SubscriptionDetector.detect(history, now)
            .filterNot { InsightsPrefs.isCancelled(cancelled, it.merchant) }
            .filter { ReminderTimes.isChargeReminderDay(it.nextChargeAt, now) }
            .forEach { sub ->
                Notifications.post(
                    applicationContext, Notifications.CHANNEL_SUBSCRIPTION,
                    Notifications.ID_SUBSCRIPTION_BASE + (sub.merchant.hashCode() and 0xFF),
                    "扣费提醒 🔔",
                    "「${sub.merchant}」预计 ${fmt.format(Date(sub.nextChargeAt))} 扣 ¥${sub.amountCents.yuan()}。不想续就去关掉自动续费，想续就当没看见～",
                    ReminderIntents.OPEN_SUBSCRIPTIONS,
                )
            }
    }
}
