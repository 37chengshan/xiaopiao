package com.xiaopiao.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Modular views the model may ask for (show_view). The app owns every pixel; the model only fills numbers.
 * The catalogue mirrors Web `services/views.ts` — keep both lists identical.
 */
val CHAT_VIEW_KINDS = setOf(
    "spend_summary", "category_split", "trend", "entries",
    "peak", "weekly_report", "subscriptions", "draft_list",
)

@Serializable
data class ChatViewRow(
    val label: String? = null,
    val merchant: String? = null,
    val category: String? = null,
    val cents: Long? = null,
    val cadence: String? = null,
)

@Serializable
data class ChatView(
    val kind: String,
    val title: String? = null,
    val totalCents: Long? = null,
    val count: Int? = null,
    val period: String? = null,
    val label: String? = null,
    val weekLabel: String? = null,
    val topCategory: String? = null,
    val rows: List<ChatViewRow> = emptyList(),
) {
    companion object {
        private fun text(obj: JsonObject, key: String, max: Int = 60): String? =
            (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }?.take(max)
        private fun long(obj: JsonObject, key: String): Long? =
            (obj[key] as? JsonPrimitive)?.takeIf { it.isString.not() }?.content?.toLongOrNull()?.takeIf { kotlin.math.abs(it) <= 1_000_000_000L }
        private fun rows(obj: JsonObject, key: String): List<ChatViewRow> =
            (obj[key] as? JsonArray)?.take(40)?.mapNotNull { element ->
                val row = element as? JsonObject ?: return@mapNotNull null
                val parsed = ChatViewRow(
                    label = text(row, "label"), merchant = text(row, "merchant"),
                    category = text(row, "category", 40), cents = long(row, "cents"),
                    cadence = text(row, "cadence", 20),
                )
                parsed.takeIf { it.label != null || it.merchant != null || it.cents != null }
            }.orEmpty()

        /** Null means the payload cannot be rendered safely; the caller must not guess. */
        fun parse(args: JsonObject): ChatView? {
            val kind = text(args, "kind", 24) ?: return null
            if (kind !in CHAT_VIEW_KINDS) return null
            val view = ChatView(
                kind = kind,
                title = text(args, "title", 80),
                totalCents = long(args, "total_cents"),
                count = long(args, "count")?.toInt()?.takeIf { it >= 0 },
                period = text(args, "period", 8),
                label = text(args, "label", 80),
                weekLabel = text(args, "week_label", 40),
                topCategory = text(args, "top_category", 40),
                rows = rows(args, if (kind == "trend") "points" else "rows"),
            )
            val ready = when (view.kind) {
                "spend_summary" -> view.totalCents != null
                "category_split", "entries", "subscriptions", "draft_list" -> view.rows.isNotEmpty()
                "trend" -> view.rows.isNotEmpty()
                "peak" -> view.label != null && view.totalCents != null
                "weekly_report" -> view.totalCents != null
                else -> false
            }
            return view.takeIf { ready }
        }
    }
}

private fun periodLabel(period: String?): String? = when (period) {
    "today" -> "今天"; "week" -> "本周"; "month" -> "本月"; else -> null
}

@Composable
fun ChatViewCard(view: ChatView, modifier: Modifier = Modifier) {
    XpCard(modifier.padding(horizontal = XpSpace.l)) {
        val title = view.title ?: when (view.kind) {
            "spend_summary" -> "支出合计"
            "category_split" -> "分类占比"
            "trend" -> "按天走势"
            "entries" -> "最近明细"
            "peak" -> "最大的一笔"
            "weekly_report" -> "本周小票"
            "subscriptions" -> "规律扣款"
            else -> "待确认草稿"
        }
        Text(title, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        when (view.kind) {
            "spend_summary" -> {
                Row(Modifier.padding(top = XpSpace.s), verticalAlignment = Alignment.Bottom) {
                    MoneyText(view.totalCents ?: 0L, style = MaterialTheme.typography.titleLarge)
                    val tail = listOfNotNull(view.count?.let { "$it 笔" }, periodLabel(view.period)).joinToString(" · ")
                    if (tail.isNotEmpty()) Text(tail, Modifier.padding(start = XpSpace.s, bottom = 3.dp), style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                }
            }
            "peak" -> Row(Modifier.padding(top = XpSpace.s), verticalAlignment = Alignment.CenterVertically) {
                Text(view.label ?: "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MoneyText(view.totalCents ?: 0L, style = MaterialTheme.typography.titleMedium)
            }
            "weekly_report" -> Row(Modifier.padding(top = XpSpace.s), verticalAlignment = Alignment.Bottom) {
                MoneyText(view.totalCents ?: 0L, style = MaterialTheme.typography.titleLarge)
                val tail = listOfNotNull(view.count?.let { "$it 笔" }, view.weekLabel, view.topCategory).joinToString(" · ")
                if (tail.isNotEmpty()) Text(tail, Modifier.padding(start = XpSpace.s, bottom = 3.dp), style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
            }
            "trend" -> TrendRow(view)
            else -> BarRows(view.rows, showBar = view.kind != "entries", showCadence = view.kind == "subscriptions")
        }
    }
}

@Composable
private fun TrendRow(view: ChatView) {
    val max = view.rows.maxOf { kotlin.math.abs(it.cents ?: 0L) }.coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth().height(72.dp).padding(top = XpSpace.s), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        view.rows.forEach { row ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().height((56 * (kotlin.math.abs(row.cents ?: 0L).toFloat() / max)).coerceAtLeast(3f).dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(XpColors.Accent),
                    )
                }
                Text(row.label ?: "", style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BarRows(rows: List<ChatViewRow>, showBar: Boolean, showCadence: Boolean) {
    val max = rows.maxOf { kotlin.math.abs(it.cents ?: 0L) }.coerceAtLeast(1L)
    Column(Modifier.padding(top = XpSpace.s), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        rows.forEach { row ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.label ?: row.merchant ?: "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (showCadence && row.cadence != null) {
                        Text(row.cadence ?: "", Modifier.padding(end = XpSpace.s), style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
                    }
                    Text((row.cents ?: 0L).yuan(), style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
                }
                if (showBar) {
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(XpColors.Fill)) {
                        Box(Modifier.fillMaxWidth((kotlin.math.abs(row.cents ?: 0L).toFloat() / max).coerceIn(0.04f, 1f)).height(3.dp).background(XpColors.Accent))
                    }
                }
            }
        }
    }
}
