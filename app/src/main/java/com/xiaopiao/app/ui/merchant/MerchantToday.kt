package com.xiaopiao.app.ui.merchant

import androidx.compose.material.icons.outlined.Storefront
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.xiaopiao.app.ui.components.EmptyState
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.motion.xpRevealLayer
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
    val best = series.maxByOrNull { it.revenueCents }
    val scroll = rememberScrollState()

    NavScaffold(
        title = bi("今日", "Today"),
        scroll = NavScrollNone,
        trailing = { MerchantTopActions(onOpenReceipt = { onOpenReceipt(today) }) },
    ) { top ->
        if (book.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(top = top), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Icons.Outlined.Storefront,
                    title = bi("今天还没有营收记录", "No revenue recorded yet"),
                    body = bi("记下今天的营业额，明天起就有昨日、上周同日可以对比。", "Log today's takings and tomorrow you can compare with yesterday and last week."),
                    actionLabel = bi("记今日营收", "Record today"),
                    onAction = onRecordToday,
                )
            }
            return@NavScaffold
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top + XpSpace.m, bottom = MerchantBottomInset),
            verticalArrangement = Arrangement.spacedBy(XpSpace.cardGap),
        ) {
            Column(Modifier.padding(horizontal = XpSpace.page).xpRevealLayer(index = 0)) {
                MerchantHeroCard(
                    label = bi("今日营收", "Today's revenue"),
                    caption = RevenueBook.dayLabel(today),
                    number = {
                        if (todayEntry != null) {
                            RevenueMoney(todayEntry.revenueCents, style = MerchantHeroNumber, animateChange = true)
                        } else {
                            Text(
                                bi("还没记今天的营收", "Not recorded yet"),
                                style = MaterialTheme.typography.titleLarge, color = XpColors.InkTertiary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    columns = {
                        StatCell(
                            bi("订单", "Orders"),
                            if ((todayEntry?.orders ?: 0) > 0) bi("${todayEntry!!.orders} 单", "${todayEntry.orders}") else "—",
                            Modifier.weight(1f),
                        )
                        StatCell(
                            bi("客单价", "Avg. ticket"),
                            if ((todayEntry?.averageTicketCents ?: 0L) > 0) RevenueBook.money(todayEntry!!.averageTicketCents) else "—",
                            Modifier.weight(1.2f),
                        )
                        Column(Modifier.weight(1.2f)) {
                            Text(
                                bi("较昨日", "vs. yesterday"), style = MaterialTheme.typography.bodySmall,
                                color = XpColors.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            ChangePill(vsYesterday, Modifier.padding(top = 6.dp))
                        }
                    },
                    strip = {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                bi("近 14 天", "Last 14 days"), style = MaterialTheme.typography.labelSmall,
                                color = XpColors.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                bi("最高 ", "Peak ") + RevenueBook.money(best?.revenueCents ?: 0L),
                                style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary, maxLines = 1,
                            )
                        }
                        MiniBars(
                            values = series.map { it.revenueCents.toFloat() },
                            modifier = Modifier.padding(top = XpSpace.s),
                            height = 56.dp,
                            color = XpColors.Ink,
                        )
                        Row(Modifier.fillMaxWidth().padding(top = XpSpace.xs)) {
                            Text(
                                RevenueBook.shortDate(series.first().dayStart),
                                style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary, maxLines = 1,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(bi("今天", "Today"), style = MaterialTheme.typography.labelSmall, color = XpColors.Accent, maxLines = 1)
                        }
                    },
                )
            }

            Column(Modifier.padding(horizontal = XpSpace.page).xpRevealLayer(index = 1)) {
                XpButton(
                    text = if (todayEntry == null || todayEntry.seeded) bi("记今日营收", "Record today") else bi("修改今日营收", "Edit today"),
                    onClick = onRecordToday,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(Modifier.padding(horizontal = XpSpace.page).xpRevealLayer(index = 2)) {
                XpCard {
                    Text(bi("对比", "Compare"), style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                    ComparisonLine(
                        bi("昨日（${RevenueBook.weekdayLabel(RevenueBook.plusDays(today, -1))}）", "Yesterday"),
                        yesterdayCents, vsYesterday,
                    )
                    Box(Modifier.fillMaxWidth().padding(vertical = XpSpace.s).height(0.5.dp).background(XpColors.Hairline))
                    ComparisonLine(bi("上周今天", "Same day last week"), lastWeekCents, vsLastWeek)
                    Box(Modifier.fillMaxWidth().padding(vertical = XpSpace.s).height(0.5.dp).background(XpColors.Hairline))
                    ComparisonLine(bi("本周 $weekDays 天", "This week · $weekDays d"), week.nowCents, week)
                }
            }

            Box(Modifier.xpRevealLayer(index = 3)) {
                SectionHeading(bi("最近记录", "Recent")) { LinkText(bi("全部 ›", "All ›"), onClick = onOpenRevenue) }
            }
            if (recent.isEmpty()) {
                Column(Modifier.padding(horizontal = XpSpace.page)) {
                    XpCard {
                        Text(
                            bi("还没有更早的记录。今天记下第一笔，明天就有得比了。", "Nothing earlier yet. Record today and tomorrow you'll have a comparison."),
                            style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary,
                        )
                    }
                }
            } else {
                Column(Modifier.padding(horizontal = XpSpace.page)) {
                    XpCard {
                        recent.forEachIndexed { index, entry ->
                            if (index > 0) Box(Modifier.fillMaxWidth().padding(vertical = XpSpace.s).height(0.5.dp).background(XpColors.Hairline))
                            RecentRow(entry) { onEditDay(entry.dayStart) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonLine(label: String, baselineCents: Long, change: Change) {
    Row(Modifier.fillMaxWidth().padding(top = XpSpace.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (baselineCents > 0) RevenueBook.money(baselineCents) else bi("没有记录", "No record"),
                style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary, maxLines = 1,
            )
        }
        ChangePill(change)
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
            Text(RevenueBook.dayLabel(entry.dayStart), style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.xs)) {
                Text(
                    if (entry.orders > 0) "${entry.orders} 单 · 客单 ${RevenueBook.money(entry.averageTicketCents)}" else "订单数未填",
                    style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                SourcePill(entry.source)
            }
        }
        Spacer(Modifier.size(XpSpace.s))
        RevenueMoney(entry.revenueCents, style = MaterialTheme.typography.bodyLarge)
    }
}
