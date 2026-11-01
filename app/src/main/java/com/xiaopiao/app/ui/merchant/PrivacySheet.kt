package com.xiaopiao.app.ui.merchant

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.merchant.K_ANONYMITY
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.tr
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

private val points: List<Triple<ImageVector, String, String>>
    get() = listOf(
        Triple(
            Icons.Outlined.PanTool,
            tr("自愿，随时撤回", "Optional, revocable anytime"),
            tr(
                "只有主动打开「数据贡献」的用户才会被统计，默认关闭，可随时撤回并一键删除自己的全部数据。",
                "Only people who turn on data sharing are counted. It is off by default, and you can withdraw and delete all of your data with one tap.",
            ),
        ),
        Triple(
            Icons.Outlined.Calculate,
            tr("只给聚合数字", "Aggregates only"),
            tr("你看到的是人群层面的占比和人数，不是任何一个人。", "You see shares and headcounts for a group, never a single person."),
        ),
        Triple(
            Icons.Outlined.GridOn,
            tr("少于 $K_ANONYMITY 人不展示", "Groups under $K_ANONYMITY hidden"),
            tr(
                "任何一格样本不足 $K_ANONYMITY 人就会画成斜线空格，写着「样本不足」，连具体人数也不透露。",
                "Any cell with fewer than $K_ANONYMITY people is drawn as a hatched blank marked “Not enough data” — even the headcount stays hidden.",
            ),
        ),
        Triple(
            Icons.Outlined.Lock,
            tr("通知原文不出手机", "Notification text stays on the phone"),
            tr(
                "支付通知只在用户手机里解析，上传的只有金额区间、品类、时段等结构化字段。",
                "Payment notifications are parsed on the user's phone. Only structured fields — amount band, category, time of day — are uploaded.",
            ),
        ),
        Triple(
            Icons.Outlined.SportsEsports,
            tr("游戏表态单独存放", "Game answers stored separately"),
            tr(
                "小游戏里的选择是「游戏表态」，和真实消费分开存、分开标注。",
                "Answers inside the games are game opinions: stored apart from real spending and labelled as such.",
            ),
        ),
        Triple(
            Icons.Outlined.Science,
            tr("人群数字只看聚合", "Crowd numbers, aggregated"),
            tr(
                "人群洞察和实验里只展示聚合数字，样本少于 20 人的格子不展示；你自己记的营收只存在这台手机上。",
                "Crowd insights and experiments show aggregates only, and cells under 20 people are hidden. The revenue you enter yourself never leaves this phone.",
            ),
        ),
    )

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PrivacySheet(onDismiss: () -> Unit) {
    XpSheet(onDismiss, containerColor = XpColors.Canvas) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = XpSpace.xl)) {
            Text(
                tr("数据怎么来的", "Where the data comes from"), Modifier.padding(horizontal = XpSpace.xl, vertical = XpSpace.s),
                style = MaterialTheme.typography.headlineMedium, color = XpColors.Ink,
            )
            GroupedSection(dividerInset = 58.dp) {
                points.forEach { (icon, title, body) ->
                    item(title) { GroupedRow(title, subtitle = body, subtitleMaxLines = 4, leading = { IconTile(icon) }) }
                }
            }
            MerchantButton(tr("知道了", "Got it"), onDismiss, Modifier.padding(horizontal = XpSpace.l).padding(top = XpSpace.l))
        }
    }
}
