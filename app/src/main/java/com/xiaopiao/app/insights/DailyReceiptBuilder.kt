package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.DailyReceipt
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn

/**
 * Contract shared by the Today screen and the insights engine.
 * Insights owns the implementation; keep this signature stable.
 *
 * @param dayStart start-of-day epoch millis of the receipt's day
 * @param dayItems transactions that happened on that day
 * @param history all transactions (for streaks, comparisons and badges)
 */
object DailyReceiptBuilder {
    var writer: CommentWriter = TemplateCommentWriter()

    fun build(dayStart: Long, dayItems: List<Txn>, history: List<Txn>): DailyReceipt {
        val outs = dayItems.filter { it.direction == Direction.OUT }
        val total = outs.sumOf { it.amountCents }
        val badge = BadgeEngine.stampForDay(dayStart, dayItems, history)
        val stats = DayStats(
            dayStart = dayStart,
            totalOutCents = total,
            txnCount = dayItems.size,
            topCategory = outs.groupBy { it.category }.maxByOrNull { e -> e.value.sumOf { it.amountCents } }?.key,
            biggestCents = outs.maxOfOrNull { it.amountCents } ?: 0L,
            recentDailyAvgCents = recentDailyAverage(dayStart, history),
            badge = badge,
        )
        return DailyReceipt(dayStart, dayItems.sortedBy { it.occurredAt }, total, writer.dayComment(stats), badge)
    }

    /** Mean daily spend over the 7 days before [dayStart]; null when there was no earlier history. */
    private fun recentDailyAverage(dayStart: Long, history: List<Txn>): Cents? {
        val from = addDays(dayStart, -7)
        val recent = history.filter { it.occurredAt in from until dayStart }
        if (recent.isEmpty()) return null
        return recent.filter { it.direction == Direction.OUT }.sumOf { it.amountCents } / 7
    }
}
