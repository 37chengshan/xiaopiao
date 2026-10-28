package com.xiaopiao.app.ui.ledger

import com.xiaopiao.app.capture.Amounts
import com.xiaopiao.app.capture.QuickDraft
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import java.util.Calendar

/** One calendar day of the ledger, newest first. */
data class DayGroup(
    val dayStart: Long,
    val label: String,
    val items: List<Txn>,
    val outCents: Cents,
    val inCents: Cents,
)

/** Pure ledger presentation logic (no Compose, no Android) so it can be unit-tested. */
object LedgerGrouping {
    private val weekdays = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

    fun group(txns: List<Txn>, now: Long): List<DayGroup> =
        txns.sortedByDescending { it.occurredAt }
            .groupBy { startOfDay(it.occurredAt) }
            .map { (day, items) ->
                DayGroup(
                    dayStart = day,
                    label = dayLabel(day, now),
                    items = items,
                    outCents = items.filter { it.direction == Direction.OUT }.sumOf { it.amountCents },
                    inCents = items.filter { it.direction == Direction.IN }.sumOf { it.amountCents },
                )
            }

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
