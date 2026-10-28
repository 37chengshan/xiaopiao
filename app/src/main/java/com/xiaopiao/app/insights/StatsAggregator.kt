package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

/** The four periods of the 统计 tab (docs/APP-DESIGN.md §3.3). */
enum class StatsRange(val label: String) { Week("周报"), Month("月报"), Year("年报"), Custom("自定义") }

/** Weekly periods read as bars, everything longer reads as a line + area (§3.7). */
enum class StatsChart { Bars, Line }

/** One column of the trend chart: a week for 周报, a day for 月报/自定义, a month for 年报. */
data class StatsBucket(
    val start: Long,
    val top: String,
    val bottom: String,
    val cents: Cents,
    val count: Int,
    val isCurrent: Boolean,
)

/** One slice of the donut and one row of the ranking (same data, two views). */
data class StatsEntry(
    val key: String,
    val label: String,
    val category: com.xiaopiao.app.domain.Category,
    val cents: Cents,
    val count: Int,
    val share: Float,
    /** 主分类视图里这一类的头号商户；子分类视图里为 null（label 本身就是商户）。 */
    val detail: String? = null,
)

/** 环比: absolute difference plus the percentage when the previous period had any spend. */
data class StatsDelta(val cents: Cents, val percent: Int?)

data class StatsSnapshot(
    val range: StatsRange,
    val chart: StatsChart,
    val periodTitle: String,
    val from: Long,
    val to: Long,
    val days: Int,
    val direction: Direction,
    val total: Cents,
    val avgPerDay: Cents,
    val balance: Cents,
    val delta: StatsDelta?,
    val deltaCaption: String,
    val activeDays: Int,
    val buckets: List<StatsBucket>,
    val entries: List<StatsEntry>,
    /** Draw every n-th x label so 月报 shows 8 ticks and 年报 shows 12. */
    val xTickEvery: Int,
    /** Bucket that carries the permanent tooltip / the highlighted bar. */
    val highlightIndex: Int = 0,
) {
    val isEmpty: Boolean get() = total == 0L
    val aiTitle: String
        get() = when (range) {
            StatsRange.Week -> "AI 本周小结"
            StatsRange.Month -> "AI 本月小结"
            StatsRange.Year -> "AI 年度小结"
            StatsRange.Custom -> "AI 区间小结"
        }
}

/** Grouping key for the composition card: 主分类 = category, 子分类 = merchant. */
enum class StatsGrouping(val label: String) { Primary("主分类"), Secondary("子分类") }

/**
 * All numbers behind 统计: buckets, totals, 环比, composition. Pure JVM so the whole page can be tested
 * without Compose; the Canvas only draws what this object already computed (§3.3).
 */
object StatsAggregator {
    // ── windows ────────────────────────────────────────────────────────────────────────────────

