package com.xiaopiao.app.ui.today

import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.DailyReceipt
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.insights.DailyReceiptBuilder

/** Turns the whole ledger into one day's receipt and its print lines. Pure; shared by the print ceremony and the 小票页 preview. */
object ReceiptSource {
    fun receiptFor(all: List<Txn>, day: Long): DailyReceipt =
        DailyReceiptBuilder.build(day, all.filter { it.occurredAt in day until day + DAY_MS }, all)

    fun metaFor(all: List<Txn>, day: Long, printedAtMs: Long): ReceiptMeta {
        val prev = all.filter { it.direction == Direction.OUT && it.occurredAt in (day - DAY_MS) until day }
        return ReceiptMeta(printedAtMs, if (prev.isEmpty()) null else prev.sumOf { it.amountCents })
    }

    fun todayOutCents(all: List<Txn>, today: Long): Cents =
        all.filter { it.direction == Direction.OUT && it.occurredAt in today until today + DAY_MS }.sumOf { it.amountCents }

    /** Lines for printing [day] with the user's [content] switches. */
    fun linesFor(all: List<Txn>, day: Long, today: Long, content: ReceiptContent, printedAtMs: Long, expanded: Boolean = false): List<ReceiptLine> {
        val receipt = receiptFor(all, day)
        return ReceiptLayout.build(receipt, day == today, false, expanded = expanded, meta = metaFor(all, day, printedAtMs), content = content)
    }
}

/** What the resident strip shows: today's spend in a handful of characters (a 28dp strip has room for six). */
object StripText {
    fun format(outCents: Cents): String {
        val yuan = outCents / 100
        return when {
            outCents <= 0L -> "¥0"
            outCents % 100 == 0L && yuan < 10_000 -> "¥$yuan"
            yuan < 100 -> "¥%d.%02d".format(yuan, outCents % 100)
            yuan < 1000 -> "¥%d.%d".format(yuan, (outCents % 100) / 10)
            yuan < 10_000 -> "¥$yuan"
            else -> "¥%.1f万".format(yuan / 10_000.0)
        }
    }
}
