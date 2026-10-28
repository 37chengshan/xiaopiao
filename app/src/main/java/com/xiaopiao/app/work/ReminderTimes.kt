package com.xiaopiao.app.work

import com.xiaopiao.app.insights.daysBetween
import java.util.Calendar

/** Pure scheduling maths, kept separate from WorkManager so it can be unit-tested. */
object ReminderTimes {
    const val DAILY_HOUR = 21
    const val DAILY_MINUTE = 30
    const val WEEKLY_WEEKDAY = Calendar.SUNDAY
    const val WEEKLY_HOUR = 20
    const val WEEKLY_MINUTE = 0
    const val SUBSCRIPTION_CHECK_HOUR = 10
    const val SUBSCRIPTION_LEAD_DAYS = 3

    /** Next [hour]:[minute] strictly after [now]. */
    fun nextDaily(now: Long, hour: Int, minute: Int): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** Next [calendarWeekday] (java.util.Calendar constant) at [hour]:[minute], strictly after [now]. */
    fun nextWeekly(now: Long, calendarWeekday: Int, hour: Int, minute: Int): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        while (c.get(Calendar.DAY_OF_WEEK) != calendarWeekday || c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun nextDailyReceipt(now: Long): Long = nextDaily(now, DAILY_HOUR, DAILY_MINUTE)
    fun nextWeeklyAwards(now: Long): Long = nextWeekly(now, WEEKLY_WEEKDAY, WEEKLY_HOUR, WEEKLY_MINUTE)
    fun nextSubscriptionCheck(now: Long): Long = nextDaily(now, SUBSCRIPTION_CHECK_HOUR, 0)

    /** True when a charge at [nextChargeAt] is exactly [leadDays] calendar days away from [now]. */
    fun isChargeReminderDay(nextChargeAt: Long, now: Long, leadDays: Int = SUBSCRIPTION_LEAD_DAYS): Boolean =
        daysBetween(now, nextChargeAt) == leadDays
}