    fun startOfMonth(ms: Long): Long = startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = startOfDay(ms)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis,
    )

    fun endOfMonth(ms: Long): Long = startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = startOfMonth(ms)
            add(Calendar.MONTH, 1)
        }.timeInMillis,
    )

    fun startOfYear(ms: Long): Long = startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = startOfDay(ms)
            set(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis,
    )

    fun endOfYear(ms: Long): Long = startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = startOfYear(ms)
            add(Calendar.YEAR, 1)
        }.timeInMillis,
    )

    /** Half-open `[from, to)` window of the period that contains (or starts at) [anchor]. */
    fun window(range: StatsRange, anchor: Long, customFrom: Long? = null, customTo: Long? = null): Pair<Long, Long> =
        when (range) {
            StatsRange.Week -> startOfWeek(anchor).let { it to addDays(it, 7) }
            StatsRange.Month -> startOfMonth(anchor) to endOfMonth(anchor)
            StatsRange.Year -> startOfYear(anchor) to endOfYear(anchor)
            StatsRange.Custom -> {
                val a = startOfDay(customFrom ?: startOfMonth(anchor))
                val b = startOfDay(customTo ?: startOfDay(anchor))
                if (a <= b) a to addDays(b, 1) else startOfDay(customTo ?: b) to addDays(a, 1)
            }
        }

    /** The period immediately before [from, to): used for 环比. */
    fun previousWindow(range: StatsRange, from: Long, to: Long): Pair<Long, Long> = when (range) {
        StatsRange.Week -> addDays(from, -7) to from
        StatsRange.Month -> startOfMonth(addMonths(from, -1)) to startOfMonth(from)
        StatsRange.Year -> startOfYear(addMonths(from, -12)) to startOfYear(from)
        StatsRange.Custom -> {
            val length = to - from
            (from - length) to from
        }
    }

    fun shiftAnchor(range: StatsRange, anchor: Long, step: Int): Long = when (range) {
        StatsRange.Week -> addDays(startOfWeek(anchor), step * 7)
        StatsRange.Month -> startOfMonth(addMonths(anchor, step))
        StatsRange.Year -> startOfYear(addMonths(anchor, step * 12))
        StatsRange.Custom -> anchor
    }

    fun periodTitle(range: StatsRange, from: Long, to: Long): String = when (range) {
        StatsRange.Week -> "${md(from)}-${md(to - DAY_MS)}"
        StatsRange.Month -> "${yearOf(from)}年${monthOf(from)}月"
        StatsRange.Year -> "${yearOf(from)}年"
        StatsRange.Custom -> {
            val sameYear = yearOf(from) == yearOf(to - DAY_MS)
            if (sameYear) "${md(from)}-${md(to - DAY_MS)}" else "${ymd(from)}-${ymd(to - DAY_MS)}"
        }
    }

    // ── build ──────────────────────────────────────────────────────────────────────────────────

    fun build(
        history: List<Txn>,
        now: Long,
        range: StatsRange,
        anchor: Long,
        direction: Direction = Direction.OUT,
        grouping: StatsGrouping = StatsGrouping.Primary,
        customFrom: Long? = null,
        customTo: Long? = null,
    ): StatsSnapshot {
        val (from, to) = window(range, anchor, customFrom, customTo)
        val inWindow = history.filter { it.occurredAt >= from && it.occurredAt < to }
        val matching = inWindow.filter { it.direction == direction }
        val total = matching.sumOf { it.amountCents }
        val days = elapsedDays(range, from, to, now)
        val prev = previousWindow(range, from, to)
        val prevTotal = history
            .filter { it.direction == direction && it.occurredAt >= prev.first && it.occurredAt < prev.second }
            .sumOf { it.amountCents }
        val delta = StatsDelta(
            cents = total - prevTotal,
            percent = if (prevTotal > 0) ((total - prevTotal) * 100.0 / prevTotal).roundToInt() else null,
        )
        val buckets = when (range) {
            // The 周报 chart is always the last five weeks, so it reads the whole history, not just this week.
            StatsRange.Week -> weekBuckets(history.filter { it.direction == direction }, startOfWeek(anchor), now)
            StatsRange.Month -> dayBuckets(matching, from, to)
            StatsRange.Year -> monthBuckets(matching, from)
            StatsRange.Custom -> dayBuckets(matching, from, to)
        }
        val entries = composition(matching, total, grouping)
        return StatsSnapshot(
            range = range,
            chart = if (range == StatsRange.Week) StatsChart.Bars else StatsChart.Line,
            periodTitle = periodTitle(range, from, to),
            from = from,
            to = to,
            days = days,
            direction = direction,
            total = total,
            avgPerDay = if (days > 0) total / days else 0L,
            balance = inWindow.filter { it.direction == Direction.IN }.sumOf { it.amountCents } -
                inWindow.filter { it.direction == Direction.OUT }.sumOf { it.amountCents },
            delta = delta,
            deltaCaption = when (range) {
                StatsRange.Week -> "较上周"
                StatsRange.Month -> "较上月"
                StatsRange.Year -> "较上年"
                StatsRange.Custom -> "较上一区间"
            },
            activeDays = inWindow.map { startOfDay(it.occurredAt) }.distinct().size,
            buckets = buckets,
            entries = entries,
            xTickEvery = when (range) {
                StatsRange.Week -> 1
                StatsRange.Month -> ((buckets.size + 7) / 8).coerceAtLeast(1)
                StatsRange.Year -> 1
                StatsRange.Custom -> ((buckets.size + 5) / 6).coerceAtLeast(1)
            },
            highlightIndex = buckets.indexOfLast { it.start <= now }.coerceAtLeast(0),
        )
    }

    // ── buckets ───────────────────────────────────────────────────────────────────────────────

    private fun weekBuckets(matching: List<Txn>, currentWeekStart: Long, now: Long): List<StatsBucket> =
        (4 downTo 0).map { back ->
            val start = addDays(currentWeekStart, -7 * back)
            val end = addDays(start, 7)
            val items = matching.filter { it.occurredAt >= start && it.occurredAt < end }
            StatsBucket(
                start = start,
                top = "${md(start)}-${md(end - DAY_MS)}",
                bottom = "${yearOf(start)}/${weekOfYear(start)}周",
                cents = items.sumOf { it.amountCents },
                count = items.size,
                isCurrent = start == currentWeekStart,
            )
        }

    private fun dayBuckets(matching: List<Txn>, from: Long, to: Long): List<StatsBucket> {
        val days = ((to - from) / DAY_MS).toInt().coerceIn(0, 400)
        return (0 until days).map { i ->
            val start = addDays(from, i)
            val items = matching.filter { startOfDay(it.occurredAt) == start }
            StatsBucket(
                start = start,
                top = md(start),
                bottom = weekdayLabel(start),
                cents = items.sumOf { it.amountCents },
                count = items.size,
                isCurrent = false,
            )
        }
    }

    private fun monthBuckets(matching: List<Txn>, yearStart: Long): List<StatsBucket> =
        (0 until 12).map { i ->
            val start = startOfMonth(addMonths(yearStart, i))
            val end = endOfMonth(start)
            val items = matching.filter { it.occurredAt >= start && it.occurredAt < end }
            StatsBucket(
                start = start,
                top = "${monthOf(start)}月",
                bottom = "",
                cents = items.sumOf { it.amountCents },
                count = items.size,
                isCurrent = false,
            )
        }

    // ── composition ───────────────────────────────────────────────────────────────────────────

    private fun composition(matching: List<Txn>, total: Cents, grouping: StatsGrouping): List<StatsEntry> {
        if (matching.isEmpty()) return emptyList()
        val groups: Map<String, List<Txn>> = when (grouping) {
            StatsGrouping.Primary -> matching.groupBy { it.category.name }
            StatsGrouping.Secondary -> matching.groupBy { it.merchant.trim().ifEmpty { "未命名" } }
        }
        return groups.map { (key, items) ->
            val cents = items.sumOf { it.amountCents }
            val label = when (grouping) {
                StatsGrouping.Primary -> items.first().category.label
                StatsGrouping.Secondary -> key
            }
            StatsEntry(
                key = key,
                label = label,
                category = items.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key ?: items.first().category,
                cents = cents,
                count = items.size,
                share = if (total > 0) cents.toFloat() / total else 0f,
                detail = if (grouping == StatsGrouping.Primary) {
                    items.groupingBy { it.merchant.trim().ifEmpty { "未命名" } }
                        .eachCount().maxByOrNull { it.value }?.key
                } else null,
            )
        }.sortedByDescending { it.cents }
    }

    private fun elapsedDays(range: StatsRange, from: Long, to: Long, now: Long): Int {
        val total = ((to - from) / DAY_MS).toInt().coerceAtLeast(1)
        if (range == StatsRange.Custom) return total
        if (now >= to) return total
        if (now < from) return 0
        return (daysBetween(from, now) + 1).coerceIn(1, total)
    }

    // ── formatting ────────────────────────────────────────────────────────────────────────────

    /** 千分位 + 两位小数（整数元时不带小数），KPI 与排行都用它。 */
    fun grouped(cents: Cents, withDecimals: Boolean = true): String {
        val abs = abs(cents)
        val whole = (abs / 100).toString()
        val grouped = StringBuilder()
        whole.reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) grouped.append(',')
            grouped.append(c)
        }
        val head = grouped.reverse().toString()
        val tail = if (withDecimals && abs % 100 != 0L) ".%02d".format(abs % 100) else ""
        val sign = if (cents < 0) "-" else ""
        return "$sign$head$tail"
    }

    fun md(ms: Long): String = "${monthOf(ms)}/${dayOf(ms)}"
    fun ymd(ms: Long): String = "${yearOf(ms)}/${monthOf(ms)}/${dayOf(ms)}"

    private fun yearOf(ms: Long): Int = cal(ms).get(Calendar.YEAR)
    private fun monthOf(ms: Long): Int = cal(ms).get(Calendar.MONTH) + 1
    private fun dayOf(ms: Long): Int = cal(ms).get(Calendar.DAY_OF_MONTH)
    private fun weekOfYear(ms: Long): Int = cal(ms).get(Calendar.WEEK_OF_YEAR)

    private fun weekdayLabel(ms: Long): String {
        val names = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        return names[cal(ms).get(Calendar.DAY_OF_WEEK) - 1]
    }

    private fun cal(ms: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ms }
}

/** The AI card's copy: facts only — biggest category, 环比 direction, days recorded. Null hides the card. */
object StatsNarrative {
    fun line(snapshot: StatsSnapshot): String? {
        if (snapshot.isEmpty) return null
        val top = snapshot.entries.firstOrNull() ?: return null
        val noun = if (snapshot.direction == Direction.OUT) "支出" else "收入"
        val share = (top.share * 100).roundToInt()
        val head = "本期最大$noun 是「${top.label}」，占 $share%"
        val percent = snapshot.delta?.percent
        val trend = when {
            percent == null -> null
            percent > 0 -> "比上一期多 $percent%"
            percent < 0 -> "比上一期少 ${-percent}%"
            else -> "与上一期持平"
        }
        val days = "记账 ${snapshot.activeDays} 天"
        return listOfNotNull(head, trend, days).joinToString("；") + "。"
    }
}
