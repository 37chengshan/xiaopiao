package com.xiaopiao.app.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.SectionHeader
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * 今日明细: one aligned row per spend category — icon, label, a share bar and a right-aligned amount.
 * Three columns, one baseline, so the eye can compare the bars instead of reading chips (docs/APP-DESIGN.md §8.5).
 */
@Composable
fun DayBreakdown(title: String, items: List<Txn>, modifier: Modifier = Modifier) {
    val totals = DaySummaries.byCategory(items)
    val total = totals.sumOf { it.cents }
    val biggest = totals.maxOfOrNull { it.cents } ?: 0L
    SectionHeader(title) {
        MoneyText(total, style = MaterialTheme.typography.titleSmall, color = XpColors.InkSecondary)
    }
    XpCard(modifier.padding(horizontal = XpSpace.l).animateContentSize(spring(0.8f, 400f))) {
        if (totals.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                Text("💤", style = MaterialTheme.typography.titleLarge)
                Text("这天没有花销，钱包睡得很香", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
            }
        } else {
            totals.forEachIndexed { i, t ->
                Row(
                    Modifier.fillMaxWidth().height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
                ) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(XpColors.Surface2),
                        contentAlignment = Alignment.Center,
                    ) { Text(t.category.emoji, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        t.category.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = XpColors.Ink,
                        modifier = Modifier.width(72.dp),
                        maxLines = 1,
                    )
                    // Share bar: the only graphic in the row, sized against the biggest category.
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(XpColors.Fill)) {
                        val frac = if (biggest <= 0) 0f else t.cents.toFloat() / biggest
                        Box(
                            Modifier.fillMaxWidth(frac.coerceIn(0.06f, 1f)).height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (i == 0) XpColors.Accent else XpColors.Ink.copy(alpha = 0.28f)),
                        )
                    }
                    MoneyText(
                        t.cents,
                        style = MaterialTheme.typography.titleSmall,
                        color = XpColors.Ink,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(80.dp),
                    )
                }
                if (i != totals.lastIndex) {
                    Box(Modifier.fillMaxWidth().padding(start = 36.dp).height(0.5.dp).background(XpColors.Hairline))
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderSpace() = Spacer(Modifier.height(XpSpace.xs))
