package com.xiaopiao.app.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.*

/** Share the printed snapshot, not a new query for today's ledger. Details are opt-in aggregates. */
object ReceiptCirclePayload {
    fun build(receipt: DailyReceipt, caption: String, includeCategories: Boolean): JsonObject = buildJsonObject {
        put("text", caption.trim().take(500))
        putJsonObject("receipt") {
            put("date", SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(receipt.dayStart)))
            put("totalCents", receipt.totalOutCents); put("title", "每日小票"); put("count", receipt.items.size)
            if (includeCategories) putJsonArray("lines") {
                receipt.items.groupBy { it.category }.entries.sortedByDescending { it.value.sumOf { row -> row.amountCents } }.forEach { (category, rows) ->
                    add(buildJsonObject { put("label", category.label.take(16)); put("amountCents", rows.sumOf { it.amountCents }) })
                }
            }
        }
    }
}
