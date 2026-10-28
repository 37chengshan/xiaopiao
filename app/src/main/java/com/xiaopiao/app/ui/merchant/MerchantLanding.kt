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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import androidx.compose.material.icons.outlined.Storefront
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.rememberNavScroll
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

@Composable
fun MerchantLanding(
    state: MerchantState,
    result: ExperimentResult?,
    onOpen: (MerchantRoute) -> Unit,
    onPrivacy: () -> Unit,
    onExit: () -> Unit = {},
) {
    val scroll = rememberScrollState()
    val exp = state.experiment
    val decision = exp?.let { state.decisionFor(it.id) }
    NavScaffold("小店版", rememberNavScroll(scroll), onBack = onExit, backLabel = "用户版", demo = true) { top ->
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = XpSpace.xl)) {
            GroupedSection(dividerInset = 62.dp) {
                item {
                    GroupedRow(
                        DEMO_SHOP_NAME, value = "数据来源", chevron = true,
                        leading = { IconTile(Icons.Outlined.Storefront, size = 34.dp, tint = XpColors.Info, background = XpColors.InfoSoft) },
                        onClick = onPrivacy,
                    )
                }
            }
            val rows = listOf(
                Triple(MerchantRoute.Insights, "人群洞察" to "看看邻居们喝什么、几点来、花多少", "随时可看"),
                Triple(MerchantRoute.Setup, "发起实验" to "选一个二选一小问题，发给邻居玩", exp?.let { "「${it.template.title}」· ${it.status.label}" } ?: "还没发布"),
                Triple(MerchantRoute.Results, "看结果" to "谁更受欢迎？结论有多稳？", result?.let { "样本 ${it.sampleSize} 人" } ?: "等实验发布"),
                Triple(MerchantRoute.Decide, "你来决定" to "3 个可以试试的方向，你选", decision?.let { "已选" } ?: "还没选"),
            )
            GroupedSection(dividerInset = 62.dp) {
                rows.forEachIndexed { i, (route, text, status) ->
                    item(route) {
                        val done = when (route) {
                            MerchantRoute.Setup -> exp != null
                            MerchantRoute.Results -> exp != null && exp.status != ExperimentStatus.DRAFT
                            MerchantRoute.Decide -> decision != null
                            else -> false
                        }
                        GroupedRow(
                            text.first, value = status, chevron = true,
                            leading = { StepBadge(i + 1, done) },
                        ) { onOpen(route) }
                    }
                }
            }
            if (state.decisions.isNotEmpty()) {
                GroupedSection(header = "决定记录 · 仅存本机", dividerInset = XpSpace.l) {
                    state.decisions.asReversed().forEachIndexed { i, d ->
                        item(i) { GroupedRow(d.headline, titleMaxLines = 3) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBadge(n: Int, done: Boolean) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(if (done) XpColors.Accent else XpColors.Fill),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Outlined.Check, null, tint = XpColors.OnAccent, modifier = Modifier.size(18.dp))
        else Text("$n", style = MaterialTheme.typography.labelLarge, color = XpColors.InkSecondary)
    }
}
