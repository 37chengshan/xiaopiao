package com.xiaopiao.app.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.ui.components.SectionHeader
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/** Compact "今日明细": one chip per spend category, biggest first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayBreakdown(title: String, items: List<Txn>, modifier: Modifier = Modifier) {
    val totals = DaySummaries.byCategory(items)
    SectionHeader(title)
    XpCard(modifier.padding(horizontal = XpSpace.l).animateContentSize(spring(0.8f, 400f))) {
        if (totals.isEmpty()) {
            Text("这天没有花销，钱包睡得很香 💤", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(XpSpace.s), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                totals.forEach { CategoryChip(it) }
            }
        }
    }
}

@Composable
private fun CategoryChip(total: CategoryTotal) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(XpColors.Canvas).padding(horizontal = XpSpace.m, vertical = XpSpace.s - 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XpSpace.xs + 2.dp),
    ) {
        Text(total.category.emoji, style = MaterialTheme.typography.bodyMedium)
        Text(total.category.label, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
        Text(ReceiptLayout.money(total.cents), style = MaterialTheme.typography.labelLarge, color = XpColors.Ink)
    }
}
