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
import com.xiaopiao.app.ui.components.MiniBars
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.components.pressableClick
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
    val demo = book.any { it.seeded }
    val scroll = rememberScrollState()

    NavScaffold(
        title = "营收",
        scroll = NavScrollNone,
        demo = demo,
        trailing = {
            NavIconButton(Icons.Outlined.UploadFile, "导入 CSV", onClick = onImportCsv)
            NavIconButton(Icons.AutoMirrored.Outlined.ReceiptLong, "经营日报小票", onClick = { onOpenReceipt(today) })
        },
    ) { top ->
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = XpSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(XpSpace.s),
        ) {
            if (currentMonth == null) {
                EmptyState(
                    icon = Icons.Outlined.PointOfSale,
                    title = "还没有营收记录",
                    body = "记下今天的营业额，或者导入一份「日期,营收,订单」的 CSV。",
                    action = "导入 CSV",
                    onAction = onImportCsv,
                )
                Column(Modifier.padding(horizontal = XpSpace.l)) {
                    XpButton(
                        "先记今天一笔", onRecord,
                        modifier = Modifier.fillMaxWidth(),
                        style = XpButtonStyle.Secondary, size = XpButtonSize.Large,
                    )
                }
            } else {
                Column(Modifier.padding(horizontal = XpSpace.l)) {
                    XpCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${currentMonth.label}合计",
                                style = MaterialTheme.typography.titleMedium, color = XpColors.Ink,
                            )
                        }
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = XpSpace.s)) {
                            RevenueMoney(currentMonth.totalCents, style = MaterialTheme.typography.headlineLarge)
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = XpSpace.m),
                            horizontalArrangement = Arrangement.spacedBy(XpSpace.xl),
                        ) {
                            MetricCell("已记") {
                                Text("${currentMonth.days} 天", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                            }
                            MetricCell("平均每天") {
                                RevenueMoney(currentMonth.totalCents / currentMonth.days.coerceAtLeast(1), style = MaterialTheme.typography.titleMedium)
                            }
                            MetricCell("订单") {
                                Text(
                                    if (currentMonth.orders > 0) "${currentMonth.orders} 单" else "—",
                                    style = MaterialTheme.typography.titleMedium, color = XpColors.Ink,
                                )
                            }
                        }
                        MiniBars(
                            values = currentMonth.entries.asReversed().map { it.revenueCents.toFloat() },
                            modifier = Modifier.padding(top = XpSpace.l),
                            height = 44.dp,
                        )
                        Row(Modifier.fillMaxWidth().padding(top = XpSpace.xs)) {
                            Text(
                                RevenueBook.shortDate(currentMonth.entries.last().dayStart),
                                style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                RevenueBook.shortDate(currentMonth.entries.first().dayStart),
                                style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary,
                            )
                        }
                    }
                }

                Row(
                    Modifier.padding(horizontal = XpSpace.l).padding(top = XpSpace.s),
                    horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
                ) {
                    XpButton("导入 CSV", onImportCsv, style = XpButtonStyle.Secondary, size = XpButtonSize.Small)
                    XpButton("记一笔", onRecord, style = XpButtonStyle.Secondary, size = XpButtonSize.Small)
                }

                months.forEach { group ->
                    MonthHeader(group)
                    Column(Modifier.padding(horizontal = XpSpace.l)) {
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
                    "本机保存 · 不读你的个人账单 · 带「演示」的是示例数据",
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkSecondary,
                    modifier = Modifier.padding(horizontal = XpSpace.l).padding(top = XpSpace.l),
                )
            }
            NavBottomSpace()
        }
    }
}

@Composable
private fun MonthHeader(group: MonthGroup) {
    Row(
        Modifier.fillMaxWidth().padding(start = XpSpace.l, end = XpSpace.l, top = XpSpace.xl, bottom = XpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(group.label, style = MaterialTheme.typography.titleSmall, color = XpColors.Ink)
        Spacer(Modifier.weight(1f))
        Text(
            "合计 ${RevenueBook.money(group.totalCents)}",
            style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
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
            Text(RevenueBook.dayLabel(entry.dayStart), style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(XpSpace.xs),
                modifier = Modifier.padding(top = 1.dp),
            ) {
                Text(
                    if (entry.orders > 0) "${entry.orders} 单 · 客单 ${RevenueBook.money(entry.averageTicketCents)}" else "订单数未填",
                    style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
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
