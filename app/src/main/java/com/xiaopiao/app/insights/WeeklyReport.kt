package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Badge
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay

data class CategoryShare(val category: Category, val cents: Cents, val share: Float, val count: Int)

data class WeeklyReport(
    val weekStart: Long,
    val weekEnd: Long,
    val isComplete: Boolean,
    val totalOutCents: Cents,
    val vsPrevPercent: Int?,
    val txnCount: Int,
    val topCategories: List<CategoryShare>,
    val biggest: Txn?,
    /** Mon..Sun, 7 entries. Days that have not happened yet are 0. */
    val dayTotals: List<Cents>,
    val startedDays: Int,
    val longestRecordStreak: Int,
    val zeroSpendDays: Int,
    val badges: List<Badge>,
    val comment: String,
) {
    val isEmpty: Boolean get() = txnCount == 0
}

object WeeklyReportBuilder {
    /** Builds the Mon–Sun report for the week containing [anyTimeInWeek], as seen at [now]. */
    fun build(
        anyTimeInWeek: Long,
        history: List<Txn>,
        now: Long,
        writer: CommentWriter = TemplateCommentWriter(),
    ): WeeklyReport {
        val w = WeekSlice(startOfWeek(anyTimeInWeek), now, history)
        val vs = vsPrevious(w)
        val top = categoryShares(w).take(3)
        val badges = pickBadges(w)
        val stats = WeekStats(w.weekStart, w.totalOut, vs, top.firstOrNull()?.category, w.zeroSpendDays, w.outs.size, badges)
        return WeeklyReport(
            weekStart = w.weekStart,
            weekEnd = w.weekEnd,
            isComplete = w.isComplete,
            totalOutCents = w.totalOut,
            vsPrevPercent = vs,
            txnCount = w.weekTxns.size,
            topCategories = top,
            biggest = w.outs.maxByOrNull { it.amountCents },
            dayTotals = w.dayTotals,
            startedDays = w.startedDays,
            longestRecordStreak = longestStreak(w),
            zeroSpendDays = w.zeroSpendDays,
            badges = badges,
            comment = writer.weekComment(stats.copy(txnCount = w.weekTxns.size)),
        )
    }

    /** Compares with the same span of the previous week so an unfinished week is not judged unfairly. */
    private fun vsPrevious(w: WeekSlice): Int? {
        if (w.startedDays == 0) return null
        val span = if (w.isComplete) 7 else w.startedDays
        val prev = w.totalOutOfWeek(w.prevWeekStart(), span)
        if (prev <= 0L) return null
        return Math.round((w.totalOut - prev) * 100.0 / prev).toInt()
    }

    private fun categoryShares(w: WeekSlice): List<CategoryShare> {
        if (w.totalOut <= 0L) return emptyList()
        return w.outs.groupBy { it.category }
            .map { (c, list) -> CategoryShare(c, list.sumCents(), list.sumCents().toFloat() / w.totalOut, list.size) }
            .sortedByDescending { it.cents }
    }

    private fun pickBadges(w: WeekSlice): List<Badge> {
        val earned = WeekBadges.compute(w).take(2)
        if (earned.isNotEmpty() || w.weekTxns.isEmpty()) return earned
        return listOf(BadgeCatalog.everydayJoy("这周平平稳稳，没有特别的头衔，也是一种福气。"))
    }

    private fun longestStreak(w: WeekSlice): Int {
        var best = 0
        var run = 0
        for (i in 0 until w.startedDays) {
            val ds = addDays(w.weekStart, i)
            if (w.weekTxns.any { startOfDay(it.occurredAt) == ds }) { run++; best = maxOf(best, run) } else run = 0
        }
        return best
    }
}
