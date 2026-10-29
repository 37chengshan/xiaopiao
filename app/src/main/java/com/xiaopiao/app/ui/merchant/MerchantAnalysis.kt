package com.xiaopiao.app.ui.merchant

import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.merchant.InsightTone
import com.xiaopiao.app.merchant.MerchantAi
import com.xiaopiao.app.merchant.MerchantInsight
import com.xiaopiao.app.merchant.RevenueBook
import com.xiaopiao.app.merchant.RevenueEntry
import com.xiaopiao.app.merchant.RevenueSource
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.motion.xpRevealLayer
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * 分析: sentences computed from the local revenue book. Every card carries a number, and the bottom
 * line says where the data came from. No network round trip, no model keys in the app.
 */
@Composable
fun MerchantAnalysisScreen(
    entries: List<RevenueEntry>,
    today: Long,
    onRecord: () -> Unit,
    onOpenCrowd: () -> Unit,
    onOpenRevenue: () -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val book = remember(entries) { RevenueBook.normalize(entries) }
    val insights = remember(book, today) { MerchantAi.draft(book, today) }
    val weekdays = remember(book, today) { RevenueBook.weekdayAverages(book, today) }
    val demo = book.any { it.source == RevenueSource.SEED }
    val scroll = rememberScrollState()
    val daysCovered = remember(book) { book.size }
    val weeksCovered = remember(book) {
        if (book.isEmpty()) 0 else {
            val spanDays = ((book.last().dayStart - book.first().dayStart) / 86_400_000L).toInt() + 1
            (spanDays + 6) / 7
        }
    }

    NavScaffold(
        title = "分析", scroll = NavScrollNone, demo = demo,
        trailing = { NavIconButton(androidx.compose.material.icons.Icons.Outlined.Settings, "设置", onClick = onOpenSettings) },
    ) { top ->
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = XpSpace.xxl)
                .xpRevealLayer(index = 0),
            verticalArrangement = Arrangement.spacedBy(XpSpace.m),
        ) {
            Column(Modifier.padding(horizontal = XpSpace.l)) {
                XpCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Outlined.AutoAwesome, size = 34.dp, tint = XpColors.Accent, background = XpColors.AccentSoft)
                        Column(Modifier.weight(1f).padding(start = XpSpace.m)) {
                            Text("本机经营分析", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                            Text(
                                "只读你自己的营收记录，不联网、不上传",
                                style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = XpSpace.l),
                        horizontalArrangement = Arrangement.spacedBy(XpSpace.xl),
                    ) {
                        MetricCell("已记", Modifier.weight(1f)) {
                            BigValue("$daysCovered 天")
                        }
                        MetricCell("覆盖", Modifier.weight(1f)) {
                            BigValue(if (weeksCovered > 0) "$weeksCovered 周" else "—")
                        }
                        MetricCell("近 14 天", Modifier.weight(1.4f)) {
                            Text(
                                RevenueBook.money(RevenueBook.windowTotal(book, RevenueBook.plusDays(today, -13), today)),
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                color = XpColors.Ink,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            if (book.isNotEmpty()) {
                Column(Modifier.padding(horizontal = XpSpace.l)) {
                    XpCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("星期规律", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                            Spacer(Modifier.weight(1f))
                            Text("近 4 周平均", style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
                        }
                        WeekdayBars(weekdays.map { it.averageCents })
                        Row(Modifier.fillMaxWidth().padding(top = XpSpace.xs)) {
                            weekdays.forEach { stat ->
                                Text(
                                    stat.label.removePrefix("周"),
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (stat.days > 0) XpColors.InkSecondary else XpColors.InkTertiary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            if (insights.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    title = if (book.isEmpty()) "还没有营收记录" else "记录还太少",
                    body = if (book.isEmpty()) {
                        "先记几天营业额，或者导入 CSV，分析就会长出来。"
                    } else {
                        "至少两天有数字，才能看出变化。"
                    },
                    action = "去记一笔",
                    onAction = onRecord,
                )
                Column(Modifier.padding(horizontal = XpSpace.l)) {
                    XpButton(
                        "看看营收表", onOpenRevenue,
                        modifier = Modifier.fillMaxWidth(),
                        style = XpButtonStyle.Secondary, size = XpButtonSize.Large,
                    )
                }
            } else {
                insights.forEach { insight -> InsightCard(insight) }
            }

            Column(Modifier.padding(horizontal = XpSpace.l)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.m)).background(XpColors.Card)
                        .pressableClick(onClick = onOpenCrowd)
                        .padding(XpSpace.l),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.AutoMirrored.Outlined.ShowChart, size = 34.dp)
                        Column(Modifier.weight(1f).padding(start = XpSpace.m)) {
                            Text("顾客人群洞察", style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
                            Text(
                                "看看邻居们喝什么、几点来",
                                style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
                            )
                        }
                        Text("›", style = MaterialTheme.typography.titleMedium, color = XpColors.InkTertiary)
                    }
                }
            }

            Row(
                Modifier.padding(horizontal = XpSpace.l).padding(top = XpSpace.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = XpColors.InkTertiary, modifier = Modifier.size(14.dp))
                Text(
                    "数据来自本机营收记录 · 分析全部在本机完成",
                    style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
                    modifier = Modifier.padding(start = XpSpace.xs),
                )
            }
            NavBottomSpace()
        }
    }
}

/** Seven bars where the tallest week is the accent — MiniBars' "last bar" highlight would read as "today". */
@Composable
private fun WeekdayBars(averages: List<Long>) {
    val max = averages.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().padding(top = XpSpace.m).height(56.dp)) {
        if (averages.isEmpty()) return@Canvas
        val gap = 8.dp.toPx()
        val w = (size.width - gap * (averages.size - 1)) / averages.size
        val radius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
        averages.forEachIndexed { i, value ->
            val h = (value.toFloat() / max * size.height).coerceAtLeast(if (value > 0) 4.dp.toPx() else 2.dp.toPx())
            drawRoundRect(
                color = if (value == max && value > 0) XpColors.Accent else XpColors.InkTertiary.copy(alpha = 0.32f),
                topLeft = androidx.compose.ui.geometry.Offset(i * (w + gap), size.height - h),
                size = androidx.compose.ui.geometry.Size(w, h),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun InsightCard(insight: MerchantInsight) {
    val tint = when (insight.tone) {
        InsightTone.Up -> XpColors.Positive
        InsightTone.Down -> XpColors.Negative
        InsightTone.Warn -> XpColors.Warning
        InsightTone.Flat -> XpColors.InkSecondary
    }
    Column(Modifier.padding(horizontal = XpSpace.l)) {
        XpCard {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(XpRadius.m)).background(XpColors.Fill),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(insight.emoji, fontSize = 18.sp)
                }
                Column(Modifier.weight(1f).padding(start = XpSpace.m)) {
                    Text(insight.title, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink, fontWeight = FontWeight.Medium)
                    Text(
                        insight.body,
                        style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    Modifier.padding(start = XpSpace.s, top = 6.dp).size(6.dp)
                        .clip(RoundedCornerShape(3.dp)).background(tint),
                )
            }
        }
    }
}
