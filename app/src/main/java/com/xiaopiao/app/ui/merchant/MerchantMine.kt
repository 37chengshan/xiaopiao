package com.xiaopiao.app.ui.merchant

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
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpSheet
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
    onDemoNote: () -> Unit,
    onExit: () -> Unit,
) {
    val experiment = state.experiment
    val decision = remember(experiment, state.decisions) { experiment?.let { state.decisionFor(it.id) } }
    val book = remember(state.revenue) { RevenueBook.normalize(state.revenue) }
    val today = remember { RevenueBook.startOfDay(System.currentTimeMillis()) }
    val weekCents = RevenueBook.windowTotal(book, RevenueBook.weekStart(today), today)
    val scroll = rememberScrollState()

    NavScaffold(title = "我的", scroll = NavScrollNone, demo = book.any { it.seeded }) { top ->
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = XpSpace.xxl),
        ) {
            Column(Modifier.padding(horizontal = XpSpace.l)) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.m))
                        .background(XpColors.Card).padding(XpSpace.l),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Outlined.Storefront, size = 40.dp, tint = XpColors.Accent, background = XpColors.AccentSoft)
                        Column(Modifier.weight(1f).padding(start = XpSpace.m)) {
                            Text(DEMO_SHOP_NAME, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                            Text("小店版 · 经营数据示例", style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
                        }
                        DemoPill()
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = XpSpace.l),
                        horizontalArrangement = Arrangement.spacedBy(XpSpace.xl),
                    ) {
                        MetricCell("营收天数") {
                            Text("${book.size} 天", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                        }
                        MetricCell("本周营收") {
                            Text(
                                RevenueBook.money(weekCents),
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                color = XpColors.Ink,
                            )
                        }
                    }
                }
            }

            SectionHeading("顾客实验")
            GroupedSection(dividerInset = 58.dp) {
                item("crowd") {
                    GroupedRow(
                        "人群洞察", value = "演示人群 300 人", chevron = true,
                        leading = { StepBadge(1, done = true, icon = Icons.Outlined.Insights) },
                    ) { onOpenSub(MerchantSub.Crowd) }
                }
                item("setup") {
                    GroupedRow(
                        "发起实验",
                        value = experiment?.let { "「${it.template.title}」" } ?: "还没发布",
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

            SectionHeading("数据")
            GroupedSection(dividerInset = 58.dp) {
                item("privacy") {
                    GroupedRow(
                        "数据说明", subtitle = "这些数字怎么来的、谁能看到", chevron = true,
                        leading = { IconTile(Icons.Outlined.Info) }, onClick = onPrivacy,
                    )
                }
                item("demo") {
                    GroupedRow(
                        "演示数据说明", subtitle = "哪些是示例、怎样换成自己的", chevron = true,
                        leading = { IconTile(Icons.Outlined.Storefront) }, onClick = onDemoNote,
                    )
                }
            }

            SectionHeading("账户")
            GroupedSection(dividerInset = 58.dp) {
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
                modifier = Modifier.padding(horizontal = XpSpace.l).padding(top = XpSpace.l),
            )
            NavBottomSpace()
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

/** Short, honest explanation of what is seeded and how it gets replaced. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DemoDataSheet(onDismiss: () -> Unit) {
    val points = listOf(
        "只有示例才标「演示」" to "带「演示」标记的数字由程序生成，用来展示小店版长什么样，不是真实顾客。",
        "你的数字会替换示例" to "记下自己的营收、或导入 CSV，同一天会替换成你的数字，标记跟着消失。",
        "标记会自动消失" to "所有演示行都被替换后，页面上的「演示数据」标记就不再见到了。",
        "只在你的手机里" to "小店版不读你的个人账单，也不上传任何数字；以后接入云端模型也只换文案。",
    )
    XpSheet(onDismiss, containerColor = XpColors.Canvas) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = XpSpace.xl)) {
            Text(
                "演示数据说明", Modifier.padding(horizontal = XpSpace.xl, vertical = XpSpace.s),
                style = MaterialTheme.typography.headlineMedium, color = XpColors.Ink,
            )
            GroupedSection(dividerInset = 58.dp) {
                points.forEachIndexed { i, (title, text) ->
                    item(i) { GroupedRow(title, subtitle = text, subtitleMaxLines = 4, leading = { IconTile(Icons.Outlined.Info) }) }
                }
            }
            XpButton(
                "知道了", onDismiss,
                Modifier.padding(horizontal = XpSpace.l).padding(top = XpSpace.l).fillMaxWidth(),
            )
        }
    }
}
