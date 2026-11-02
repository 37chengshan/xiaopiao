package com.xiaopiao.app.domain

import kotlinx.serialization.json.JsonNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.*

/**
 * How the author's paper looked when it was printed: paper / ink / font ids, the printed-section bit mask
 * ([com.xiaopiao.app.ui.today.ReceiptContent.toBits]), the stamp, the print time and last period's spend. Sent with
 * the card so a circle member sees the same receipt (tt 2026-11-02). No bill text in here.
 */
data class ReceiptSnapshot(
    val paper: String,
    val ink: Int?,
    val font: String,
    val content: Int,
    val stamp: String?,
    val printedAt: Long,
    val prevCents: Cents?,
)

/** Share the printed snapshot, not a new query for today's ledger. Details are opt-in aggregates. */
object ReceiptCirclePayload {
    fun build(receipt: DailyReceipt, caption: String, includeCategories: Boolean, snapshot: ReceiptSnapshot? = null): JsonObject = buildJsonObject {
        put("text", caption.trim().take(500))
        putJsonObject("receipt") {
            put("date", SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(receipt.dayStart)))
            put("totalCents", receipt.totalOutCents); put("title", "每日小票"); put("count", receipt.items.size)
            if (includeCategories) putJsonArray("lines") {
                receipt.items.groupBy { it.category }.entries.sortedByDescending { it.value.sumOf { row -> row.amountCents } }.forEach { (category, rows) ->
                    add(buildJsonObject {
                        put("label", category.label.take(16)); put("amountCents", rows.sumOf { it.amountCents }); put("count", rows.size)
                    })
                }
            }
            if (snapshot != null) {
                put("comment", receipt.aiComment.take(120))
                putJsonObject("style") {
                    put("paper", snapshot.paper)
                    if (snapshot.ink != null) put("ink", snapshot.ink.toLong() and 0xFFFFFFFFL) else put("ink", JsonNull)
                    put("font", snapshot.font)
                    put("content", snapshot.content)
                }
                snapshot.stamp?.trim()?.take(16)?.takeIf { it.isNotEmpty() }?.let { put("stamp", it) }
                put("printedAt", snapshot.printedAt)
                if (snapshot.prevCents != null) put("prevCents", snapshot.prevCents) else put("prevCents", JsonNull)
            }
        }
    }
}
