package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Subscription
import com.xiaopiao.app.domain.Txn
import kotlin.math.abs

enum class SubscriptionFlag(val label: String) {
    PRICE_UP("涨价了"),
    DUPLICATE_KIND("同类重复"),
}

data class SubscriptionInsight(
    val subscription: Subscription,
    val flags: Set<SubscriptionFlag>,
    val kind: SubscriptionKind?,
    /** True when the expected charge date passed without a charge (probably cancelled). */
    val lapsed: Boolean,
)

/** Real cost per year using calendar cycles (weekly ×52, monthly ×12 …). */
fun Subscription.annualCents(): Cents = when {
    cycleDays <= 7 -> amountCents * 52
    cycleDays <= 31 -> amountCents * 12
    cycleDays <= 92 -> amountCents * 4
    else -> amountCents
}

/**
 * Finds "same merchant + near-equal amount (±10%) + regular cycle" charges.
 * Pure function of history, so it is easy to test and safe to run in a background worker.
 */
object SubscriptionDetector {
    private data class Cycle(val days: Int, val minGap: Int, val maxGap: Int, val minOccurrences: Int, val graceDays: Int)

    private val CYCLES = listOf(
        Cycle(7, 6, 8, 3, 2),
        Cycle(30, 26, 35, 2, 7),
        Cycle(91, 84, 98, 2, 14),
        Cycle(365, 350, 380, 2, 30),
    )

    /** Categories that may be subscriptions without any naming hint. */
    private val OPEN_CATEGORIES = setOf(Category.SUBSCRIPTION, Category.FUN, Category.LEARNING, Category.OTHER)

    /** Active subscriptions as of [now]. */
    fun detect(history: List<Txn>, now: Long): List<Subscription> = analyze(history, now).map { it.subscription }

    /** Active subscriptions with warning flags, sorted by next charge date. */
    fun analyze(history: List<Txn>, now: Long): List<SubscriptionInsight> =
        analyzeAll(history, now).filterNot { it.lapsed }.sortedBy { it.subscription.nextChargeAt }.withDuplicateFlags()

    /** Everything that ever looked like a subscription, including ones whose charges stopped. */
    fun analyzeAll(history: List<Txn>, now: Long): List<SubscriptionInsight> =
        history.filter { it.direction == Direction.OUT && it.amountCents > 0 }
            .groupBy { normalizeMerchant(it.merchant) }
            .values
            .mapNotNull { findIn(it, now) }

    /**
     * Subscriptions whose charge was due in [from, to) based on earlier history but never arrived —
     * the "订阅清道夫" signal. Looks only at history before [from].
     */
    fun droppedDuring(history: List<Txn>, from: Long, to: Long): List<Subscription> {
        val before = history.filter { it.occurredAt < from }
        val cycleOf = { s: Subscription -> CYCLES.first { it.days == s.cycleDays } }
        return analyzeAll(before, from)
            .map { it.subscription }
            .filter { it.nextChargeAt in from until to }
            .filter { s ->
                val due = s.nextChargeAt
                val deadline = addDays(due, cycleOf(s).graceDays)
                history.none { t ->
                    t.direction == Direction.OUT && normalizeMerchant(t.merchant) == normalizeMerchant(s.merchant) &&
                        t.occurredAt >= addDays(due, -cycleOf(s).graceDays) && t.occurredAt <= deadline
                }
            }
    }

    // ---- internals -------------------------------------------------------------------------

    private fun findIn(group: List<Txn>, now: Long): SubscriptionInsight? {
        val charges = dedupeSameDay(group.sortedBy { it.occurredAt })
        if (charges.size < 2 || !plausible(group)) return null
        for (end in charges.indices.reversed()) {
            val best = CYCLES.map { it to chainEndingAt(charges, end, it) }
                .filter { (c, chain) -> chain.size >= c.minOccurrences }
                .maxByOrNull { it.second.size } ?: continue
            return build(best.first, best.second, now)
        }
        return null
    }

    /** Two charges within 2 days are one event (e.g. retry after a failed payment). */
    private fun dedupeSameDay(sorted: List<Txn>): List<Txn> {
        val out = ArrayList<Txn>()
        for (t in sorted) {
            if (out.isNotEmpty() && daysBetween(out.last().occurredAt, t.occurredAt) <= 1) continue
            out += t
        }
        return out
    }

    private fun plausible(group: List<Txn>): Boolean =
        group.any { it.category in OPEN_CATEGORIES } ||
            group.any { SubscriptionCatalog.looksLikeSubscription(it.merchant + " " + it.note) }

    /** Walks backwards from [end] while the gap matches [cycle] and amounts stay within ±10%. */
    private fun chainEndingAt(charges: List<Txn>, end: Int, cycle: Cycle): List<Txn> {
        val chain = arrayListOf(charges[end])
        var i = end - 1
        while (i >= 0) {
            val gap = daysBetween(charges[i].occurredAt, chain.last().occurredAt)
            val amountOk = nearEqual(charges[i].amountCents, chain.last().amountCents) ||
                (chain.size == 1 && isPriceHike(charges[i].amountCents, chain.last().amountCents))
            if (gap !in cycle.minGap..cycle.maxGap || !amountOk) break
            chain += charges[i]
            i--
        }
        return chain.reversed()
    }

    /** The newest charge may jump up to +50% (a price hike) — that is exactly what we want to warn about. */
    private fun isPriceHike(earlier: Cents, later: Cents): Boolean = later > earlier && later * 2 <= earlier * 3

    private fun nearEqual(earlier: Cents, later: Cents): Boolean = abs(later - earlier) * 10 <= earlier

    private fun build(cycle: Cycle, chain: List<Txn>, now: Long): SubscriptionInsight {
        val last = chain.last()
        val next = when (cycle.days) {
            7 -> addDays(last.occurredAt, 7)
            30 -> addMonths(last.occurredAt, 1)
            91 -> addMonths(last.occurredAt, 3)
            else -> addMonths(last.occurredAt, 12)
        }
        val sub = Subscription(
            merchant = last.merchant.trim(),
            amountCents = last.amountCents,
            cycleDays = cycle.days,
            lastChargedAt = last.occurredAt,
            nextChargeAt = next,
            occurrences = chain.size,
        )
        val flags = if (chain.size >= 2 && last.amountCents * 100 >= chain[chain.size - 2].amountCents * 105) {
            setOf(SubscriptionFlag.PRICE_UP)
        } else emptySet()
        return SubscriptionInsight(sub, flags, SubscriptionCatalog.kindOf(sub.merchant), lapsed = now > addDays(next, cycle.graceDays))
    }

    private fun List<SubscriptionInsight>.withDuplicateFlags(): List<SubscriptionInsight> {
        val dupKinds = mapNotNull { it.kind }.groupingBy { it }.eachCount().filterValues { it >= 2 }.keys
        return map { if (it.kind in dupKinds) it.copy(flags = it.flags + SubscriptionFlag.DUPLICATE_KIND) else it }
    }
}
