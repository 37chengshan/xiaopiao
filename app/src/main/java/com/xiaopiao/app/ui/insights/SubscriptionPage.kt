package com.xiaopiao.app.ui.insights

import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.IconTile
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.Icons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.insights.CancelGuides
import com.xiaopiao.app.insights.InsightsPrefs
import com.xiaopiao.app.insights.SubscriptionDetector
import com.xiaopiao.app.insights.SubscriptionFlag
import com.xiaopiao.app.insights.SubscriptionInsight
import com.xiaopiao.app.insights.annualCents
import com.xiaopiao.app.insights.daysBetween
import com.xiaopiao.app.insights.normalizeMerchant
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.launch

/** 订阅侦探: finds quiet auto-renewals, shows the yearly bill, guides cancelling (never cancels itself). */
@Composable
fun SubscriptionPage(input: InsightsInput, topPad: androidx.compose.ui.unit.Dp = 0.dp) {
    val context = LocalContext.current
    val prefs = remember { InsightsPrefs(context) }
    val scope = rememberCoroutineScope()
    val stored by prefs.observeCancelled().collectAsState(emptySet())
    var demoMarked by remember { mutableStateOf(setOf<String>()) }
    val cancelled = if (input.demo) demoMarked else stored

    val all = remember(input) { SubscriptionDetector.analyze(input.history, input.now) }
    val (done, active) = all.partition { InsightsPrefs.isCancelled(cancelled, it.subscription.merchant) }
    val yearly = active.sumOf { it.subscription.annualCents() }

    fun mark(merchant: String, value: Boolean) {
        if (input.demo) {
            val key = normalizeMerchant(merchant)
            demoMarked = if (value) demoMarked + key else demoMarked - key
        } else scope.launch { prefs.setCancelled(merchant, value) }
    }

    InsightsPage("揪出悄悄扣钱的自动续费", topPad = topPad) {
        if (all.isEmpty()) {
            InsightsEmpty(Icons.Outlined.Search, "暂时没抓到自动续费", "账本里还没有规律重复的扣款。多记几个月，侦探会自己出动～")
            return@InsightsPage
        }
        XpCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("这些订阅一年大约要花", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary, modifier = Modifier.weight(1f))
            }
            BigMoney(yearly)
            Text("共 ${active.size} 项 · 只做提醒，关不关由你决定", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        }
        if (active.isEmpty()) InsightsEmpty(Icons.Outlined.CheckCircle, "全部标记为已取消", "清爽！下面可以随时撤销标记。")
        active.forEach { SubscriptionCard(it, input.now, cancelled = false) { mark(it.subscription.merchant, true) } }
        if (done.isNotEmpty()) {
            Text("已标记取消", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
            done.forEach { SubscriptionCard(it, input.now, cancelled = true) { mark(it.subscription.merchant, false) } }
        }
        Text(CancelGuides.FOOTNOTE, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
    }
}

private fun cycleLabel(days: Int) = when {
    days <= 7 -> "每周"
    days <= 31 -> "每月"
    days <= 92 -> "每季"
    else -> "每年"
}

@Composable
private fun SubscriptionCard(insight: SubscriptionInsight, now: Long, cancelled: Boolean, onToggle: () -> Unit) {
    val sub = insight.subscription
    var open by rememberSaveable(sub.merchant) { mutableStateOf(false) }
    val days = daysBetween(now, sub.nextChargeAt)
    XpCard(Modifier.bounceClick { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.m)) {
            IconTile(Icons.Outlined.Autorenew, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(sub.merchant, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val meta = MaterialTheme.typography.labelMedium
                    val metaColor = XpColors.InkSecondary
                    Text("${cycleLabel(sub.cycleDays)} ", style = meta, color = metaColor)
                    MoneyText(sub.amountCents, style = meta, color = metaColor)
                    Text(" · 一年约 ", style = meta, color = metaColor)
                    MoneyText(sub.annualCents(), style = meta, color = metaColor)
                }
            }
            Icon(if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null, tint = XpColors.InkTertiary)
        }
        Row(Modifier.padding(top = XpSpace.s), horizontalArrangement = Arrangement.spacedBy(XpSpace.s), verticalAlignment = Alignment.CenterVertically) {
            if (cancelled) Pill("已标记取消") else CountdownChip(days, insight.lapsed)
            insight.flags.forEach { Pill(it.label + if (it == SubscriptionFlag.DUPLICATE_KIND) "（${insight.kind?.label.orEmpty()}）" else "") }
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = XpSpace.m), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                Text("怎么取消", style = MaterialTheme.typography.labelLarge, color = XpColors.Ink)
                CancelGuides.appTip(sub.merchant)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary) }
                CancelGuides.forMerchant(sub.merchant).forEach { g ->
                    Text("· 在${g.channel}扣的话", style = MaterialTheme.typography.labelMedium, color = XpColors.Ink)
                    g.steps.forEachIndexed { i, s -> Text("   ${i + 1}. $s", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary) }
                }
                XpButton(
                    if (cancelled) "撤销标记" else "我已取消", onToggle,
                    style = if (cancelled) XpButtonStyle.Secondary else XpButtonStyle.Primary, size = XpButtonSize.Small,
                )
            }
        }
    }
}

@Composable
private fun CountdownChip(days: Int, lapsed: Boolean) {
    val (text, color) = when {
        lapsed -> "好像已经没再扣了" to XpColors.Positive
        days < 0 -> "已过扣费日 ${-days} 天" to XpColors.InkSecondary
        days == 0 -> "今天扣费" to XpColors.Stamp
        days <= 3 -> "还有 $days 天扣费" to XpColors.Stamp
        else -> "还有 $days 天扣费" to XpColors.Warning
    }
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = XpSpace.s, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
