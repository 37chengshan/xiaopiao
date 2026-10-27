package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn

sealed interface RegretTarget {
    val label: String

    data class ByCategory(val category: Category) : RegretTarget {
        override val label get() = "${category.emoji} ${category.label}"
    }

    data class ByMerchant(val merchant: String) : RegretTarget {
        override val label get() = merchant
    }

    fun matches(t: Txn): Boolean = when (this) {
        is ByCategory -> t.category == category
        is ByMerchant -> normalizeMerchant(t.merchant) == normalizeMerchant(merchant)
    }
}

data class RegretScenario(val target: RegretTarget, val reductionPercent: Int, val windowMonths: Int)

/** "≈ N 顿火锅": [count] of something that costs [unitCents]. */
data class Equivalent(val emoji: String, val unitLabel: String, val noun: String, val unitCents: Cents, val count: Int)

data class RegretResult(
    val scenario: RegretScenario,
    val windowStart: Long,
    val windowEnd: Long,
    val spentCents: Cents,
    val txnCount: Int,
    val savedCents: Cents,
    val equivalents: List<Equivalent>,
    val disclaimer: String,
)

/** Pure arithmetic only: no returns, no investing, no advice. */
object RegretSimulator {
    const val DISCLAIMER = "仅根据你的历史账目做的算术模拟，不含任何收益率，也不构成理财或消费建议。花不花、省不省，永远由你决定 🌱"
    val WINDOWS = listOf(1, 3, 6)

    private data class Yardstick(val emoji: String, val unitLabel: String, val noun: String, val cents: Cents)

    /** Ordered from big to small; the biggest few that fit are shown. */
    private val UNITS = listOf(
        Yardstick("🏝️", "次", "周末短途", 80000),
        Yardstick("🍲", "顿", "火锅", 12000),
        Yardstick("🎬", "张", "电影票", 4500),
        Yardstick("📺", "个月", "视频会员", 2500),
        Yardstick("🧋", "杯", "奶茶", 1800),
    )

    fun simulate(history: List<Txn>, scenario: RegretScenario, now: Long): RegretResult {
        val start = addMonths(now, -scenario.windowMonths)
        val matched = windowOuts(history, start, now).filter { scenario.target.matches(it) }
        val spent = matched.sumCents()
        val saved = spent * scenario.reductionPercent.coerceIn(0, 100) / 100
        return RegretResult(scenario, start, now, spent, matched.size, saved, equivalentsFor(saved), DISCLAIMER)
    }

    /** Biggest categories and merchants in the window, to offer as chips. */
    fun candidates(history: List<Txn>, now: Long, windowMonths: Int, limit: Int = 6): Pair<List<RegretTarget.ByCategory>, List<RegretTarget.ByMerchant>> {
        val outs = windowOuts(history, addMonths(now, -windowMonths), now)
        val cats = outs.groupBy { it.category }.entries.sortedByDescending { it.value.sumCents() }
            .take(limit).map { RegretTarget.ByCategory(it.key) }
        val merchants = outs.groupBy { normalizeMerchant(it.merchant) }.values
            .sortedByDescending { it.sumCents() }.take(limit)
            .map { RegretTarget.ByMerchant(it.first().merchant.trim()) }
        return cats to merchants
    }

    fun equivalentsFor(savedCents: Cents): List<Equivalent> =
        UNITS.filter { savedCents / it.cents >= 1 }.take(3)
            .map { Equivalent(it.emoji, it.unitLabel, it.noun, it.cents, (savedCents / it.cents).toInt()) }

    private fun windowOuts(history: List<Txn>, start: Long, end: Long): List<Txn> =
        history.filter { it.direction == Direction.OUT && it.occurredAt in start..end }
}
