package com.xiaopiao.app.ui.merchant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.merchant.AgeBand
import com.xiaopiao.app.merchant.Aggregator
import com.xiaopiao.app.merchant.CohortStats
import com.xiaopiao.app.merchant.CrowdMember
import com.xiaopiao.app.merchant.K_ANONYMITY
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

private val donutColors = listOf(XpColors.Accent, XpColors.Warning, XpColors.Positive, XpColors.InkSecondary, XpColors.InkTertiary)

@Composable
fun InsightsStep(crowd: List<CrowdMember>, onNext: () -> Unit, onPrivacy: () -> Unit) {
    var age by remember { mutableStateOf<AgeBand?>(null) }
    val stats = remember(crowd, age) { Aggregator.compute(crowd, age) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(bottom = XpSpace.xl),
        verticalArrangement = Arrangement.spacedBy(XpSpace.m),
    ) {
        Reveal(0) { KpiRow(stats) }
        Reveal(1) { AgeChips(age) { age = it } }
        Reveal(2) { SectionCard("喝什么", "过去 30 天买过这类饮品的人") { KindBars(stats) } }
        Reveal(3) { SectionCard("什么时候来", "点一格看人数 · 灰色 = $SUPPRESSED_TEXT（少于 $K_ANONYMITY 人）") { HeatSection(stats) } }
        Reveal(4) { SectionCard("愿意花多少", "按每个人的平均客单价分组") { PriceBars(stats) } }
        Reveal(5) { SectionCard("爱加什么料", "每人只算一个最爱") { ToppingDonut(stats) } }
        Reveal(6) {
            Column(Modifier.padding(horizontal = XpSpace.l), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                Text(
                    "🔒 这里只有聚合数字，看不到任何一个人。点「数据怎么来的」了解细节。",
                    style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                    modifier = Modifier.clickable(onClick = onPrivacy),
                )
                MerchantButton("下一步：发起一个小实验", onNext)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, hint: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = XpSpace.l)) {
        XpCard {
            Text(title, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
            Text(hint, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
            Box(Modifier.padding(top = XpSpace.m)) { content() }
        }
    }
}

@Composable
private fun KpiRow(stats: CohortStats) {
    val enough = stats.avgPriceCents != null
    Row(Modifier.padding(horizontal = XpSpace.l), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        Kpi("授权邻居", if (enough) stats.totalPeople.toFloat() else null, "人", Modifier.weight(1f))
        Kpi("平均客单", stats.avgPriceCents?.let { it / 100f }, "元", Modifier.weight(1f))
        Kpi("常客占比", stats.repeatRate?.let { it * 100 }, "%", Modifier.weight(1f))
    }
}

@Composable
private fun Kpi(label: String, value: Float?, unit: String, modifier: Modifier) {
    val v by animateFloatAsState(value ?: 0f, SpringSoft, label = label)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(XpColors.Card).padding(XpSpace.m),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        if (value == null) {
            Text(SUPPRESSED_TEXT, style = MaterialTheme.typography.titleMedium, color = XpColors.InkTertiary)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (unit == "元") "%.1f".format(v) else "${v.toInt()}",
                    style = MaterialTheme.typography.titleLarge, color = XpColors.Accent,
                )
                Text(unit, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            }
        }
    }
}

@Composable
private fun AgeChips(selected: AgeBand?, onSelect: (AgeBand?) -> Unit) {
    Column(Modifier.padding(horizontal = XpSpace.l)) {
        Text("看哪群人", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        Row(Modifier.padding(top = XpSpace.xs), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            Chip("全部", selected == null) { onSelect(null) }
            AgeBand.entries.forEach { Chip(it.label, selected == it) { onSelect(it) } }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) XpColors.Accent else XpColors.Card, label = "chip")
    val fg by animateColorAsState(if (on) XpColors.Card else XpColors.Ink, label = "chipfg")
    val haptic = LocalHapticFeedback.current
    Text(
        label, style = MaterialTheme.typography.labelMedium, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg)
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun KindBars(stats: CohortStats) {
    val max = stats.kindShare.maxOfOrNull { it.share ?: 0f } ?: 0f
    Column(verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        stats.kindShare.forEachIndexed { i, c -> CellBar(c, max, XpColors.Accent, i) }
    }
}

@Composable
private fun PriceBars(stats: CohortStats) {
    val max = stats.priceBands.maxOfOrNull { it.share ?: 0f } ?: 0f
    Column(verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        stats.priceBands.forEachIndexed { i, c -> CellBar(c, max, XpColors.Warning, i) }
        val avg = stats.avgPriceCents
        if (avg != null) Text("平均每杯约 ¥${avg.yuan()}", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
    }
}

@Composable
private fun HeatSection(stats: CohortStats) {
    var sel by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        Heatmap(stats.heatmap, stats.heatmapMax, sel) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); sel = it
        }
        AnimatedContent(sel, label = "heatdetail") { s ->
            val cell = s?.let { stats.heatmap[it.first][it.second] }
            Text(
                when {
                    cell == null -> "👆 点一格看看"
                    cell.suppressed -> "${cell.label} · $SUPPRESSED_TEXT，为保护隐私不展示"
                    else -> "${cell.label} · 约 ${cell.people} 人到店"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (cell?.suppressed == true) XpColors.InkSecondary else XpColors.Ink,
            )
        }
    }
}

@Composable
private fun ToppingDonut(stats: CohortStats) {
    val values = stats.toppings.mapIndexed { i, c -> donutColors[i % donutColors.size] to c.people }
    Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.l), verticalAlignment = Alignment.CenterVertically) {
        Donut(values) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🍠", style = MaterialTheme.typography.titleLarge)
                Text("最爱小料", style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            stats.toppings.forEachIndexed { i, c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(donutColors[i % donutColors.size].let { col -> if (c.suppressed) XpColors.InkTertiary.copy(alpha = .4f) else col }))
                    Text(
                        c.label + "  " + if (c.suppressed) SUPPRESSED_TEXT else "${(c.share!! * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (c.suppressed) XpColors.InkTertiary else XpColors.Ink,
                    )
                }
            }
        }
    }
}
