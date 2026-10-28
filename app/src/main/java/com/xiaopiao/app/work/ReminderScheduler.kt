package com.xiaopiao.app.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import java.util.concurrent.TimeUnit

/**
 * Local reminders: daily receipt 21:30, Sunday awards 20:00, subscription charge heads-up (checked 10:00).
 * Each is a self-re-enqueuing one-time work so the time of day stays exact (periodic work drifts).
 */
object ReminderScheduler {
    enum class Kind(val uniqueName: String, val workerClass: Class<out Worker>, val nextAt: (Long) -> Long) {
        DAILY("reminder_daily_receipt", DailyReceiptWorker::class.java, ReminderTimes::nextDailyReceipt),
        WEEKLY("reminder_weekly_awards", WeeklyAwardsWorker::class.java, ReminderTimes::nextWeeklyAwards),
        SUBSCRIPTION("reminder_subscription_watch", SubscriptionWatchWorker::class.java, ReminderTimes::nextSubscriptionCheck),
    }

    /** Idempotent: safe to call on every app start / screen open. Existing pending reminders are kept. */
    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        Notifications.createChannels(app)
        Kind.entries.forEach { schedule(app, it, replace = false) }
    }

    internal fun schedule(context: Context, kind: Kind, replace: Boolean) {
        val now = System.currentTimeMillis()
        val request = OneTimeWorkRequest.Builder(kind.workerClass)
            .setInitialDelay(kind.nextAt(now) - now, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            kind.uniqueName,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
