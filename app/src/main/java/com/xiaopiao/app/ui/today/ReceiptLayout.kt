package com.xiaopiao.app.ui.today

import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.DailyReceipt
import com.xiaopiao.app.domain.Direction
import java.util.Calendar

/** Kinds of printable line; each has a fixed paper height so the print timeline stays pure. */
enum class LineKind(val heightDp: Float) {
    BLANK(18f), TITLE(40f), SUBTITLE(20f), DEMO(20f), DASH(16f), ITEM(28f), MORE(28f),
    TOTAL(46f), INCOME(24f), COMMENT(22f), BIG(60f), STAMP_SPACE(112f), FOOTER(24f), BARCODE(36f),
}

/**
 * One printable row. [left] is the monospace text, [right] the right-aligned amount text,
 * [cents] the raw amount (TOTAL only) and [positive] marks income rows.
 */
data class ReceiptLine(
    val kind: LineKind,
    val left: String = "",
    val right: String = "",
    val cents: Cents = 0,
    val positive: Boolean = false,
    val extra: Int = 0,
)

/** Turns a [DailyReceipt] into fixed-width printable lines (ellipsis, wrapping, collapse). Pure and testable. */
object ReceiptLayout {
    const val COLS = 30
    const val COMMENT_COLS = 28
    const val MAX_ROWS = 8
    const val DEFAULT_STAMP = "钱包休眠者"
    private val WEEKDAYS = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

    /** Terminal-style column width: CJK and emoji take two columns, joiners and variation selectors none. */
    fun displayWidth(text: String): Int = text.codePoints().toArray().sumOf { cpWidth(it) }

    private fun cpWidth(cp: Int): Int = when {
        cp == 0x200D || cp in 0xFE00..0xFE0F || cp in 0x0300..0x036F -> 0
        cp < 0x2E80 && cp !in 0x2600..0x27BF -> 1
        else -> 2
    }

    /** Shortens [text] to at most [maxCols] columns, ending in "…" when it had to cut. */
    fun ellipsize(text: String, maxCols: Int): String {
        if (displayWidth(text) <= maxCols) return text
        val sb = StringBuilder()
        var used = 0
        for (cp in text.codePoints().toArray()) {
            val w = cpWidth(cp)
            if (used + w > maxCols - 1) break
            sb.appendCodePoint(cp); used += w
        }
        return sb.append('…').toString()
    }

    /** Greedy wrap by column width; never returns an empty list for non-blank text. */
    fun wrap(text: String, maxCols: Int): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var used = 0
        for (cp in text.trim().codePoints().toArray()) {
            val w = cpWidth(cp)
            if (used + w > maxCols && sb.isNotEmpty()) { out += sb.toString(); sb.clear(); used = 0 }
            sb.appendCodePoint(cp); used += w
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    /** "¥1,234.5": grouped thousands, decimals only when needed. [sign] prefixes + or -. */
    fun money(cents: Cents, sign: String = ""): String {
        val abs = kotlin.math.abs(cents)
        val whole = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        val frac = when {
            abs % 100 == 0L -> ""
            abs % 10 == 0L -> ".${(abs % 100) / 10}"
            else -> ".%02d".format(abs % 100)
        }
        return "$sign¥$whole$frac"
    }

    fun dayLabel(dayStart: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = dayStart }
        return "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日 ${WEEKDAYS[c.get(Calendar.DAY_OF_WEEK) - 1]}"
    }

    fun stampText(receipt: DailyReceipt): String =
        receipt.badge?.title?.takeIf { it.isNotBlank() } ?: DEFAULT_STAMP

