package com.xiaopiao.app.ui.ledger

import com.xiaopiao.app.capture.Amounts
import com.xiaopiao.app.capture.QuickDraft
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.domain.yuan
import java.util.Calendar
import kotlin.math.abs

/** One calendar day of the ledger, newest first. [header] is the day card's title line. */
data class DayGroup(
    val dayStart: Long,
    val header: String,
    val items: List<Txn>,
    val outCents: Cents,
    val inCents: Cents,
)

/** What the summary card states: today's spend plus this month's both directions. */
data class LedgerSummary(
    val todayOutCents: Cents,
    val monthOutCents: Cents,
    val monthInCents: Cents,
)

/** Placeholder for every amount while 「隐藏金额」 is on (docs/APP-DESIGN.md §3.7). */
const val AMOUNT_MASK = "···"

/** Pure ledger presentation logic (no Compose, no Android) so it can be unit-tested. */
object LedgerGrouping {
    private val weekdays = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

    fun group(txns: List<Txn>, now: Long): List<DayGroup> =
        txns.sortedByDescending { it.occurredAt }
            .groupBy { startOfDay(it.occurredAt) }
            .map { (day, items) ->
                DayGroup(
                    dayStart = day,
                    header = header(day, now),
                    items = items,
                    outCents = items.filter { it.direction == Direction.OUT }.sumOf { it.amountCents },
                    inCents = items.filter { it.direction == Direction.IN }.sumOf { it.amountCents },
                )
            }

    /** Day card title: 「10月28日 今天」 — date first, the relative word is only a suffix (§3.7 A 10.1#5). */
    fun header(dayStart: Long, now: Long): String {
        val date = shortDate(dayStart).substringBefore(' ')
        val suffix = when (val diff = Math.round((startOfDay(now) - dayStart) / 86_400_000.0).toInt()) {
            0 -> "今天"
            1 -> "昨天"
            else -> if (diff > 1) weekdays[Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_WEEK) - 1] else ""
        }
        return if (suffix.isEmpty()) date else "$date $suffix"
    }

    /** Relative word only (今天 / 昨天 / 前天 / else the weekday) — used by the record sheet's date chips. */
    fun dayLabel(dayStart: Long, now: Long): String {
        val today = startOfDay(now)
        val diff = Math.round((today - dayStart) / 86_400_000.0).toInt()
        return when (diff) {
            0 -> "今天"
            1 -> "昨天"
            2 -> "前天"
            else -> shortDate(dayStart)
        }
    }

    fun shortDate(epochMillis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = epochMillis }
        return "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日 ${weekdays[c.get(Calendar.DAY_OF_WEEK) - 1]}"
    }

    fun clock(epochMillis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = epochMillis }
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }
}

/** Day / month boundaries used by the summary card and the filter sheet. */
object LedgerRanges {
    fun monthStart(now: Long): Long = startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = startOfDay(now)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis,
    )

    fun nextMonthStart(now: Long): Long = startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = monthStart(now)
            add(Calendar.MONTH, 1)
        }.timeInMillis,
    )

    fun lastMonthStart(now: Long): Long = startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = monthStart(now)
            add(Calendar.MONTH, -1)
        }.timeInMillis,
    )

    fun yearStart(now: Long): Long = startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = startOfDay(now)
            set(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis,
    )

    fun weekStart(now: Long): Long = startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = startOfDay(now)
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }.timeInMillis,
    )
}

/** Today's spend + this month's two columns, computed once per data change. */
object LedgerTotals {
    fun summary(txns: List<Txn>, now: Long): LedgerSummary {
        val today = startOfDay(now)
        val monthFrom = LedgerRanges.monthStart(now)
        var todayOut = 0L
        var monthOut = 0L
        var monthIn = 0L
        txns.forEach { t ->
            if (t.direction == Direction.OUT && t.occurredAt >= today) todayOut += t.amountCents
            if (t.occurredAt >= monthFrom) {
                if (t.direction == Direction.OUT) monthOut += t.amountCents else monthIn += t.amountCents
            }
        }
        return LedgerSummary(todayOut, monthOut, monthIn)
    }
}

