package com.xiaopiao.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.insights.CategoryShare
import com.xiaopiao.app.insights.WeeklyReport
import com.xiaopiao.app.ui.components.DemoChip
import com.xiaopiao.app.ui.components.MiniBars
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlin.math.roundToInt

/** Cards embedded in the chat flow: full-width [XpCard]s, never bubbles. */

/** 今日小票预览 — compact, real ledger data. 演示 chip only when those rows are demo rows. */
@Composable
fun TodayReceiptCard(stats: ChatStats, dayLabel: String) {
    XpCard(Modifier.padding(horizontal = XpSpace.l)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("今日小票", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
            if (stats.hasDemo) DemoChip(Modifier.padding(start = XpSpace.s))
            Spacer(Modifier.weight(1f))
            Text(dayLabel, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
        }
        Row(Modifier.padding(top = XpSpace.s), verticalAlignment = Alignment.Bottom) {
            MoneyText(stats.todayOutCents, style = MaterialTheme.typography.titleLarge)
            Text(
                "${stats.todayOutCount} 笔",
                Modifier.padding(start = XpSpace.s, bottom = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
            )
        }
        if (stats.todayOutItems.isEmpty()) {
            Text(
                "今天还没有支出。说一句，或点下面的「＋」手填。",
                Modifier.padding(top = XpSpace.s),
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
            )
        } else {
            stats.todayOutItems.take(3).forEach { txn ->
                Row(
                    Modifier.fillMaxWidth().padding(top = XpSpace.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${txn.category.emoji} ${txn.merchant}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = XpColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MoneyText(txn.amountCents, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
                }
            }
            if (stats.todayOutItems.size > 3) {
                Text(
                    "还有 ${stats.todayOutItems.size - 3} 笔",
                    Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.InkTertiary,
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = XpSpace.m).height(0.5.dp).background(XpColors.Hairline))
        Text(
            "去「记账」右上角的小票图标打印",
            Modifier.padding(top = XpSpace.s),
            style = MaterialTheme.typography.labelSmall,
            color = XpColors.InkTertiary,
        )
    }
}

/** 周报卡 — this Mon–Sun week: total vs the same span of last week, daily shape, top categories. */
@Composable
fun WeekReportCard(stats: ChatStats) {
    val week = stats.week
    XpCard(Modifier.padding(horizontal = XpSpace.l)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("本周周报", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
            if (stats.hasDemo) DemoChip(Modifier.padding(start = XpSpace.s))
            Spacer(Modifier.weight(1f))
            Pill(if (week.isComplete) "已完成" else "进行中")
        }
        Row(Modifier.padding(top = XpSpace.s), verticalAlignment = Alignment.Bottom) {
            MoneyText(week.totalOutCents, style = MaterialTheme.typography.titleLarge)
            Text(
                weekDelta(week),
                Modifier.padding(start = XpSpace.s, bottom = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
            )
        }
        WeekBars(week.dayTotals)
        val peak = stats.weekPeakDay
        Text(
            if (peak != null) "花得最多：${ChatCopy.WEEK[peak]} ${yuanText(stats.weekPeakCents)}" else "这周还没有支出",
            Modifier.padding(top = XpSpace.s),
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkSecondary,
        )
        week.topCategories.take(2).forEach { ShareRow(it) }
    }
}

private fun weekDelta(week: WeeklyReport): String {
    val pct = week.vsPrevPercent
        ?: return if (week.txnCount == 0) "还没有数据" else "上周没有可比数据"
    return when {
        pct > 0 -> "比上周同期多 $pct%"
        pct < 0 -> "比上周同期少 ${-pct}%"
        else -> "和上周同期持平"
    }
}

@Composable
private fun ShareRow(share: CategoryShare) {
    Row(
        Modifier.fillMaxWidth().padding(top = XpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${share.category.emoji} ${share.category.label}",
            Modifier.width(78.dp),
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier.weight(1f).padding(horizontal = XpSpace.s).height(4.dp)
                .clip(RoundedCornerShape(XpRadius.pill))
                .background(XpColors.Fill),
        ) {
            Box(
                Modifier.fillMaxWidth(share.share.coerceIn(0f, 1f)).fillMaxHeight()
                    .clip(RoundedCornerShape(XpRadius.pill))
                    .background(XpColors.Accent),
            )
        }
        Text(
            "${(share.share * 100).roundToInt()}%",
            Modifier.width(34.dp),
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkSecondary,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * Confirm card for a locally parsed sentence. The preview is not in the ledger yet, so it carries a 演示
 * mark — the row only becomes real data after 确认 (then 今日小票 above updates from the repository).
 */
@Composable
fun ConfirmCard(item: ChatItem.Confirm, onConfirm: () -> Unit, onEdit: () -> Unit) {
    XpCard(Modifier.padding(horizontal = XpSpace.l)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("我记成", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
            Pill("演示", Modifier.padding(start = XpSpace.s))
            Spacer(Modifier.weight(1f))
            MoneyText(item.draft.amountCents, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "${item.draft.category.emoji} ${item.draft.category.label} · ${item.draft.merchant}",
            Modifier.padding(top = XpSpace.xs),
            style = MaterialTheme.typography.bodyMedium,
            color = XpColors.Ink,
        )
        when (item.state) {
            ConfirmState.Pending, ConfirmState.Saving -> Row(
                Modifier.padding(top = XpSpace.m),
                horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
            ) {
                XpButton(
                    "确认",
                    onConfirm,
                    style = XpButtonStyle.Primary,
                    size = XpButtonSize.Small,
                    enabled = item.state == ConfirmState.Pending,
                )
                XpButton(
                    "改一下",
                    onEdit,
                    style = XpButtonStyle.Secondary,
                    size = XpButtonSize.Small,
                    enabled = item.state == ConfirmState.Pending,
                )
            }

            ConfirmState.Saved -> Row(
                Modifier.padding(top = XpSpace.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = XpColors.Positive,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "已记进账本",
                    Modifier.padding(start = XpSpace.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.InkSecondary,
                )
            }

            ConfirmState.Duplicate -> Text(
                "这笔已经记过了，没有重复入账。",
                Modifier.padding(top = XpSpace.m),
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
            )
        }
    }
}

/** Seven slim bars with weekday initials underneath; today's column carries the accent. */
@Composable
internal fun WeekBars(dayTotals: List<Long>) {
    val todayIdx = ((java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7)
    val max = (dayTotals.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(
        Modifier.fillMaxWidth().padding(top = XpSpace.m),
        horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
        verticalAlignment = Alignment.Bottom,
    ) {
        dayTotals.forEachIndexed { i, v ->
            val ratio = (v.toFloat() / max).coerceIn(0f, 1f)
            val active = i == todayIdx
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth(0.6f).height((6f + 30f * ratio).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (active) XpColors.Accent else XpColors.Fill),
                )
                Text(
                    labels[i],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) XpColors.Accent else XpColors.InkTertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
