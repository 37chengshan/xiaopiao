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
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.merchant.K_ANONYMITY
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

private val points = listOf(
    Triple(Icons.Outlined.PanTool, "自愿，随时撤回", "只有主动打开「数据贡献」的用户才会被统计，默认关闭，可随时撤回并一键删除自己的全部数据。"),
    Triple(Icons.Outlined.Calculate, "只给聚合数字", "你看到的是人群层面的占比和人数，不是任何一个人。"),
    Triple(Icons.Outlined.GridOn, "少于 $K_ANONYMITY 人不展示", "任何一格样本不足 $K_ANONYMITY 人就会画成斜线空格，写着「样本不足」，连具体人数也不透露。"),
    Triple(Icons.Outlined.Lock, "通知原文不出手机", "支付通知只在用户手机里解析，上传的只有金额区间、品类、时段等结构化字段。"),
    Triple(Icons.Outlined.SportsEsports, "游戏表态单独存放", "小游戏里的选择是「游戏表态」，和真实消费分开存、分开标注。"),
    Triple(Icons.Outlined.Science, "人群数字只看聚合", "人群洞察和实验里只展示聚合数字，样本少于 20 人的格子不展示；你自己记的营收只存在这台手机上。"),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PrivacySheet(onDismiss: () -> Unit) {
    XpSheet(onDismiss, containerColor = XpColors.Canvas) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = XpSpace.xl)) {
            Text(
                "数据怎么来的", Modifier.padding(horizontal = XpSpace.xl, vertical = XpSpace.s),
                style = MaterialTheme.typography.headlineMedium, color = XpColors.Ink,
            )
            GroupedSection(dividerInset = 58.dp) {
                points.forEach { (icon, title, body) ->
                    item(title) { GroupedRow(title, subtitle = body, subtitleMaxLines = 4, leading = { IconTile(icon) }) }
                }
            }
            MerchantButton("知道了", onDismiss, Modifier.padding(horizontal = XpSpace.l).padding(top = XpSpace.l))
        }
    }
}
