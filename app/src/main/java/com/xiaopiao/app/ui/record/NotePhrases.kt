package com.xiaopiao.app.ui.record

import com.xiaopiao.app.domain.CategoryDefaults
import com.xiaopiao.app.domain.Txn
import kotlin.math.exp

/**
 * 备注上方的「常用短语」（tt 2026-11-02）：从这台手机最近 90 天的记录里数出最常写的备注。
 * 当前分类里用过的排前面，不够再用全局补齐。次数是主权重，最近用过只加一点（tt 2026-11-02：用得最多的
 * 在最前面，时间权重低一些）。只在本机算，不上传。
 */
object NotePhrases {
    const val MAX = 8
    private const val WINDOW_DAYS = 90
    private const val MAX_LENGTH = 16
    private const val DAY_MS = 86_400_000L
    private const val RECENCY = 0.2

    fun rank(txns: List<Txn>, categoryId: String, now: Long, max: Int = MAX): List<String> {
        val since = now - WINDOW_DAYS * DAY_MS
        val inCategory = HashMap<String, Double>()
        val overall = HashMap<String, Double>()
        for (txn in txns) {
            if (txn.occurredAt < since || txn.occurredAt > now + DAY_MS) continue
            val phrase = txn.merchant.trim()
            if (!usable(phrase)) continue
            // Each use counts 1; recency adds at most 0.2 (today) fading to ~0.07 after a month, so it only breaks
            // near-ties and never lets a recent one-off outrank a phrase used more often.
            val ageDays = (now - txn.occurredAt).coerceAtLeast(0L) / DAY_MS.toDouble()
            val weight = 1.0 + RECENCY * exp(-ageDays / 30.0)
            overall.merge(phrase, weight, Double::plus)
            val id = txn.categoryId.ifBlank { CategoryDefaults.idFor(txn.category) }
            if (id == categoryId) inCategory.merge(phrase, weight, Double::plus)
        }
        val first = inCategory.entries.sortedWith(order).map { it.key }
        val rest = overall.entries.sortedWith(order).map { it.key }.filter { it !in inCategory }
        return (first + rest).take(max)
    }

    /** Heaviest first; ties go to the shorter phrase, then alphabetical so the row never reshuffles. */
    private val order = compareByDescending<Map.Entry<String, Double>> { it.value }
        .thenBy { it.key.length }
        .thenBy { it.key }

    private fun usable(phrase: String): Boolean =
        phrase.isNotEmpty() && phrase.length <= MAX_LENGTH && phrase.any { it.isLetter() }
}
