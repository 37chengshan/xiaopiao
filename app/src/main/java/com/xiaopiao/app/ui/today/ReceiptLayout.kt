package com.xiaopiao.app.ui.today

import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.DailyReceipt
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import java.util.Calendar
import kotlin.math.roundToInt

/** Kinds of printable line; each has a fixed paper height so the print timeline stays pure (docs/DESIGN.md §7). */
enum class LineKind(val heightDp: Float) {
    BLANK(10f), TITLE(48f), SUBTITLE(22f), DEMO(22f), DASH(14f), META(22f), TOTAL(80f), NOTICE(24f),
    INCOME(22f), HEADER(24f), CATEGORY(26f), ITEM(21f), MORE(22f), STATS(30f), COMMENT(22f), BIG(70f),
    STAMP_SPACE(100f), FOOTER(28f), SHARE_BAR(24f), BARCODE(46f),
}

/**
 * One printable row. [left] / [right] are the two ends of the row (a STATS row also carries the
 * week-day number in [extra]), [cents] the raw amount (TOTAL / ITEM / CATEGORY) and [positive] marks income rows.
 */
data class ReceiptLine(
    val kind: LineKind,
    val left: String = "",
    val right: String = "",
    val cents: Cents = 0,
    val positive: Boolean = false,
    val extra: Int = 0,
)

/** Facts about the moment of printing that are not part of the day itself. */
data class ReceiptMeta(val printedAtMs: Long = 0L, val previousOutCents: Cents? = null)

/** Turns a [DailyReceipt] into fixed-width printable lines (grouped by category, ellipsis, wrapping). Pure and testable. */
object ReceiptLayout {
    const val COLS = 30
    const val COMMENT_COLS = 26
    /** Item rows shown under their category before "还有 N 笔" kicks in (collapsed = what gets printed on screen). */
    const val MAX_ROWS = 6
    const val DEFAULT_STAMP = "钱包休眠者"
    const val TAGLINE = "认真生活，留下凭证。"
    const val NOTICE_TEXT = "仅作记录 · 非正式票据"
    const val FOOTER_TEXT = "— 今日小票 · 存根联 —"
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

    /** "¥88.00": always two decimals (the big total and subtotal columns). */
    fun moneyFixed(cents: Cents, sign: String = ""): String {
        val abs = kotlin.math.abs(cents)
        val whole = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        return "$sign¥$whole.%02d".format(abs % 100)
    }