    fun build(receipt: DailyReceipt, isToday: Boolean, demo: Boolean, expanded: Boolean = false): List<ReceiptLine> {
        val out = mutableListOf<ReceiptLine>()
        out += ReceiptLine(LineKind.BLANK)
        out += ReceiptLine(LineKind.TITLE, if (isToday) "今日小票" else "往日小票")
        out += ReceiptLine(LineKind.SUBTITLE, dayLabel(receipt.dayStart))
        if (demo) out += ReceiptLine(LineKind.DEMO, "※ 演示数据 ※")
        out += ReceiptLine(LineKind.DASH)
        out += itemLines(receipt, expanded)
        out += ReceiptLine(LineKind.DASH)
        out += ReceiptLine(LineKind.TOTAL, "合计", money(receipt.totalOutCents), receipt.totalOutCents)
        val income = receipt.items.filter { it.direction == Direction.IN }.sumOf { it.amountCents }
        if (income > 0) out += ReceiptLine(LineKind.INCOME, "收入", money(income, "+"), income, positive = true)
        out += ReceiptLine(LineKind.DASH)
        wrap(receipt.aiComment, COMMENT_COLS).forEach { out += ReceiptLine(LineKind.COMMENT, it) }
        out += ReceiptLine(LineKind.STAMP_SPACE, stampText(receipt))
        out += ReceiptLine(LineKind.FOOTER, "*** 谢谢惠顾 · 明天见 ***")
        out += ReceiptLine(LineKind.BARCODE, extra = (receipt.dayStart / 86_400_000L).toInt())
        out += ReceiptLine(LineKind.BLANK)
        return out
    }

    private fun itemLines(receipt: DailyReceipt, expanded: Boolean): List<ReceiptLine> {
        if (receipt.items.isEmpty()) return listOf(ReceiptLine(LineKind.BIG, "今日无消费 🎉"))
        val shown = if (expanded || receipt.items.size <= MAX_ROWS) receipt.items else receipt.items.take(MAX_ROWS)
        val rows = shown.map { t ->
            val income = t.direction == Direction.IN
            val right = if (income) money(t.amountCents, "+") else money(t.amountCents)
            val budget = (COLS - 3 - displayWidth(right) - 1).coerceAtLeast(4)
            ReceiptLine(LineKind.ITEM, "${t.category.emoji} ${ellipsize(t.merchant.ifBlank { t.category.label }, budget)}", right, t.amountCents, income)
        }
        val hidden = receipt.items.size - shown.size
        return when {
            hidden > 0 -> rows + ReceiptLine(LineKind.MORE, "还有 $hidden 笔 ▾", extra = hidden)
            expanded && receipt.items.size > MAX_ROWS -> rows + ReceiptLine(LineKind.MORE, "收起 ▴", extra = -1)
            else -> rows
        }
    }

    /** Plain-text version for the share fallback when bitmap rendering fails. */
    fun toPlainText(lines: List<ReceiptLine>): String = lines.mapNotNull { l ->
        when (l.kind) {
            LineKind.DASH -> "-".repeat(COLS)
            LineKind.TITLE, LineKind.SUBTITLE, LineKind.DEMO, LineKind.FOOTER, LineKind.BIG, LineKind.COMMENT -> l.left
            LineKind.ITEM, LineKind.TOTAL, LineKind.INCOME ->
                l.left + " ".repeat((COLS - displayWidth(l.left) - displayWidth(l.right)).coerceAtLeast(1)) + l.right
            LineKind.STAMP_SPACE -> "[ ${l.left} ]"
            LineKind.MORE -> l.left
            LineKind.BLANK, LineKind.BARCODE -> null
        }
    }.joinToString("\n")

    /** Total paper height in dp. */
    fun heightDp(lines: List<ReceiptLine>): Float = lines.sumOf { it.kind.heightDp.toDouble() }.toFloat()

    /** Top offset (dp) of the stamp area, or -1 when absent. */
    fun stampTopDp(lines: List<ReceiptLine>): Float {
        var y = 0f
        for (l in lines) { if (l.kind == LineKind.STAMP_SPACE) return y; y += l.kind.heightDp }
        return -1f
    }
}
