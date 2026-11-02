package com.xiaopiao.app.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.xiaopiao.app.domain.Badge
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.DailyReceipt
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.ReceiptSnapshot
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn

/** One category total of a receipt someone posted to a circle (the server never carries merchants). */
data class SharedReceiptLine(val category: Category, val cents: Cents)

/**
 * A circle member's receipt, printed again from what they shared (tt 2026-11-02: 每个人分享出来的应该自带他那个时候的参数).
 * It goes through the same [ReceiptLayout.build] and [ReceiptPaper] as 小票, with the author's paper, ink, font and
 * printed sections from [snapshot]. The one difference: rows are category totals, because a shared card never
 * carries individual entries or merchants.
 */
@Composable
fun SharedReceiptPaper(
    dayStart: Long,
    totalCents: Cents,
    count: Int,
    lines: List<SharedReceiptLine>,
    remark: String,
    snapshot: ReceiptSnapshot?,
    isToday: Boolean,
    /** When it was posted: the print time for cards shared before snapshots carried one. */
    postedAt: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val content = snapshot?.let { ReceiptContent.fromBits(it.content) } ?: ReceiptPreset.Brief.content
    val colors = remember(snapshot) { snapshot?.let { PaperColors.resolve(PaperTheme.fromId(it.paper), it.ink) } }
    val font = snapshot?.let { ReceiptFont.fromId(it.font) }
    val stamp = snapshot?.stamp
    val receiptLines = remember(dayStart, totalCents, count, lines, remark, snapshot, isToday, postedAt) {
        val items = lines.map { l ->
            Txn(amountCents = l.cents, direction = Direction.OUT, merchant = l.category.label, category = l.category, occurredAt = dayStart, source = Source.MANUAL)
        }
        val receipt = DailyReceipt(
            dayStart = dayStart,
            items = items,
            totalOutCents = totalCents,
            aiComment = remark,
            badge = stamp?.let { Badge(code = "shared", title = it, emoji = "", reason = "") },
        )
        val meta = ReceiptMeta(
            printedAtMs = snapshot?.printedAt?.takeIf { it > 0L } ?: postedAt,
            previousOutCents = snapshot?.prevCents,
            entryCount = count,
            aggregateOnly = true,
        )
        // A remark the author did not have does not get printed as an empty quote.
        ReceiptLayout.build(receipt, isToday, demo = false, meta = meta, content = content.copy(remark = content.remark && remark.isNotBlank(), stamp = content.stamp && stamp != null))
    }
    val timeline = remember(receiptLines) { PrintTimeline.build(receiptLines, reduced = true) }
    val clock = remember(timeline) { PrintClock(timeline, instant = true) { 0L } }
    val stampVisuals = remember { StampVisuals({ 1f }, { 1f }, { 0f }) }
    CompositionLocalProvider(
        LocalPaperColors provides (colors ?: LocalPaperColors.current),
        LocalReceiptFont provides (font ?: LocalReceiptFont.current),
    ) {
        ReceiptPaper(receiptLines, clock, stamp.orEmpty(), stampVisuals, tornTop = true, modifier = modifier)
    }
}
