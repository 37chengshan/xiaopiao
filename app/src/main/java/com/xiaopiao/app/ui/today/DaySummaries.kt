package com.xiaopiao.app.ui.today

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay

/** One day's thumbnail data for the "最近几天的小票" carousel. */
data class DaySummary(val dayStart: Long, val count: Int, val totalOutCents: Cents, val topEmoji: String?)

/** Spend per category for the "今日明细" chips. */
data class CategoryTotal(val category: Category, val cents: Cents)

/** Pure aggregation for the Today screen. */
object DaySummaries {
    /** Newest first: [days] entries ending at [today], empty days included (so gaps show as quiet receipts). */
    fun recent(all: List<Txn>, today: Long, days: Int = 7): List<DaySummary> {
        val byDay = all.groupBy { startOfDay(it.occurredAt) }
        return (0 until days).map { back ->
            val day = startOfDay(today - back * DAY_MS)
            val items = byDay[day].orEmpty()
            val out = items.filter { it.direction == Direction.OUT }
            DaySummary(day, items.size, out.sumOf { it.amountCents }, topCategory(out)?.category?.emoji)
        }
    }

    fun byCategory(items: List<Txn>): List<CategoryTotal> =
        items.filter { it.direction == Direction.OUT }
            .groupBy { it.category }
            .map { (c, list) -> CategoryTotal(c, list.sumOf { it.amountCents }) }
            .sortedByDescending { it.cents }

    private fun topCategory(out: List<Txn>): CategoryTotal? = byCategory(out).firstOrNull()
}
