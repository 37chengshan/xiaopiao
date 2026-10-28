package com.xiaopiao.app.merchant

import kotlinx.serialization.Serializable
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where a day's number came from. [SEED] rows are mock data: recognisable here in code, not labelled in the UI (tt#54); audit before submission. */
@Serializable
enum class RevenueSource(val label: String) {
    SEED("演示"),
    MANUAL("手动"),
    CSV("导入"),
}

/** One day of the shop's own revenue book. [orders] stays 0 when the owner only knows the total. */
@Serializable
data class RevenueEntry(
    val dayStart: Long,
    val revenueCents: Long,
    val orders: Int = 0,
    val note: String = "",
    val source: RevenueSource = RevenueSource.MANUAL,
) {
    val seeded: Boolean get() = source == RevenueSource.SEED
    val averageTicketCents: Long get() = RevenueBook.averageTicketCents(revenueCents, orders)
}

/** One calendar day in a chart or comparison. [source] is null on days without an entry. */
data class RevenuePoint(
    val dayStart: Long,
    val revenueCents: Long,
    val orders: Int = 0,
    val source: RevenueSource? = null,
) {
    val hasEntry: Boolean get() = source != null
    val averageTicketCents: Long get() = RevenueBook.averageTicketCents(revenueCents, orders)
}

/** [now] versus a baseline. [pct] is null when the baseline is zero (nothing meaningful to divide by). */
data class Change(val nowCents: Long, val beforeCents: Long) {
    val diffCents: Long get() = nowCents - beforeCents
    val pct: Float? get() = if (beforeCents <= 0L) null else diffCents.toFloat() / beforeCents
    val hasBase: Boolean get() = beforeCents > 0L
}

data class WeekdayAverage(val index: Int, val label: String, val averageCents: Long, val days: Int)

data class MonthGroup(
    val key: String,
    val label: String,
    val totalCents: Long,
    val orders: Int,
    val entries: List<RevenueEntry>,
) {
    val days: Int get() = entries.size
    /** A month is demo only when every row in it is seeded. */
    val allSeeded: Boolean get() = entries.isNotEmpty() && entries.all { it.seeded }
}

/**
 * The revenue book: pure day/week math over [RevenueEntry] rows. No Android, no clock — every function
 * takes the book and today's day-start so the same numbers can be tested and said out loud.
 */
object RevenueBook {
    val weekdayShort = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    // ---- day math -------------------------------------------------------------------------------

    fun startOfDay(epochMillis: Long): Long = com.xiaopiao.app.domain.startOfDay(epochMillis)

