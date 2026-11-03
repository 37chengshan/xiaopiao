package com.xiaopiao.app.domain

import java.util.Calendar

data class BudgetBucket(val from: Long, val until: Long, val spent: Cents, val count: Int)
data class BudgetRank(val key: String, val spent: Cents, val count: Int)
data class BudgetDetail(
    val from: Long,
    val until: Long,
    /** 计入预算的支出；已花、分桶、排行都只看它。 */
    val txns: List<Txn>,
    val buckets: List<BudgetBucket>,
    val categories: List<BudgetRank>,
    val merchants: List<BudgetRank>,
    /** 窗口内全部相关账单，含未计入的，好让详情页把它们再打开计入。新的在前。 */
    val listed: List<Txn>,
) {
    val spent: Cents get() = txns.sumOf { it.amountCents }
}

/** Uses the same half-open, local-calendar windows and expense semantics as BudgetMath. */
object BudgetDetails {
    fun shift(period: BudgetPeriod, anchor: Long, amount: Int): Long = Calendar.getInstance().apply {
        timeInMillis = BudgetMath.startOf(period, anchor)
        add(when (period) {
            BudgetPeriod.Day -> Calendar.DAY_OF_YEAR
            BudgetPeriod.Week -> Calendar.WEEK_OF_YEAR
            BudgetPeriod.Month -> Calendar.MONTH
        }, amount)
    }.timeInMillis

    fun merchantKey(txn: Txn, defs: List<CategoryDef>): String =
        txn.merchant.trim().ifEmpty { defs.resolve(txn).label }

    fun build(
        period: BudgetPeriod,
        anchor: Long,
        txns: List<Txn>,
        category: Category? = null,
        defs: List<CategoryDef> = CategoryDefaults.all,
        counting: BudgetCounting = BudgetCounting(),
    ): BudgetDetail {
        val from = BudgetMath.startOf(period, anchor)
        val until = BudgetMath.endOf(period, anchor)
        // 收到的钱只出现在「全部分类」里；分类预算只算支出。
        val listed = txns.filter {
            it.occurredAt >= from && it.occurredAt < until && (
                (it.direction == Direction.OUT && (category == null || it.category == category)) ||
                    (it.direction == Direction.IN && category == null)
                )
        }.sortedWith(compareByDescending<Txn> { it.occurredAt }.thenByDescending { it.id })
        val expenses = listed.filter { it.direction == Direction.OUT && counting.counts(it) }
        val buckets = mutableListOf<BudgetBucket>()
        var cursor = from
        while (cursor < until && period != BudgetPeriod.Day) {
            // A month follows Monday-based calendar weeks, clipped at both month edges.
            val next = if (period == BudgetPeriod.Week) shift(BudgetPeriod.Day, cursor, 1)
                else minOf(BudgetMath.endOf(BudgetPeriod.Week, cursor), until)
            val rows = expenses.filter { it.occurredAt >= cursor && it.occurredAt < next }
            buckets += BudgetBucket(cursor, next, rows.sumOf { it.amountCents }, rows.size)
            cursor = next
        }
        fun ranks(key: (Txn) -> String) = expenses.groupBy(key).map { (id, rows) ->
            BudgetRank(id, rows.sumOf { it.amountCents }, rows.size)
        }.sortedWith(compareByDescending<BudgetRank> { it.spent }.thenBy { it.key })
        return BudgetDetail(from, until, expenses, buckets,
            ranks { defs.resolve(it).id }, ranks { merchantKey(it, defs) }, listed)
    }
}
