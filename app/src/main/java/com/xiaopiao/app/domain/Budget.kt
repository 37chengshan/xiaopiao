package com.xiaopiao.app.domain

import java.util.Calendar

/** 用户自设的预算周期（tt#70；docs/APP-DESIGN.md §3.7 已把「预算 = 理财建议」的否决项划掉）。 */
enum class BudgetPeriod(val label: String, val noun: String) {
    Day("每日", "日"),
    Week("每周", "周"),
    Month("每月", "月"),
}

/**
 * 哪些账单进预算。支出默认计入；收到的转账 / 红包由两个全局开关决定；每笔账单可以在详情页单独覆盖（tt 2026-11-03）。
 */
data class BudgetCounting(
    val transfer: Boolean = false,
    val redPacket: Boolean = false,
    /** 账单 id → 单独设置的结果，优先于全局开关和默认规则。 */
    val overrides: Map<Long, Boolean> = emptyMap(),
) {
    fun counts(txn: Txn): Boolean {
        overrides[txn.id]?.let { return it }
        if (txn.direction == Direction.OUT) return true
        return when (TxnKinds.of(txn)) {
            TxnKind.Transfer -> transfer
            TxnKind.RedPacket -> redPacket
            null -> false
        }
    }
}

/** 三个周期可以任意组合、任意一个留空；分类预算按月。 */
data class BudgetSettings(
    val dailyLimit: Cents? = null,
    val weeklyLimit: Cents? = null,
    val monthlyLimit: Cents? = null,
    val categoryLimits: Map<Category, Cents> = emptyMap(),
    val counting: BudgetCounting = BudgetCounting(),
) {
    fun limitOf(period: BudgetPeriod): Cents? = when (period) {
        BudgetPeriod.Day -> dailyLimit
        BudgetPeriod.Week -> weeklyLimit
        BudgetPeriod.Month -> monthlyLimit
    }

    fun withLimit(period: BudgetPeriod, limit: Cents?): BudgetSettings = when (period) {
        BudgetPeriod.Day -> copy(dailyLimit = limit)
        BudgetPeriod.Week -> copy(weeklyLimit = limit)
        BudgetPeriod.Month -> copy(monthlyLimit = limit)
    }

    val isEmpty: Boolean
        get() = dailyLimit == null && weeklyLimit == null && monthlyLimit == null && categoryLimits.isEmpty()
}

/** 一个周期的算术结果：只含数字，不含任何建议文案。 */
data class BudgetStatus(
    val period: BudgetPeriod,
    val limit: Cents,
    val spent: Cents,
    /** 计入预算的收入（收到的转账 / 红包），会抬高额度。 */
    val income: Cents,
    /** 可以为负（已经超出）。 */
    val remaining: Cents,
    /** 本周期还剩几天（含今天）。 */
    val daysLeft: Int,
    /** 剩余额度摊到剩余天数（整数除法，可能为负）。 */
    val perDayLeft: Cents,
    val ratio: Float,
)

data class CategoryBudget(
    val category: Category,
    val limit: Cents,
    val spent: Cents,
) {
    val remaining: Cents get() = limit - spent
    val ratio: Float get() = if (limit > 0) spent.toFloat() / limit else 0f
}

/** 给账单页总览卡用（ledger-v7 的 `onOpenBudget()` / `BudgetSummary?` 参数以此为准）。 */
data class BudgetSummary(
    val monthLimit: Cents?,
    val monthSpent: Cents,
    val monthRemaining: Cents?,
    val monthIncome: Cents = 0L,
)

/**
 * 预算的全部算术。纯 JVM、按设备本地时区取周期边界（周一为一周起点），方便单测跨月与周起始。
 */
object BudgetMath {
    fun startOf(period: BudgetPeriod, now: Long): Long {
        val day = startOfDay(now)
        return when (period) {
            BudgetPeriod.Day -> day
            BudgetPeriod.Week -> startOfDay(
                Calendar.getInstance().apply {
                    timeInMillis = day
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                }.timeInMillis,
            )
            BudgetPeriod.Month -> startOfDay(
                Calendar.getInstance().apply {
                    timeInMillis = day
                    set(Calendar.DAY_OF_MONTH, 1)
                }.timeInMillis,
            )
        }
    }