    fun plusDays(dayStart: Long, days: Int): Long {
        val c = Calendar.getInstance().apply { timeInMillis = dayStart }
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_MONTH, days)
        return c.timeInMillis
    }

    /** 0 = Monday … 6 = Sunday. */
    fun weekdayIndex(dayStart: Long): Int {
        val dow = Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_WEEK)
        return (dow + 5) % 7
    }

    fun weekdayLabel(dayStart: Long): String = weekdayShort[weekdayIndex(dayStart)]

    fun weekStart(dayStart: Long): Long = plusDays(dayStart, -weekdayIndex(dayStart))

    fun monthKey(dayStart: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = dayStart }
        return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    /** "2026年10月". */
    fun monthLabel(dayStart: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = dayStart }
        return "${c.get(Calendar.YEAR)}年${c.get(Calendar.MONTH) + 1}月"
    }

    /** "10月28日 周三". */
    fun dayLabel(dayStart: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = dayStart }
        return "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日 ${weekdayLabel(dayStart)}"
    }

    /** "10/28". */
    fun shortDate(dayStart: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = dayStart }
        return "${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)}"
    }

    /** "2026-10-28". */
    fun isoDate(dayStart: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = dayStart }
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** "¥6,420.5": grouped thousands, decimals only when the amount has them. */
    fun money(cents: Long, sign: String = ""): String {
        val a = abs(cents)
        val prefix = if (cents < 0) "-$sign" else sign
        val whole = (a / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        val frac = when {
            a % 100 == 0L -> ""
            a % 10 == 0L -> ".${(a % 100) / 10}"
            else -> ".%02d".format(a % 100)
        }
        return "$prefix¥$whole$frac"
    }

    // ---- book -----------------------------------------------------------------------------------

    /** Drops negative rows, keeps one row per day (last wins) and sorts oldest → newest. */
    fun normalize(entries: List<RevenueEntry>): List<RevenueEntry> =
        entries
            .filter { it.revenueCents >= 0L && it.orders >= 0 }
            .groupBy { it.dayStart }
            .map { (_, rows) -> rows.last() }
            .sortedBy { it.dayStart }

    fun entryFor(entries: List<RevenueEntry>, dayStart: Long): RevenueEntry? =
        entries.lastOrNull { it.dayStart == dayStart }

    fun totalCents(entries: List<RevenueEntry>): Long = entries.sumOf { it.revenueCents }

    fun totalOrders(entries: List<RevenueEntry>): Int = entries.sumOf { it.orders }

    /** Rounded yuan-per-order; 0 when the order count is unknown. */
    fun averageTicketCents(revenueCents: Long, orders: Int): Long =
        if (orders <= 0) 0L else (revenueCents + orders / 2) / orders

    /** Weighted by orders, so a big day counts more than a slow one. */
    fun averageTicketCents(entries: List<RevenueEntry>): Long =
        averageTicketCents(entries.sumOf { it.revenueCents }, entries.sumOf { it.orders })

    /** Existing rows between [from] and [to] inclusive. */
    fun window(entries: List<RevenueEntry>, from: Long, to: Long): List<RevenueEntry> =
        entries.filter { it.dayStart in from..to }.sortedBy { it.dayStart }

    fun windowTotal(entries: List<RevenueEntry>, from: Long, to: Long): Long =
        window(entries, from, to).sumOf { it.revenueCents }

    /** [days] points ending at [endDay], oldest first, zero-filled for days without an entry. */
    fun series(entries: List<RevenueEntry>, endDay: Long, days: Int): List<RevenuePoint> {
        val byDay = entries.associateBy { it.dayStart }
        return (0 until days).map { i ->
            val day = plusDays(endDay, i - (days - 1))
            val e = byDay[day]
            RevenuePoint(day, e?.revenueCents ?: 0L, e?.orders ?: 0, e?.source)
        }
    }

    fun upsert(entries: List<RevenueEntry>, entry: RevenueEntry): List<RevenueEntry> =
        normalize(entries.filterNot { it.dayStart == entry.dayStart } + entry)

    /** Import merge: incoming rows win per day, everything else stays. */
    fun merge(entries: List<RevenueEntry>, incoming: List<RevenueEntry>): List<RevenueEntry> {
        val days = incoming.map { it.dayStart }.toSet()
        return normalize(entries.filterNot { it.dayStart in days } + incoming)
    }

    fun groupByMonth(entries: List<RevenueEntry>): List<MonthGroup> =
        normalize(entries)
            .groupBy { monthKey(it.dayStart) }
            .toSortedMap(compareByDescending { it })
            .map { (key, rows) ->
                MonthGroup(
                    key = key,
                    label = monthLabel(rows.last().dayStart),
                    totalCents = rows.sumOf { it.revenueCents },
                    orders = rows.sumOf { it.orders },
                    entries = rows.sortedByDescending { it.dayStart },
                )
            }

    // ---- comparisons ----------------------------------------------------------------------------

    /** Today versus yesterday. Both numbers are 0 on a day without an entry. */
    fun todayVsYesterday(entries: List<RevenueEntry>, today: Long): Change =
        Change(
            nowCents = entryFor(entries, today)?.revenueCents ?: 0L,
            beforeCents = entryFor(entries, plusDays(today, -1))?.revenueCents ?: 0L,
        )

    /** Today versus the same weekday last week — the fair comparison for a shop. */
    fun sameWeekdayChange(entries: List<RevenueEntry>, today: Long): Change =
        Change(
            nowCents = entryFor(entries, today)?.revenueCents ?: 0L,
            beforeCents = entryFor(entries, plusDays(today, -7))?.revenueCents ?: 0L,
        )

    /** Monday → today of this week versus the same weekday span last week. */
    fun weekToDateChange(entries: List<RevenueEntry>, today: Long): Change {
        val start = weekStart(today)
        return Change(
            nowCents = windowTotal(entries, start, today),
            beforeCents = windowTotal(entries, plusDays(start, -7), plusDays(today, -7)),
        )
    }

    /** Average ticket of the last 7 days versus the 7 days before them. */
    fun averageTicketChange(entries: List<RevenueEntry>, today: Long): Change =
        Change(
            nowCents = averageTicketCents(window(entries, plusDays(today, -6), today)),
            beforeCents = averageTicketCents(window(entries, plusDays(today, -13), plusDays(today, -7))),
        )

    /** Best recorded day in the window; ties go to the later day. */
    fun bestDay(entries: List<RevenueEntry>, today: Long, days: Int = 14): RevenuePoint? {
        val rows = window(entries, plusDays(today, -(days - 1)), today)
        val best = rows.maxWithOrNull(compareBy<RevenueEntry> { it.revenueCents }.thenBy { it.dayStart }) ?: return null
        return RevenuePoint(best.dayStart, best.revenueCents, best.orders, best.source)
    }

    /** Quietest recorded day in the window; days with no entry are not "quiet", they are unknown. */
    fun worstDay(entries: List<RevenueEntry>, today: Long, days: Int = 14): RevenuePoint? {
        val rows = window(entries, plusDays(today, -(days - 1)), today)
        val worst = rows.minWithOrNull(compareBy<RevenueEntry> { it.revenueCents }.thenBy { it.dayStart }) ?: return null
        return RevenuePoint(worst.dayStart, worst.revenueCents, worst.orders, worst.source)
    }

    /** Average revenue per weekday over the last [weeks] weeks, ignoring days without an entry. */
    fun weekdayAverages(entries: List<RevenueEntry>, today: Long, weeks: Int = 4): List<WeekdayAverage> {
        val rows = window(entries, plusDays(today, -(weeks * 7 - 1)), today)
        return (0..6).map { index ->
            val ofDay = rows.filter { weekdayIndex(it.dayStart) == index }
            WeekdayAverage(
                index = index,
                label = weekdayShort[index],
                averageCents = if (ofDay.isEmpty()) 0L else ofDay.sumOf { it.revenueCents } / ofDay.size,
                days = ofDay.size,
            )
        }
    }

    /**
     * Consecutive days, ending at the latest recorded day, that each earned less than the day before.
     * 0 means the latest day did not fall (or there is nothing to compare it with).
     */
    fun declineStreak(entries: List<RevenueEntry>, today: Long): Int {
        val byDay = normalize(entries).associateBy { it.dayStart }
        var day = byDay.keys.filter { it <= today }.maxOrNull() ?: return 0
        var streak = 0
        while (true) {
            val current = byDay[day] ?: break
            val previous = byDay[plusDays(day, -1)] ?: break
            if (current.revenueCents >= previous.revenueCents) break
            streak++
            day = plusDays(day, -1)
        }
        return streak
    }

    /** Finishing total of the decline streak, for the warning sentence ("从 ¥x 到 ¥y"). */
    fun declineRange(entries: List<RevenueEntry>, today: Long, streak: Int): Pair<Long, Long>? {
        if (streak <= 0) return null
        val byDay = normalize(entries).associateBy { it.dayStart }
        val last = byDay.keys.filter { it <= today }.maxOrNull() ?: return null
        val high = byDay[plusDays(last, -streak)]?.revenueCents ?: return null
        val low = byDay[last]?.revenueCents ?: return null
        return high to low
    }

    /** "▲ 12%" style suffix used by the cards; null when there is no baseline. */
    fun percentText(pct: Float?): String? = pct?.let {
        val points = (it * 100).roundToInt()
        (if (points > 0) "+" else "") + "$points%"
    }
}
