package com.xiaopiao.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.xiaopiao.app.MainActivity
import com.xiaopiao.app.R

/** Extra put on the launch intent so the app can jump to the right place. MainActivity may read it later. */
object ReminderIntents {
    const val EXTRA_OPEN = "open"
    const val OPEN_TODAY = "today"
    const val OPEN_WEEKLY = "weekly"
    const val OPEN_SUBSCRIPTIONS = "subscriptions"
    const val OPEN_BUDGET = "budget"
}

object Notifications {
    const val CHANNEL_DAILY = "daily_receipt"
    const val CHANNEL_WEEKLY = "weekly_awards"
    const val CHANNEL_SUBSCRIPTION = "subscription_charge"
    const val CHANNEL_BUDGET = "budget_milestones"

    const val ID_DAILY = 2101
    const val ID_WEEKLY = 2102
    const val ID_SUBSCRIPTION_BASE = 2200
    const val ID_BUDGET_BASE = 2300

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DAILY, "每日小票", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "每晚 21:30 提醒你来领今天的小票和头衔"
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_WEEKLY, "周报颁奖典礼", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "每周日 20:00 的颁奖典礼"
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_SUBSCRIPTION, "扣费提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "订阅扣费前 3 天的小提醒"
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_BUDGET, "预算提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "日/周/月预算用到 80% 或 100% 时提醒一次"
        })
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
    }

    /** Posts a notification that opens MainActivity with `open=[openTarget]`. Silently does nothing without permission. */
    fun post(context: Context, channel: String, id: Int, title: String, text: String, openTarget: String) {
        if (!canPost(context)) return
        createChannels(context)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(ReminderIntents.EXTRA_OPEN, openTarget)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = android.app.Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_receipt)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
    }
}
