package com.xiaopiao.app.insights

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
    fun build(dayStart: Long, dayItems: List<Txn>, history: List<Txn>): DailyReceipt {
        val total = dayItems.filter { it.direction == Direction.OUT }.sumOf { it.amountCents }
        val comment = if (dayItems.isEmpty()) "今天钱包在冬眠，真乖。" else "今天一共 ${dayItems.size} 笔，辛苦钱包了。"
        return DailyReceipt(dayStart, dayItems.sortedBy { it.occurredAt }, total, comment, badge = null)
    }
}
