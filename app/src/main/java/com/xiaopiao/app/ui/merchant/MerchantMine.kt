package com.xiaopiao.app.ui.merchant

import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.merchant.DEMO_SHOP_NAME
import com.xiaopiao.app.merchant.ExperimentResult
import com.xiaopiao.app.merchant.ExperimentStatus
import com.xiaopiao.app.merchant.MerchantState
import com.xiaopiao.app.merchant.RevenueBook
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.motion.xpRevealLayer
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * 我的: the shop's identity, the experiments (kept from the earlier build), the data notes,
 * and the way back to the user edition.
 */
@Composable
fun MerchantMineScreen(
    state: MerchantState,
    result: ExperimentResult?,
    onOpenSub: (MerchantSub) -> Unit,
    onPrivacy: () -> Unit,
    onExit: () -> Unit,
    onOpenReceipt: (Long) -> Unit = {},
) {
    val experiment = state.experiment
    val decision = remember(experiment, state.decisions) { experiment?.let { state.decisionFor(it.id) } }
    val book = remember(state.revenue) { RevenueBook.normalize(state.revenue) }
    val today = remember { RevenueBook.startOfDay(System.currentTimeMillis()) }
    val weekCents = RevenueBook.windowTotal(book, RevenueBook.weekStart(today), today)
    val scroll = rememberScrollState()

    NavScaffold(
        title = bi("我的", "Me"),
        scroll = NavScrollNone,
        trailing = { MerchantTopActions(onOpenReceipt = { onOpenReceipt(today) }) },
    ) { top ->
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top + XpSpace.m, bottom = merchantBottomInset())
                .xpRevealLayer(index = 0),
        ) {
            Column(Modifier.padding(horizontal = XpSpace.page)) {
                MerchantHeroCard(
                    label = bi("小店版", "Shop edition"),
                    corner = { IconTile(Icons.Outlined.Storefront, size = 36.dp, tint = XpColors.Accent, background = XpColors.Card.copy(alpha = 0.6f)) },
                    number = {
                        Text(
                            DEMO_SHOP_NAME, style = MaterialTheme.typography.titleLarge, color = XpColors.Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                    columns = {
                        StatCell(bi("营收天数", "Days logged"), bi("${book.size} 天", "${book.size}"), Modifier.weight(1f))
                        StatCell(bi("本周营收", "This week"), RevenueBook.money(weekCents), Modifier.weight(1.4f))
                    },
                )
            }

            SectionHeading(bi("顾客实验", "Customer experiments"))
            GroupedSection(dividerInset = 58.dp, margin = XpSpace.page) {
                item("crowd") {
                    GroupedRow(
                        "人群洞察", value = "本机人群", chevron = true,
                        leading = { StepBadge(1, done = true, icon = Icons.Outlined.Insights) },
                    ) { onOpenSub(MerchantSub.Crowd) }
                }
                item("setup") {
                    GroupedRow(
                        "发起实验",
                        value = experiment?.status?.label ?: "还没发布",
                        chevron = true,
                        leading = { StepBadge(2, done = experiment != null, icon = Icons.Outlined.Science) },
                    ) { onOpenSub(MerchantSub.Setup) }
                }
                item("results") {
                    GroupedRow(
                        "看结果",
                        value = result?.let { "样本 ${it.sampleSize} 人" } ?: "等实验发布",
                        chevron = true,
                        leading = {
                            StepBadge(
                                3,
                                done = experiment != null && experiment.status != ExperimentStatus.DRAFT,
                                icon = Icons.Outlined.TaskAlt,
                            )
                        },
                    ) { onOpenSub(MerchantSub.Results) }
                }
                item("decide") {
                    GroupedRow(
                        "你来决定", value = if (decision != null) "已选" else "还没选", chevron = true,
                        leading = { StepBadge(4, done = decision != null, icon = Icons.Outlined.Visibility) },
                    ) { onOpenSub(MerchantSub.Decide) }
                }
            }

            SectionHeading(bi("数据", "Data"))
            GroupedSection(dividerInset = 58.dp, margin = XpSpace.page) {
                item("privacy") {
                    GroupedRow(
                        "数据说明", subtitle = "这些数字怎么来的、谁能看到", chevron = true,
                        leading = { IconTile(Icons.Outlined.Info) }, onClick = onPrivacy,
                    )
                }
            }

            SectionHeading(bi("账户", "Account"))
            GroupedSection(dividerInset = 58.dp, margin = XpSpace.page) {
                item("exit") {
                    GroupedRow(
                        "切换回用户版", subtitle = "回到记账版，经营数字留在这里", chevron = true,
                        leading = { IconTile(Icons.Outlined.Person) }, onClick = onExit,
                    )
                }
            }

            Text(
                "小店版只在本机保存经营数字，不读你的个人账单。",
                style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
                modifier = Modifier.padding(horizontal = XpSpace.page).padding(top = XpSpace.l),
            )
        }
    }
}

@Composable
private fun StepBadge(n: Int, done: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(if (done) XpColors.Accent else XpColors.Fill),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = XpColors.OnAccent, modifier = Modifier.size(18.dp))
        } else {
            Icon(icon, contentDescription = null, tint = XpColors.InkSecondary, modifier = Modifier.size(17.dp))
        }
    }
}
