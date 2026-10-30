package com.xiaopiao.app.ui.chat

import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.Direction
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import kotlinx.serialization.json.*

/** Read-only capabilities; capped queries declare truncation so partial records cannot imply a full total. */
object LocalAgentTools {
    fun execute(name: String, args: JsonObject, history: List<Txn>, now: Long, zone: ZoneId = ZoneId.systemDefault()): JsonObject {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val period = args["period"]?.jsonPrimitive?.content ?: "week"
        val start = when (period) {
            "today" -> today
            "week" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            "month" -> today.withDayOfMonth(1)
            else -> return buildJsonObject { put("error", "INVALID_PERIOD") }
        }.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = history.filter { it.occurredAt in start until end }
        fun buckets(dimension: String): Map<String, List<Txn>> = rows.filter { it.direction == Direction.OUT }.groupBy {
            when (dimension) {
                "day" -> Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate().toString()
                "category" -> it.category.name
                else -> it.merchant.take(80)
            }
        }
        return when (name) {
            "summarize_period" -> buildJsonObject {
                put("period", period); put("count", rows.size)
                put("total_out", rows.filter { it.direction == Direction.OUT }.sumOf { it.amountCents })
                put("total_in", rows.filter { it.direction == Direction.IN }.sumOf { it.amountCents })
                put("unit", "cents")
                val group = args["group_by"]?.jsonPrimitive?.content ?: "category"
                require(group in listOf("day", "category"))
                putJsonArray("buckets") { buckets(group).entries.sortedByDescending { it.value.sumOf { t -> t.amountCents } }.take(31).forEach { (label, ts) -> add(buildJsonObject { put("label", label); put("cents", ts.sumOf { it.amountCents }) }) } }
            }
            "find_peak" -> buildJsonObject {
                val dimension = args["dimension"]?.jsonPrimitive?.content ?: "day"
                require(dimension in listOf("day", "category", "merchant"))
                val peak = buckets(dimension).entries.maxByOrNull { it.value.sumOf { t -> t.amountCents } }
                put("label", peak?.key); put("cents", peak?.value?.sumOf { it.amountCents }); put("unit", "cents")
            }
            "query_transactions" -> buildJsonObject {
                val from = args["from"]?.jsonPrimitive?.longOrNull ?: start
                val to = args["to"]?.jsonPrimitive?.longOrNull ?: end
                require(to > from)
                val limit = (args["limit"]?.jsonPrimitive?.intOrNull ?: 20).coerceIn(1, 20)
                val matched = history.filter { it.occurredAt in from until to }.sortedByDescending { it.occurredAt }
                put("matched_count", matched.size); put("truncated", matched.size > limit)
                putJsonArray("rows") { matched.take(limit).forEach { t -> add(buildJsonObject {
                    put("id", t.id); put("cents", t.amountCents); put("merchant", t.merchant.take(80)); put("category", t.category.name); put("direction", t.direction.name); put("occurredAt", t.occurredAt)
                }) } }
                put("unit", "cents")
            }
            "read_circle" -> buildJsonObject { put("shared", false); put("message", "没有可用的已授权圈子数据，不得使用演示成员推断") }
            else -> buildJsonObject { put("error", "TOOL_NOT_ALLOWED") }
        }
    }
}
