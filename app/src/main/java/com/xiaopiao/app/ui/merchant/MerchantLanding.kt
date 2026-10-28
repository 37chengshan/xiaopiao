package com.xiaopiao.app.ui.merchant

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.merchant.DEMO_SHOP_NAME
import com.xiaopiao.app.merchant.ExperimentResult
import com.xiaopiao.app.merchant.ExperimentStatus
import com.xiaopiao.app.merchant.MerchantState
import com.xiaopiao.app.ui.components.LargeTitle
import com.xiaopiao.app.ui.components.SectionHeader
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

@Composable
fun MerchantLanding(
    state: MerchantState,
    result: ExperimentResult?,
    onOpen: (MerchantRoute) -> Unit,
    onPrivacy: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = XpSpace.xl)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { LargeTitle("小店", subtitle = DEMO_SHOP_NAME) }
            DemoPill(Modifier.padding(end = XpSpace.l))
        }
        Reveal(0) {
            Column(Modifier.padding(horizontal = XpSpace.l)) {
                XpCard {
                    Text("🏪 你是店主", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                    Text(
                        "看看附近邻居的消费习惯（自愿授权、聚合匿名），做个小实验，最后怎么做由你决定，应用只负责把数字摆清楚。",
                        Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary,
                    )
                    Text(
                        "🔒 数据怎么来的", Modifier.padding(top = XpSpace.s).clickable(onClick = onPrivacy),
                        style = MaterialTheme.typography.labelLarge, color = XpColors.Accent,
                    )
                }
            }
        }
        SectionHeader("四步走")
        val exp = state.experiment
        val decision = exp?.let { state.decisionFor(it.id) }
        val rows = listOf(
            Triple(MerchantRoute.Insights, "人群洞察" to "看看邻居们喝什么、几点来、花多少", "随时可看"),
            Triple(MerchantRoute.Setup, "发起实验" to "选一个二选一小问题，发给邻居玩", exp?.let { "「${it.template.title}」· ${it.status.label}" } ?: "还没发布"),
            Triple(MerchantRoute.Results, "看结果" to "谁更受欢迎？结论有多稳？", result?.let { "样本 ${it.sampleSize} 人" } ?: "等实验发布"),
            Triple(MerchantRoute.Decide, "你来决定" to "3 个可以试试的方向，你选", decision?.let { "已选 · 两周后复盘" } ?: "还没选"),
        )
        Column(Modifier.padding(horizontal = XpSpace.l), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            rows.forEachIndexed { i, (route, text, status) ->
                Reveal(i + 1) {
                    val done = when (route) {
                        MerchantRoute.Setup -> exp != null
                        MerchantRoute.Results -> exp != null && exp.status != ExperimentStatus.DRAFT
                        MerchantRoute.Decide -> decision != null
                        else -> false
                    }
                    StepRow(i + 1, text.first, text.second, status, done) { onOpen(route) }
                }
            }
        }
        Reveal(5) {
            Column(Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.l)) {
                GameCard(state, running = exp?.status == ExperimentStatus.RUNNING) { onOpen(MerchantRoute.Game) }
            }
        }
        if (state.decisions.isNotEmpty()) {
            SectionHeader("决定记录 · 仅存本机")
            Column(Modifier.padding(horizontal = XpSpace.l), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                state.decisions.asReversed().forEach {
                    XpCard { Text("✅ ${it.headline}", style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink) }
                }
            }
        }
    }
}

@Composable
private fun StepRow(n: Int, title: String, desc: String, status: String, done: Boolean, onClick: () -> Unit) {
    SelectableCard(false, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.m)) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (done) XpColors.Accent else XpColors.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (done) "✓" else "$n", style = MaterialTheme.typography.labelLarge, color = if (done) XpColors.Card else XpColors.Accent)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                Text(desc, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
            }
            Text(status, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
        }
    }
}

@Composable
private fun GameCard(state: MerchantState, running: Boolean, onPlay: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(XpColors.Accent)
            .clickable(onClick = onPlay).padding(XpSpace.l),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("🎮 玩一局 · 二选一大作战", style = MaterialTheme.typography.titleMedium, color = XpColors.Card)
        Text(
            if (running) "站在顾客的位置玩一局，你的选择会变成「游戏表态」加进实验结果。"
            else "站在顾客的位置体验一下。发布实验后，你的选择还会加进结果里。",
            style = MaterialTheme.typography.bodyMedium, color = XpColors.Card.copy(alpha = 0.92f),
        )
        Text(
            "已玩 ${state.gamesPlayed} 局 · 徽章 ${state.badgeCodes.size} 枚 · 演示数据",
            style = MaterialTheme.typography.labelMedium, color = XpColors.Card.copy(alpha = 0.8f),
        )
    }
}
