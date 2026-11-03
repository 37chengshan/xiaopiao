package com.xiaopiao.app.domain

import com.xiaopiao.app.domain.BudgetMath.endOf
import com.xiaopiao.app.domain.BudgetMath.startOf

/**
 * 80% / 100% 两档提醒：每个周期、每个档位只发一次（key 里带周期起点，换周期自然重来）。
 * 直接跨到 100% 时只发 100% 那条，并把 80% 一起标记为已发，避免过一会儿又补一条。
 */
object BudgetAlerts {
    data class Alert(
        val key: String,
        val title: String,
        val body: String,
        /** 这次要一起写进「已提醒」的 key（含当前档位与更低档位）。 */
        val keysToMark: Set<String>,
    )

    private fun key(period: BudgetPeriod, windowStart: Long, level: Int) = "${period.name}:$windowStart:$level"

    fun pending(
        settings: BudgetSettings,
        txns: List<Txn>,
        now: Long,
        notified: Set<String>,
    ): List<Alert> = BudgetPeriod.entries.mapNotNull { period ->
        val limit = settings.limitOf(period) ?: return@mapNotNull null
        val windowStart = startOf(period, now)
        val windowEnd = endOf(period, now)
        val spent = BudgetMath.spentIn(txns, windowStart, windowEnd, settings.counting)
        val income = BudgetMath.incomeIn(txns, windowStart, windowEnd, settings.counting)
        val base = limit + income
        val level = BudgetMath.level(spent, base)
        if (level == 0) return@mapNotNull null
        val key = key(period, windowStart, level)
        if (key in notified) return@mapNotNull null

        val percent = (spent * 100 / base).toInt()
        val whenWord = when (period) {
            BudgetPeriod.Day -> "今天"
            BudgetPeriod.Week -> "本周"
            BudgetPeriod.Month -> "本月"
        }
        val incomeWord = if (income > 0) "，含收到 ¥${income.yuan()}" else ""
        val body = if (level >= 100) {
            "${whenWord}已花 ¥${spent.yuan()}，${period.noun}预算 ¥${limit.yuan()}$incomeWord，超出 ¥${(spent - base).yuan()}"
        } else {
            "${whenWord}已花 ¥${spent.yuan()}，${period.noun}预算 ¥${limit.yuan()}$incomeWord"
        }
        val title = when {
            level >= 100 -> "${period.noun}预算已用完"
            else -> "${period.noun}预算已用 $percent%"
        }
        val mark = (80..level step 20).map { key(period, windowStart, it) }.toSet()
        Alert(key = key, title = title, body = body, keysToMark = mark)
    }
}
