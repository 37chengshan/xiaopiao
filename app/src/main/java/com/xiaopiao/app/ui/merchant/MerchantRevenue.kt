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
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.merchant.MonthGroup
import com.xiaopiao.app.merchant.RevenueBook
import com.xiaopiao.app.merchant.RevenueEntry
import com.xiaopiao.app.ui.components.EmptyState
import com.xiaopiao.app.ui.components.EmptyState
import com.xiaopiao.app.ui.components.MiniBars
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
 * 营收: the owner's own daily-total book. Manual rows and a tolerant CSV import land in the same
 * local list; nothing here touches the personal ledger or the network.
 */
@Composable
fun MerchantRevenueScreen(
    entries: List<RevenueEntry>,
    today: Long,
    onImportCsv: () -> Unit,
    onRecord: () -> Unit,
    onEditDay: (Long) -> Unit,
    onOpenReceipt: (Long) -> Unit,
) {
    val book = remember(entries) { RevenueBook.normalize(entries) }
    val months = remember(book) { RevenueBook.groupByMonth(book) }
    val currentMonth = remember(months, today) {
        months.firstOrNull { it.key == RevenueBook.monthKey(today) } ?: months.firstOrNull()
    }
    val scroll = rememberScrollState()

    NavScaffold(
        title = bi("营收", "Revenue"),
        scroll = NavScrollNone,
        trailing = { MerchantTopActions(onOpenReceipt = { onOpenReceipt(today) }) },
    ) { top ->
        if (currentMonth == null) {
            Column(Modifier.fillMaxSize().padding(top = top), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    icon = Icons.Outlined.PointOfSale,
                    title = bi("还没有营收记录", "No revenue yet"),
                    body = bi("记下今天的营业额，或导入一份「日期,营收,订单」的 CSV。", "Log today's takings, or import a CSV of date, revenue, orders."),
                    actionLabel = bi("记今天一笔", "Record today"),
                    onAction = onRecord,
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LinkText(bi("导入 CSV", "Import CSV"), onClick = onImportCsv)
                }
            }
            return@NavScaffold
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top + XpSpace.m, bottom = MerchantBottomInset)
                .xpRevealLayer(index = 0),
            verticalArrangement = Arrangement.spacedBy(XpSpace.s),
        ) {
            Column(Modifier.padding(horizontal = XpSpace.page)) {
                MerchantHeroCard(
                    label = bi("${currentMonth.label}合计", "${currentMonth.label} total"),
                    corner = { HeroPill(bi("导入 CSV", "Import CSV"), Icons.Outlined.UploadFile, onImportCsv) },
                    number = { RevenueMoney(currentMonth.totalCents, style = MerchantHeroNumber, animateChange = true) },
                    columns = {
                        StatCell(bi("已记", "Days"), bi("${currentMonth.days} 天", "${currentMonth.days}"), Modifier.weight(1f))
                        StatCell(
                            bi("平均每天", "Daily avg."),
                            RevenueBook.money(currentMonth.totalCents / currentMonth.days.coerceAtLeast(1)),
                            Modifier.weight(1.4f),
                        )
                        StatCell(
                            bi("订单", "Orders"),
                            if (currentMonth.orders > 0) bi("${currentMonth.orders} 单", "${currentMonth.orders}") else "—",
                            Modifier.weight(1.1f),
                        )
                    },
                    strip = {
                        MiniBars(
                            values = currentMonth.entries.asReversed().map { it.revenueCents.toFloat() },
                            height = 52.dp,
                            color = XpColors.Ink,
                        )
                        Row(Modifier.fillMaxWidth().padding(top = XpSpace.xs)) {
                            Text(
                                RevenueBook.shortDate(currentMonth.entries.last().dayStart),
                                style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary, maxLines = 1,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                RevenueBook.shortDate(currentMonth.entries.first().dayStart),
                                style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary, maxLines = 1,
                            )
                        }
                    },
                )
            }

            months.forEach { group ->
                MonthHeader(group)
                Column(Modifier.padding(horizontal = XpSpace.page)) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.m)).background(XpColors.Card),
                    ) {
                        group.entries.forEachIndexed { index, entry ->
                            if (index > 0) {
                                Box(
                                    Modifier.fillMaxWidth()
                                        .padding(start = 56.dp + XpSpace.l)
                                        .height(0.5.dp).background(XpColors.Hairline),
                                )
                            }
                            RevenueRow(entry) { onEditDay(entry.dayStart) }
                        }
                    }
                }
            }

            Text(
                bi("本机保存 · 不读你的个人账单", "Stored on this phone · never reads your personal ledger"),
                style = MaterialTheme.typography.labelSmall,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(horizontal = XpSpace.page).padding(top = XpSpace.l),
            )
        }
    }
}

@Composable
private fun MonthHeader(group: MonthGroup) {
    Row(
        Modifier.fillMaxWidth().padding(start = XpSpace.page, end = XpSpace.page, top = XpSpace.xl, bottom = XpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(group.label, style = MaterialTheme.typography.titleSmall, color = XpColors.Ink, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(
            bi("合计 ", "Total ") + RevenueBook.money(group.totalCents),
            style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun RevenueRow(entry: RevenueEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressableClick(onClick = onClick)
            .padding(horizontal = XpSpace.l, vertical = XpSpace.s + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateTile(entry.dayStart)
        RailGap()
        Column(Modifier.weight(1f)) {
            Text(RevenueBook.dayLabel(entry.dayStart), style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(XpSpace.xs),
                modifier = Modifier.padding(top = 1.dp),
            ) {
                Text(
                    if (entry.orders > 0) "${entry.orders} 单 · 客单 ${RevenueBook.money(entry.averageTicketCents)}" else "订单数未填",
                    style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                SourcePill(entry.source)
            }
            if (entry.note.isNotBlank()) {
                Text(
                    entry.note, style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.size(XpSpace.s))
        RevenueMoney(entry.revenueCents, style = MaterialTheme.typography.bodyLarge)
    }
}
