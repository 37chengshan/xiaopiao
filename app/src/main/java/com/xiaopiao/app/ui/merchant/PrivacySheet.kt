package com.xiaopiao.app.ui.merchant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.merchant.K_ANONYMITY
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

private val points = listOf(
    Triple("🙋", "自愿，随时撤回", "只有主动打开「数据贡献」的用户才会被统计，默认关闭，可随时撤回并一键删除自己的全部数据。"),
    Triple("🧮", "只给聚合数字", "你看到的是人群层面的占比和人数，不是任何一个人。"),
    Triple("🚫", "少于 $K_ANONYMITY 人不展示", "任何一格样本不足 $K_ANONYMITY 人就会置灰，写着「样本不足」，连具体人数也不透露。"),
    Triple("📵", "通知原文不出手机", "支付通知只在用户手机里解析，上传的只有金额区间、品类、时段等结构化字段。"),
    Triple("🎮", "游戏表态单独存放", "小游戏里的选择是「游戏表态」，和真实消费分开存、分开标注。"),
    Triple("🏷️", "现在看到的是演示数据", "这个版本里商家侧的所有数字都是程序生成的演示数据，不是真实用户。"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = XpColors.Canvas) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = XpSpace.l).padding(bottom = XpSpace.xl),
            verticalArrangement = Arrangement.spacedBy(XpSpace.m),
        ) {
            Text("数据怎么来的", style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
            points.forEachIndexed { i, (emoji, title, body) ->
                Reveal(i) {
                    Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                        Text(emoji, style = MaterialTheme.typography.titleLarge)
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                            Text(body, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
                        }
                    }
                }
            }
            MerchantButton("知道了", onDismiss, Modifier.padding(top = XpSpace.s))
        }
    }
}
