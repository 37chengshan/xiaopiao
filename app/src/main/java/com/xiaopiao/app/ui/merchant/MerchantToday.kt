package com.xiaopiao.app.ui.merchant

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
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.merchant.Change
import com.xiaopiao.app.merchant.RevenueBook
import com.xiaopiao.app.merchant.RevenueEntry
import com.xiaopiao.app.merchant.RevenueSource
import com.xiaopiao.app.ui.components.MiniBars
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * 今日: the one screen that answers "how is today going". Big number, two comparisons, a 14-day
 * shape, and the single primary action of the tab — record today.
 */
@Composable
fun MerchantTodayScreen(
    entries: List<RevenueEntry>,
    today: Long,
    onRecordToday: () -> Unit,
    onEditDay: (Long) -> Unit,
    onOpenReceipt: (Long) -> Unit,
    onOpenRevenue: () -> Unit,
) {
    val book = remember(entries) { RevenueBook.normalize(entries) }
    val todayEntry = remember(book, today) { RevenueBook.entryFor(book, today) }
    val series = remember(book, today) { RevenueBook.series(book, today, 14) }
    val vsYesterday = remember(book, today) { RevenueBook.todayVsYesterday(book, today) }
    val vsLastWeek = remember(book, today) { RevenueBook.sameWeekdayChange(book, today) }
    val week = remember(book, today) { RevenueBook.weekToDateChange(book, today) }
    val weekDays = remember(book, today) { RevenueBook.window(book, RevenueBook.weekStart(today), today).size }
    val recent = remember(book, today) {
        book.filter { it.dayStart < today }.sortedByDescending { it.dayStart }.take(4)
    }
    val yesterdayCents = remember(book, today) { RevenueBook.entryFor(book, RevenueBook.plusDays(today, -1))?.revenueCents ?: 0L }
    val lastWeekCents = remember(book, today) { RevenueBook.entryFor(book, RevenueBook.plusDays(today, -7))?.revenueCents ?: 0L }
    val demo = series.any { it.source == RevenueSource.SEED }
    val best = series.maxByOrNull { it.revenueCents }
    val scroll = rememberScrollState()

    NavScaffold(
        title = "今日",
        scroll = NavScrollNone,
        demo = demo,
        trailing = { NavIconButton(Icons.AutoMirrored.Outlined.ReceiptLong, "经营日报小票", onClick = { onOpenReceipt(today) }) },
    ) { top ->
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = XpSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(XpSpace.m),
        ) {
            Column(Modifier.padding(horizontal = XpSpace.l)) {
                XpCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("今日营收", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                    }
                    Text(
                        RevenueBook.dayLabel(today),
                        style = MaterialTheme.typography.labelMedium,
                        color = XpColors.InkSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (todayEntry != null) {
                        RevenueMoney(
                            todayEntry.revenueCents,
                            modifier = Modifier.padding(top = XpSpace.s),
                            style = MaterialTheme.typography.headlineLarge,
                        )
                    } else {
                        Text(
                            "还没记今天的营收",
                            style = MaterialTheme.typography.headlineMedium,
                            color = XpColors.InkTertiary,
                            modifier = Modifier.padding(top = XpSpace.s),
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = XpSpace.l), horizontalArrangement = Arrangement.spacedBy(XpSpace.xl)) {
                        MetricCell("订单") {
                            Text(
                                if ((todayEntry?.orders ?: 0) > 0) "${todayEntry!!.orders} 单" else "—",
                                style = MaterialTheme.typography.titleMedium, color = XpColors.Ink,
                            )
                        }
                        MetricCell("客单价") {
                            RevenueMoney(todayEntry?.averageTicketCents ?: 0L, style = MaterialTheme.typography.titleMedium)
                        }
                        MetricCell("较昨日") { ChangeLabel(vsYesterday) }
                    }
                    Box(Modifier.fillMaxWidth().padding(top = XpSpace.l).height(1.dp).background(XpColors.Hairline))
                    Row(Modifier.fillMaxWidth().padding(top = XpSpace.m), verticalAlignment = Alignment.CenterVertically) {
                        Text("近 14 天", style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "最高 ${RevenueBook.money(best?.revenueCents ?: 0L)}",
                            style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
                        )
                    }
                    MiniBars(
                        values = series.map { it.revenueCents.toFloat() },
                        modifier = Modifier.padding(top = XpSpace.s),
                        height = 52.dp,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = XpSpace.xs)) {
                        Text(
                            RevenueBook.shortDate(series.first().dayStart),
                            style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text("今天", style = MaterialTheme.typography.labelSmall, color = XpColors.Accent)
                    }
                }
            }

            Column(Modifier.padding(horizontal = XpSpace.l)) {
                XpButton(
                    text = if (todayEntry == null || todayEntry.seeded) "记今日营收" else "修改今日营收",
                    onClick = onRecordToday,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(Modifier.padding(horizontal = XpSpace.l)) {
                XpCard {
                    Text("对比", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                    ComparisonLine("昨日（${RevenueBook.weekdayLabel(RevenueBook.plusDays(today, -1))}）", yesterdayCents, vsYesterday)
                    Box(Modifier.fillMaxWidth().padding(vertical = XpSpace.s).height(1.dp).background(XpColors.Hairline))
                    ComparisonLine("上周今天", lastWeekCents, vsLastWeek)
                    Box(Modifier.fillMaxWidth().padding(vertical = XpSpace.s).height(1.dp).background(XpColors.Hairline))
                    ComparisonLine("本周 $weekDays 天", week.nowCents, week)
                }
            }

            SectionHeading("最近记录") {
                LinkText("全部 ›", onClick = onOpenRevenue)
            }
            if (recent.isEmpty()) {
                Column(Modifier.padding(horizontal = XpSpace.l)) {
                    XpCard {
                        Text(
                            "还没有更早的记录。今天记下第一笔，明天就有得比了。",
                            style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary,
                        )
                    }
                }
            } else {
                Column(Modifier.padding(horizontal = XpSpace.l)) {
                    XpCard {
                        recent.forEachIndexed { index, entry ->
                            if (index > 0) Box(Modifier.fillMaxWidth().padding(vertical = XpSpace.s).height(1.dp).background(XpColors.Hairline))
                            RecentRow(entry) { onEditDay(entry.dayStart) }
                        }
                    }
                }
            }

            NavBottomSpace()
        }
    }
}

@Composable
private fun ComparisonLine(label: String, baselineCents: Long, change: Change) {
    Row(Modifier.fillMaxWidth().padding(top = XpSpace.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
            Text(
                if (baselineCents > 0) RevenueBook.money(baselineCents) else "没有记录",
                style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
            )
        }
        ChangeLabel(change)
    }
}

@Composable
private fun RecentRow(entry: RevenueEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.s)).pressableClick(onClick = onClick)
            .padding(vertical = XpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateTile(entry.dayStart)
        RailGap()
        Column(Modifier.weight(1f)) {
            Text(RevenueBook.dayLabel(entry.dayStart), style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.xs)) {
                Text(
                    if (entry.orders > 0) "${entry.orders} 单 · 客单 ${RevenueBook.money(entry.averageTicketCents)}" else "订单数未填",
                    style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
                )
                SourcePill(entry.source)
            }
        }
        Spacer(Modifier.size(XpSpace.s))
        RevenueMoney(entry.revenueCents, style = MaterialTheme.typography.bodyLarge)
    }
}
