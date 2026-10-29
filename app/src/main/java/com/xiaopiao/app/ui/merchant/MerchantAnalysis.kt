package com.xiaopiao.app.ui.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.merchant.InsightKind
import com.xiaopiao.app.merchant.InsightTone
import com.xiaopiao.app.merchant.MerchantAi
import com.xiaopiao.app.merchant.MerchantInsight
import com.xiaopiao.app.merchant.RevenueBook
import com.xiaopiao.app.merchant.RevenueEntry
import com.xiaopiao.app.merchant.WeekdayAverage
import com.xiaopiao.app.ui.components.EmptyState
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
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
    onOpenReceipt: (Long) -> Unit = {},
) {
    val book = remember(entries) { RevenueBook.normalize(entries) }
    val insights = remember(book, today) { MerchantAi.draft(book, today) }
    val weekdays = remember(book, today) { RevenueBook.weekdayAverages(book, today) }
    val scroll = rememberScrollState()
    val daysCovered = remember(book) { book.size }
    val weeksCovered = remember(book) {
        if (book.isEmpty()) 0 else {
            val spanDays = ((book.last().dayStart - book.first().dayStart) / 86_400_000L).toInt() + 1
            (spanDays + 6) / 7
        }
    }
    val windowTotal = remember(book, today) { RevenueBook.windowTotal(book, RevenueBook.plusDays(today, -13), today) }

    NavScaffold(
        title = bi("分析", "Insights"),
        scroll = NavScrollNone,
        trailing = { MerchantTopActions(onOpenReceipt = { onOpenReceipt(today) }) },
    ) { top ->
        if (book.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(top = top), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    title = bi("还没有营收记录", "No revenue yet"),
                    body = bi("先记几天营业额，或导入 CSV，分析就会长出来。", "Log a few days of takings or import a CSV and insights will appear."),
                    actionLabel = bi("去记一笔", "Record now"),
                    onAction = onRecord,
                )
            }
            return@NavScaffold
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top + XpSpace.m, bottom = MerchantBottomInset)
                .xpRevealLayer(index = 0),
            verticalArrangement = Arrangement.spacedBy(XpSpace.cardGap),
        ) {
            Column(Modifier.padding(horizontal = XpSpace.page)) {
                MerchantHeroCard(
                    label = bi("近 14 天营收", "Last 14 days"),
                    caption = bi("只读本机营收记录，不联网、不上传", "Reads only this phone's records · offline"),
                    number = { RevenueMoney(windowTotal, style = MerchantHeroNumber, animateChange = true) },
                    columns = {
                        StatCell(bi("已记", "Days logged"), bi("$daysCovered 天", "$daysCovered"), Modifier.weight(1f))
                        StatCell(
                            bi("覆盖", "Span"),
                            if (weeksCovered > 0) bi("$weeksCovered 周", "$weeksCovered wk") else "—",
                            Modifier.weight(1f),
                        )
                        StatCell(
                            bi("日均", "Daily avg."),
                            RevenueBook.money(windowTotal / 14),
                            Modifier.weight(1.4f),
                        )
                    },
                    strip = {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                bi("星期规律", "Weekday pattern"), style = MaterialTheme.typography.labelMedium,
                                color = XpColors.Ink, maxLines = 1, modifier = Modifier.weight(1f, fill = false),
                            )
                            Text(
                                bi("近 4 周平均", "4-week avg."), style = MaterialTheme.typography.labelSmall,
                                color = XpColors.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = XpSpace.s), textAlign = TextAlign.End,
                            )
                        }
                        WeekdayBars(weekdays, Modifier.padding(top = XpSpace.s))
                    },
                )
            }

            if (insights.isEmpty()) {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    title = bi("记录还太少", "Not enough days yet"),
                    body = bi("至少两天有数字，才能看出变化。", "Two days of numbers are needed to see a change."),
                    actionLabel = bi("看看营收表", "Open revenue"),
                    onAction = onOpenRevenue,
                )
            } else {
                Box(Modifier.xpRevealLayer(index = 1)) { SectionHeading(bi("本周读数", "What the numbers say")) }
                Column(Modifier.padding(horizontal = XpSpace.page), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    insights.forEach { insight -> InsightCard(insight) }
                }
            }

            Column(Modifier.padding(horizontal = XpSpace.page)) {
                XpCard(Modifier.pressableClick(onClick = onOpenCrowd)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Outlined.Groups, size = 36.dp, tint = XpColors.Accent, background = XpColors.AccentSoft)
                        Column(Modifier.weight(1f).padding(horizontal = XpSpace.m)) {
                            Text(
                                bi("顾客人群洞察", "Customer insights"), style = MaterialTheme.typography.bodyMedium,
                                color = XpColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                bi("看看邻居们喝什么、几点来", "What neighbours drink, and when"),
                                style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = XpColors.InkTertiary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Row(
                Modifier.padding(horizontal = XpSpace.page),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = XpColors.InkTertiary, modifier = Modifier.size(14.dp))
                Text(
                    bi("数据来自本机营收记录 · 分析全部在本机完成", "From this phone's records · analysed on-device"),
                    style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
                    modifier = Modifier.padding(start = XpSpace.xs),
                )
            }
        }
    }
}

/**
 * Seven bars, tallest weekday in the accent with its value above it. MiniBars' "last bar" highlight would read
 * as "today" here, so this one marks the peak instead. Empty weekdays stay a thin stub.
 */
@Composable
private fun WeekdayBars(stats: List<WeekdayAverage>, modifier: Modifier = Modifier) {
    val max = stats.maxOfOrNull { it.averageCents }?.coerceAtLeast(1L) ?: 1L
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            stats.forEach { stat ->
                val peak = stat.averageCents == max && stat.averageCents > 0
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (peak) {
                        FitText(
                            RevenueBook.money(stat.averageCents),
                            Modifier.padding(bottom = 3.dp),
                            MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            XpColors.Accent, minSize = 7.sp, textAlign = TextAlign.Center,
                        )
                    }
                    val fraction = if (stat.averageCents > 0) (stat.averageCents.toFloat() / max).coerceIn(0.06f, 1f) else 0.03f
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(fraction * (if (peak) 0.82f else 0.82f))
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (peak) XpColors.Accent else XpColors.Ink.copy(alpha = if (stat.days > 0) 0.16f else 0.07f)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = XpSpace.xs), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            stats.forEach { stat ->
                Text(
                    stat.label.removePrefix("周"), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stat.days > 0) XpColors.InkSecondary else XpColors.InkTertiary,
                    textAlign = TextAlign.Center, maxLines = 1,
                )
            }
        }
    }
}

private fun iconFor(kind: InsightKind): ImageVector = when (kind) {
    InsightKind.Decline -> Icons.AutoMirrored.Outlined.TrendingDown
    InsightKind.Week -> Icons.AutoMirrored.Outlined.TrendingUp
    InsightKind.Weekday -> Icons.Outlined.CalendarMonth
    InsightKind.Ticket -> Icons.Outlined.Receipt
    InsightKind.Best -> Icons.Outlined.EmojiEvents
}

@Composable
private fun InsightCard(insight: MerchantInsight) {
    val tint = when (insight.tone) {
        InsightTone.Up -> XpColors.Positive
        InsightTone.Down -> XpColors.Negative
        InsightTone.Warn -> XpColors.Warning
        InsightTone.Flat -> XpColors.InkSecondary
    }
    XpCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(XpRadius.m)).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(iconFor(insight.kind), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = XpSpace.m)) {
                Text(insight.title, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink, fontWeight = FontWeight.Medium)
                Text(
                    insight.body,
                    style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