    fun endOf(period: BudgetPeriod, now: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOf(period, now) }
        return when (period) {
            BudgetPeriod.Day -> cal.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            BudgetPeriod.Week -> cal.apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis
            BudgetPeriod.Month -> cal.apply { add(Calendar.MONTH, 1) }.timeInMillis
        }
    }

    /** 含今天的剩余天数：日 = 1，周 = 7 − 已过天数，月 = 当月剩余天数。 */
    fun daysLeft(period: BudgetPeriod, now: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return when (period) {
            BudgetPeriod.Day -> 1
            BudgetPeriod.Week -> {
                val index = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7   // Monday = 0
                7 - index
            }
            BudgetPeriod.Month -> cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH) + 1
        }.coerceAtLeast(1)
    }

    fun spentIn(txns: List<Txn>, fromInclusive: Long, toExclusive: Long, counting: BudgetCounting = BudgetCounting()): Cents =
        txns.filter {
            it.direction == Direction.OUT && it.occurredAt >= fromInclusive && it.occurredAt < toExclusive && counting.counts(it)
        }.sumOf { it.amountCents }

    fun incomeIn(txns: List<Txn>, fromInclusive: Long, toExclusive: Long, counting: BudgetCounting = BudgetCounting()): Cents =
        txns.filter {
            it.direction == Direction.IN && it.occurredAt >= fromInclusive && it.occurredAt < toExclusive && counting.counts(it)
        }.sumOf { it.amountCents }

    fun status(period: BudgetPeriod, limit: Cents?, txns: List<Txn>, now: Long, counting: BudgetCounting = BudgetCounting()): BudgetStatus? {
        if (limit == null || limit <= 0) return null
        val from = startOf(period, now)
        val to = endOf(period, now)
        val spent = spentIn(txns, from, to, counting)
        // 计入的收入抬高额度；没有收入时 base 就是 limit，和以前一样。
        val income = incomeIn(txns, from, to, counting)
        val base = limit + income
        val remaining = base - spent
        val days = daysLeft(period, now)
        return BudgetStatus(
            period = period,
            limit = limit,
            spent = spent,
            income = income,
            remaining = remaining,
            daysLeft = days,
            perDayLeft = Math.floorDiv(remaining, days.toLong()),
            ratio = spent.toFloat() / base,
        )
    }

    fun statuses(settings: BudgetSettings, txns: List<Txn>, now: Long): List<BudgetStatus> =
        BudgetPeriod.entries.mapNotNull { status(it, settings.limitOf(it), txns, now, settings.counting) }

    fun categoryBudgets(settings: BudgetSettings, txns: List<Txn>, now: Long): List<CategoryBudget> {
        val from = startOf(BudgetPeriod.Month, now)
        val to = endOf(BudgetPeriod.Month, now)
        return settings.categoryLimits.map { (category, limit) ->
            CategoryBudget(
                category = category,
                limit = limit,
                spent = txns.filter {
                    it.direction == Direction.OUT && it.category == category &&
                        it.occurredAt >= from && it.occurredAt < to && settings.counting.counts(it)
                }.sumOf { it.amountCents },
            )
        }.sortedByDescending { it.limit }
    }

    fun summary(settings: BudgetSettings, txns: List<Txn>, now: Long): BudgetSummary {
        val from = startOf(BudgetPeriod.Month, now)
        val to = endOf(BudgetPeriod.Month, now)
        val spent = spentIn(txns, from, to, settings.counting)
        val income = incomeIn(txns, from, to, settings.counting)
        val limit = settings.monthlyLimit
        return BudgetSummary(
            monthLimit = limit,
            monthSpent = spent,
            monthRemaining = limit?.let { it + income - spent },
            monthIncome = income,
        )
    }

    /** 0 = 未到 80%，80 = 已到 80%，100 = 已用完/超出。整数比较，避免浮点误差。 */
    fun level(spent: Cents, limit: Cents?): Int {
        if (limit == null || limit <= 0) return 0
        return when {
            spent >= limit -> 100
            spent * 5 >= limit * 4 -> 80
            else -> 0
        }
    }
}
