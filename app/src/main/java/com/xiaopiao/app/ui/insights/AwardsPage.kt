package com.xiaopiao.app.ui.insights

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.Badge
import com.xiaopiao.app.insights.WeeklyReport
import com.xiaopiao.app.insights.WeeklyReportBuilder
import com.xiaopiao.app.insights.addDays
import com.xiaopiao.app.insights.startOfWeek
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.SectionHeader
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DAY_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")

/** 周报颁奖典礼: confetti, flipping medals, animated bars, one-line comment. */
@Composable
fun AwardsPage(input: InsightsInput, onBack: () -> Unit) {
    var lastWeek by rememberSaveable { mutableStateOf(false) }
    val anchor = if (lastWeek) addDays(startOfWeek(input.now), -7) else input.now
    val report = remember(input, lastWeek) { WeeklyReportBuilder.build(anchor, input.history, input.now) }
    var burst by remember { mutableIntStateOf(1) }
    val haptic = LocalHapticFeedback.current

    Box {
        InsightsPage("周报颁奖典礼", rangeLabel(report), onBack) {
            Segmented(listOf(true to "上周", false to "本周"), lastWeek) { lastWeek = it; burst++ }
            if (report.isEmpty) {
                InsightsEmpty("🏆", "这周还没有小票", "记几笔账，周日晚上就有颁奖典礼啦～")
            } else {
                Text(
                    if (report.isComplete) "本周获奖名单" else "进行中 · 暂定名单",
                    style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                )
                // key(...) restarts the staggered flip whenever the week changes.
                androidx.compose.runtime.key(lastWeek, report.badges.map { it.code }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                        report.badges.forEachIndexed { i, b -> MedalCard(b, delayMs = 500L + i * 650L, Modifier.weight(1f)) }
                        if (report.badges.size == 1) Box(Modifier.weight(1f))
                    }
                }
                if (report.badges.isEmpty()) {
                    XpCard { Text("这周没有特别的头衔，平平稳稳也是一种本事 🌿", color = XpColors.InkSecondary) }
                }
                CommentCard(report.comment)
                WeekStatsCard(report, input.demo)
                CategoriesCard(report)
                FactsCard(report)
            }
        }
        if (!report.isEmpty) ConfettiBurst(burst)
    }
    LaunchedEffect(burst, report.isEmpty) {
        if (!report.isEmpty) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

private fun rangeLabel(r: WeeklyReport): String {
    val f = SimpleDateFormat("M月d日", Locale.CHINA)
    return "${f.format(Date(r.weekStart))} – ${f.format(Date(addDays(r.weekEnd, -1)))}"
}

/** A medal that flips in (rotationY 180 -> 0, bouncy) after [delayMs]. */
@Composable
private fun MedalCard(badge: Badge, delayMs: Long, modifier: Modifier = Modifier) {
    val rot = remember { Animatable(180f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs)
        rot.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessVeryLow))
    }
    LaunchedEffect(rot.value < 90f) { if (rot.value < 90f) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val density = LocalDensity.current.density
    val front = rot.value < 90f
    Box(
        modifier.height(188.dp).graphicsLayer {
            rotationY = rot.value
            cameraDistance = 14f * density
        }.clip(RoundedCornerShape(24.dp))
            .background(if (front) XpColors.Card else XpColors.AccentSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (front) {
            Column(Modifier.padding(XpSpace.m), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(XpSpace.xs)) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(50)).background(XpColors.AccentSoft), contentAlignment = Alignment.Center) {
                    Text(badge.emoji, style = MaterialTheme.typography.headlineLarge)
                }
                Text(badge.title, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink, textAlign = TextAlign.Center)
                Text(badge.reason, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary, textAlign = TextAlign.Center, maxLines = 3)
            }
        } else {
            // Mirrored back face is hidden by showing a placeholder that is not mirrored by rotationY > 90.
            Text("🎁", style = MaterialTheme.typography.displayLarge, modifier = Modifier.graphicsLayer { rotationY = 180f })
        }
    }
}

@Composable
private fun CommentCard(comment: String) {
    XpCard {
        Text("小票评语", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        Text(comment, style = MaterialTheme.typography.bodyLarge, color = XpColors.Ink, modifier = Modifier.padding(top = XpSpace.xs))
    }
}

@Composable
private fun WeekStatsCard(r: WeeklyReport, demo: Boolean) {
    XpCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("本周花了", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary, modifier = Modifier.weight(1f))
        }
        BigMoney(r.totalOutCents)
        r.vsPrevPercent?.let { p ->
            val txt = when {
                p == 0 -> "和上周差不多"
                p < 0 -> "比上周少 ${-p}%"
                else -> "比上周多 $p%"
            }
            Text(txt, style = MaterialTheme.typography.labelLarge, color = if (p <= 0) XpColors.Positive else XpColors.Warning)
        }
        val max = (r.dayTotals.maxOrNull() ?: 0L).coerceAtLeast(1L)
        Row(
            Modifier.fillMaxWidth().padding(top = XpSpace.l).height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(XpSpace.s), verticalAlignment = Alignment.Bottom,
        ) {
            r.dayTotals.forEachIndexed { i, cents ->
                val frac by animateFloatAsState(cents.toFloat() / max, tween(700, delayMillis = 120 * i), label = "bar")
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.fillMaxWidth(0.7f).height((100 * frac).coerceAtLeast(3f).dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (cents == max && cents > 0) XpColors.Accent else XpColors.AccentSoft),
                        )
                    }
                    Text(DAY_NAMES[i], style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
                }
            }
        }
    }
}

@Composable
private fun CategoriesCard(r: WeeklyReport) {
    if (r.topCategories.isEmpty()) return
    XpCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("花得最多的", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary, modifier = Modifier.weight(1f))
        }
        val topTotal = r.topCategories.sumOf { it.cents }.coerceAtLeast(1L)
        r.topCategories.forEachIndexed { i, c ->
            // Share of the listed categories, animated with a spring so it grows instead of jumping.
            val frac by animateFloatAsState(
                (c.cents.toFloat() / topTotal).coerceIn(0.03f, 1f),
                androidx.compose.animation.core.spring(0.85f, 420f),
                label = "share",
            )
            Column(Modifier.padding(top = XpSpace.m)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${c.category.emoji} ${c.category.label}", style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
                    MoneyText(c.cents, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
                }
                Box(Modifier.fillMaxWidth().padding(top = XpSpace.xs).height(8.dp).clip(RoundedCornerShape(4.dp)).background(XpColors.Hairline)) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(XpColors.InkSecondary))
                }
            }
        }
    }
}

@Composable
private fun FactsCard(r: WeeklyReport) {
    XpCard {
        Text("小数据", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        Fact("🧾", "共 ${r.txnCount} 笔记录")
        r.biggest?.let { b ->
            Row(
                Modifier.padding(top = XpSpace.s), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
            ) {
                Text("💥")
                Text("最大一笔", style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
                MoneyText(b.amountCents, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
                Text(
                    "· ${b.merchant}", style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Fact("🔥", "连续记账最长 ${r.longestRecordStreak} 天")
        Fact("😴", "${r.zeroSpendDays} 天钱包在休眠")
    }
}

@Composable
private fun Fact(emoji: String, text: String) {
    Row(Modifier.padding(top = XpSpace.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        Text(emoji)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
    }
}