/** Amount text for one row: spends carry an explicit minus, incomes a plus (§3.7). */
fun signedAmount(cents: Cents, direction: Direction): String =
    (if (direction == Direction.IN) "+" else "-") + abs(cents).yuan()

/** One day is a search hit when merchant, note, category, source or the amount text contains the query. */
fun Txn.matchesQuery(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return merchant.contains(q, ignoreCase = true) ||
        note.contains(q, ignoreCase = true) ||
        category.label.contains(q, ignoreCase = true) ||
        source.label.contains(q, ignoreCase = true) ||
        abs(amountCents).yuan().contains(q.removePrefix("¥").removePrefix("￥").trim())
}

/** Newest-first, case-insensitive, capped shortlist for the search page's empty state. */
object RecentSearches {
    const val LIMIT = 6

    fun push(recent: List<String>, query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return recent
        return (listOf(q) + recent.filterNot { it.equals(q, ignoreCase = true) }).take(LIMIT)
    }
}

/** Month grid for the 「按天跳转」 sheet and the filter sheet's custom range. */
object LedgerCalendar {
    fun title(anchor: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = anchor }
        return "${c.get(Calendar.YEAR)}年${c.get(Calendar.MONTH) + 1}月"
    }

    fun shiftMonths(anchor: Long, delta: Int): Long = startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = LedgerRanges.monthStart(anchor)
            add(Calendar.MONTH, delta)
        }.timeInMillis,
    )

    fun dayOfMonth(dayStart: Long): Int =
        Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_MONTH)

    /**
     * Day starts for one month as a 7-column grid starting on Monday; `0` marks a blank cell.
     * Rows are always full weeks so the grid keeps its shape.
     */
    fun cells(anchor: Long): List<Long> {
        val first = LedgerRanges.monthStart(anchor)
        val cal = Calendar.getInstance().apply {
            timeInMillis = first
            firstDayOfWeek = Calendar.MONDAY
        }
        val leading = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val total = ((leading + days + 6) / 7) * 7
        return List(total) { i ->
            val d = i - leading
            if (d < 0 || d >= days) 0L else startOfDay(first + d * DAY_MS)
        }
    }
}

/** Day offset 0 keeps "now"; other days land at noon so they sort sensibly inside their day. */
fun QuickDraft.toTxn(now: Long, source: Source = Source.QUICK_ADD): Txn {
    val at = if (dayOffset == 0) now else Calendar.getInstance().run {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
    return Txn(
        amountCents = amountCents, direction = direction, merchant = merchant,
        category = category, occurredAt = at, source = source,
    )
}

/** Moves [original] to [dayStart], keeping its time of day. */
fun moveToDay(original: Long, dayStart: Long): Long {
    val src = Calendar.getInstance().apply { timeInMillis = original }
    return Calendar.getInstance().run {
        timeInMillis = dayStart
        set(Calendar.HOUR_OF_DAY, src.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, src.get(Calendar.MINUTE))
        timeInMillis
    }
}

/** Keypad-style amount entry. The value is kept as text so "5." and "0.0" stay editable. */
object AmountEntry {
    private const val MAX_INT_DIGITS = 7
    const val BACKSPACE = "⌫"

    fun press(current: String, key: String): String = when (key) {
        BACKSPACE -> current.dropLast(1)
        "." -> when {
            '.' in current -> current
            current.isEmpty() -> "0."
            else -> "$current."
        }
        else -> if (key.length == 1 && key[0].isDigit()) appendDigit(current, key) else current
    }

    private fun appendDigit(current: String, digit: String): String {
        val dot = current.indexOf('.')
        return when {
            dot >= 0 -> if (current.length - dot - 1 >= 2) current else current + digit
            current == "0" -> digit
            current.length >= MAX_INT_DIGITS -> current
            else -> current + digit
        }
    }

    fun toCents(text: String): Cents = Amounts.toCents(text.trimEnd('.')) ?: 0L

    fun display(text: String): String = text.ifEmpty { "0" }

    fun fromCents(cents: Cents): String =
        if (cents % 100 == 0L) (cents / 100).toString() else "%d.%02d".format(cents / 100, cents % 100).trimEnd('0')
}
