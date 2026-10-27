package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay

/** One Mon–Sun week of history, viewed as of [now] (future days of the current week don't count). */
internal class WeekSlice(val weekStart: Long, val now: Long, val history: List<Txn>) {
    val weekEnd: Long = addDays(weekStart, 7)
    val isComplete: Boolean = now >= weekEnd

    /** Days that have at least started (1..7), 0 for a future week. */
    val startedDays: Int = when {
        isComplete -> 7
        now < weekStart -> 0
        else -> daysBetween(weekStart, now) + 1
    }

    /** Days that are fully over — today is still in progress, so it doesn't count as "zero spend" yet. */
    val finishedDays: Int = if (isComplete) 7 else (startedDays - 1).coerceAtLeast(0)

    val weekTxns: List<Txn> = history.filter { it.occurredAt in weekStart until weekEnd && it.occurredAt <= now }
    val outs: List<Txn> = weekTxns.filter { it.direction == Direction.OUT }
    val totalOut: Cents = outs.sumCents()

    /** Spend per day, always 7 entries (days not started yet are 0). */
    val dayTotals: List<Cents> = (0 until 7).map { i ->
        val ds = addDays(weekStart, i)
        outs.filter { startOfDay(it.occurredAt) == ds }.sumCents()
    }

    val activeDayCount: Int = (0 until startedDays).count { i ->
        val ds = addDays(weekStart, i)
        weekTxns.any { startOfDay(it.occurredAt) == ds }
    }

    val zeroSpendDays: Int = (0 until finishedDays).count { dayTotals[it] == 0L }

    fun prevWeekStart(n: Int = 1): Long = addDays(weekStart, -7 * n)

    /** Spend of the n-th previous week (full week). */
    fun totalOutOfWeek(start: Long, days: Int = 7): Cents {
        val end = addDays(start, days)
        return history.filter { it.direction == Direction.OUT && it.occurredAt in start until end }.sumCents()
    }
}