    fun dayLabel(dayStart: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = dayStart }
        return "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日 ${WEEKDAYS[c.get(Calendar.DAY_OF_WEEK) - 1]}"
    }

    /** "2026-10-27 周二". */
    fun isoDay(dayStart: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = dayStart }
        return "%04d-%02d-%02d %s".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), WEEKDAYS[c.get(Calendar.DAY_OF_WEEK) - 1])
    }

    fun clock(ms: Long): String {
        if (ms <= 0L) return "--:--"
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    /** 1..7, Monday first ("本周第 3 天"). */
    fun weekDayNumber(dayStart: Long): Int {
        val dow = Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_WEEK)
        return (dow + 5) % 7 + 1
    }

    /** "较昨日 −12%"; a dash when there is no sensible baseline. */
    fun deltaText(todayOut: Cents, previousOut: Cents?, isToday: Boolean): String {
        val base = if (isToday) "较昨日" else "较前日"
        if (previousOut == null || previousOut <= 0L) return "$base —"
        val pct = ((todayOut - previousOut) * 100.0 / previousOut).roundToInt()
        return when {
            pct == 0 -> "$base 持平"
            pct > 0 -> "$base +$pct%"
            else -> "$base −${-pct}%"
        }
    }

    fun stampText(receipt: DailyReceipt): String =
        receipt.badge?.title?.takeIf { it.isNotBlank() } ?: DEFAULT_STAMP

    /**
     * [expanded] prints every item (the share sheet's 完整); otherwise rows beyond [MAX_ROWS] fold into "还有 N 笔".
     * [brief] (简版) prints category subtotals only.
     */
    fun build(
        receipt: DailyReceipt,
        isToday: Boolean,
        demo: Boolean,
        expanded: Boolean = false,
        brief: Boolean = false,
        meta: ReceiptMeta = ReceiptMeta(),
        content: ReceiptContent = ReceiptContent.PLAIN,
    ): List<ReceiptLine> {
        val out = mutableListOf<ReceiptLine>()
        fun dash() { if (out.lastOrNull()?.kind != LineKind.DASH) out += ReceiptLine(LineKind.DASH) }
        val spend = receipt.items.filter { it.direction == Direction.OUT }
        out += ReceiptLine(LineKind.BLANK)
        out += ReceiptLine(LineKind.TITLE, if (isToday) "今日小票" else "往日小票")
        out += ReceiptLine(LineKind.SUBTITLE, TAGLINE)
        out += ReceiptLine(LineKind.DASH)
        out += ReceiptLine(LineKind.META, "日期", isoDay(receipt.dayStart))
        out += ReceiptLine(LineKind.META, "出票", clock(meta.printedAtMs))
        out += ReceiptLine(LineKind.DASH)
        if (content.total) {
            if (spend.isEmpty()) {
                out += ReceiptLine(LineKind.BIG, "今日无消费")
            } else {
                out += ReceiptLine(LineKind.TOTAL, if (isToday) "今日支出" else "当日支出", moneyFixed(receipt.totalOutCents), receipt.totalOutCents)
            }
            out += ReceiptLine(LineKind.NOTICE, NOTICE_TEXT)
            val income = receipt.items.filter { it.direction == Direction.IN }.sumOf { it.amountCents }
            if (income > 0) out += ReceiptLine(LineKind.INCOME, "收入", money(income, "+"), income, positive = true)
        }
        if (receipt.items.isNotEmpty()) {
            if (content.items || brief) {
                dash()
                out += ReceiptLine(LineKind.HEADER, "分类 / 明细", "金额")
                out += groupLines(receipt.items, expanded, brief)
            }
            if (content.bars) {
                val bars = shareBars(spend)
                if (bars.isNotEmpty()) { dash(); out += bars }
            }
            if (content.weekCompare && !brief) {
                dash()
                out += ReceiptLine(
                    LineKind.STATS, "笔数 ${receipt.items.size}", deltaText(receipt.totalOutCents, meta.previousOutCents, isToday),
                    extra = weekDayNumber(receipt.dayStart),
                )
            }
        }
        if (content.remark) {
            dash()
            val remark = wrap(receipt.aiComment, COMMENT_COLS)
            remark.forEachIndexed { i, text ->
                val open = if (i == 0) "「" else ""
                val close = if (i == remark.lastIndex) "」" else ""
                out += ReceiptLine(LineKind.COMMENT, "$open$text$close")
            }
        }
        if (content.stamp) out += ReceiptLine(LineKind.STAMP_SPACE, stampText(receipt))
        else dash()
        if (content.barcode) out += ReceiptLine(LineKind.BARCODE, serialFor(receipt))
        out += ReceiptLine(LineKind.FOOTER, FOOTER_TEXT)
        out += ReceiptLine(LineKind.BLANK)
        return out
    }

    /** Top three spending categories as share bars: [ReceiptLine.extra] is the percentage of the day's spend (0..100). */
    fun shareBars(spend: List<Txn>): List<ReceiptLine> {
        val total = spend.sumOf { it.amountCents }
        if (total <= 0L) return emptyList()
        return spend.groupBy { it.category }
            .map { (c, list) -> c to list.sumOf { it.amountCents } }
            .sortedByDescending { it.second }
            .take(3)
            .map { (c, sum) ->
                val pct = (sum * 100.0 / total).roundToInt().coerceIn(0, 100)
                ReceiptLine(LineKind.SHARE_BAR, "${c.emoji} ${c.label}", "$pct%", sum, extra = pct)
            }
    }

    /** "NO.20261028-0731": the day plus a stable four-digit hash of what was printed, so a reprint carries the same number. */
    fun serialFor(receipt: DailyReceipt): String {
        val c = Calendar.getInstance().apply { timeInMillis = receipt.dayStart }
        val seed = receipt.totalOutCents * 31 + receipt.items.size * 7 + receipt.dayStart / 86_400_000L
        return "NO.%04d%02d%02d-%04d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), kotlin.math.abs(seed) % 10_000)
    }

    /** Category rows with subtotals (biggest first); a one-item category stays a single row unless [expanded]. */
    private fun groupLines(items: List<Txn>, expanded: Boolean, brief: Boolean): List<ReceiptLine> {
        val rows = mutableListOf<ReceiptLine>()
        val groups = items.filter { it.direction == Direction.OUT }.groupBy { it.category }
            .map { (c, list) -> Triple(c, list.sortedBy { it.occurredAt }, list.sumOf { it.amountCents }) }
            .sortedByDescending { it.third }
        var budget = if (expanded) Int.MAX_VALUE else MAX_ROWS
        var hidden = 0
        for ((category, list, sum) in groups) {
            rows += ReceiptLine(LineKind.CATEGORY, "${category.emoji} ${category.label}", moneyFixed(sum), sum)
            if (brief) continue
            if (list.size == 1 && !expanded) continue
            for (t in list) {
                if (budget > 0) { rows += itemLine(t); budget-- } else hidden++
            }
        }
        if (!brief) items.filter { it.direction == Direction.IN }.forEach { rows += itemLine(it) }
        if (hidden > 0) rows += ReceiptLine(LineKind.MORE, "…另有 $hidden 笔明细", extra = hidden)
        return rows
    }

    private fun itemLine(t: Txn): ReceiptLine {
        val income = t.direction == Direction.IN
        val right = if (income) money(t.amountCents, "+") else moneyFixed(t.amountCents)
        val budget = (COLS - 4 - displayWidth(right) - 1).coerceAtLeast(4)
        return ReceiptLine(LineKind.ITEM, ellipsize(t.merchant.ifBlank { t.category.label }, budget), right, t.amountCents, income)
    }

    /** Plain-text version for 复制文字 and the share fallback. */
    fun toPlainText(lines: List<ReceiptLine>): String = lines.mapNotNull { l ->
        fun row(left: String, right: String, indent: String = "") =
            indent + left + " ".repeat((COLS - displayWidth(indent) - displayWidth(left) - displayWidth(right)).coerceAtLeast(1)) + right
        when (l.kind) {
            LineKind.DASH -> "-".repeat(COLS)
            LineKind.TITLE, LineKind.SUBTITLE, LineKind.DEMO, LineKind.NOTICE, LineKind.FOOTER, LineKind.COMMENT -> l.left
            LineKind.BIG -> l.left
            LineKind.META, LineKind.HEADER, LineKind.INCOME, LineKind.CATEGORY -> row(l.left, l.right)
            LineKind.ITEM -> row(l.left, l.right, "   ")
            LineKind.TOTAL -> "${l.left} ${l.right}"
            LineKind.STATS -> "${l.left}  ${l.right}  本周第 ${l.extra} 天"
            LineKind.STAMP_SPACE -> "[ ${l.left} ]"
            LineKind.MORE -> l.left
            LineKind.SHARE_BAR -> row(l.left, l.right)
            LineKind.BARCODE -> l.left
            LineKind.BLANK -> null
        }
    }.joinToString("\n")

    /** 简版 for sharing: title strip, hero total, one AI remark and the stamp. No metadata, rows or stats. */
    fun brief(lines: List<ReceiptLine>): List<ReceiptLine> {
        val keep = setOf(
            LineKind.BLANK, LineKind.TITLE, LineKind.SUBTITLE, LineKind.DEMO, LineKind.DASH, LineKind.TOTAL, LineKind.BIG,
            LineKind.NOTICE, LineKind.COMMENT, LineKind.STAMP_SPACE, LineKind.FOOTER, LineKind.SHARE_BAR,
        )
        val out = mutableListOf<ReceiptLine>()
        for (l in lines) {
            if (l.kind !in keep) continue
            if (l.kind == LineKind.DASH && out.lastOrNull()?.kind == LineKind.DASH) continue
            out += l
        }
        return out
    }

    /** Total paper height in dp. */
    fun heightDp(lines: List<ReceiptLine>): Float = lines.sumOf { it.kind.heightDp.toDouble() }.toFloat()

    /** Top offset (dp) of the stamp area, or -1 when absent. */
    fun stampTopDp(lines: List<ReceiptLine>): Float {
        var y = 0f
        for (l in lines) { if (l.kind == LineKind.STAMP_SPACE) return y; y += l.kind.heightDp }
        return -1f
    }
}
