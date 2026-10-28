package com.xiaopiao.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.importer.ImportSummary
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.ledger.GhostButton
import com.xiaopiao.app.ui.ledger.PrimaryButton
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay

/** "导入账单" card: pick a CSV, then show what happened. */
@Composable
fun ImportCard(importing: Boolean, summary: ImportSummary?, onPick: () -> Unit, modifier: Modifier = Modifier) {
    XpCard(modifier) {
        Text("导入账单", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
        Text(
            "支付宝 / 微信导出的 CSV 账单，补齐开通自动记账之前的记录。重复的账单会自动跳过。",
            style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(XpSpace.s))
        PrimaryButton(if (importing) "导入中…" else "选择 CSV 文件", Modifier.fillMaxWidth(), enabled = !importing, onClick = onPick)
        AnimatedContent(summary, label = "importSummary") { s ->
            if (s != null) {
                Column(Modifier.padding(top = XpSpace.m)) {
                    if (s.error != null) {
                        Text(s.error, style = MaterialTheme.typography.bodyMedium, color = XpColors.Stamp)
                    } else {
                        Text(
                            "识别为${s.format.label}账单",
                            style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.l)) {
                            Stat("新增", s.imported, XpColors.Positive)
                            Stat("重复跳过", s.duplicates, XpColors.InkSecondary)
                            Stat("读取失败", s.failed, if (s.failed > 0) XpColors.Warning else XpColors.InkSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
    }
}

/** Demo data + the two-step wipe. No system dialogs: the confirm happens inline. */
@Composable
fun DataCard(
    demoMessage: String?,
    onLoadDemo: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var armed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(armed) { if (armed) { delay(6000); armed = false } }
    XpCard(modifier) {
        Text("我的数据", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
        Text(
            "想先看看效果？载入约 3 周的演示数据，账单上会标着「演示数据」。",
            style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(XpSpace.s))
        PrimaryButton("载入演示数据", Modifier.fillMaxWidth(), onClick = onLoadDemo)
        AnimatedContent(demoMessage, label = "demoMsg") { m ->
            if (m != null) Text(m, Modifier.padding(top = XpSpace.s), style = MaterialTheme.typography.labelMedium, color = XpColors.Positive)
        }
        HorizontalDivider(Modifier.padding(vertical = XpSpace.l), thickness = 0.5.dp, color = XpColors.Hairline)
        AnimatedContent(armed, label = "wipe") { isArmed ->
            if (!isArmed) {
                GhostButton("清空我的全部数据", color = XpColors.Stamp, onClick = { armed = true })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    Text(
                        "真的要清空吗？所有账单和通知记录都会删除，无法撤销。",
                        style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                        PrimaryButton("确认清空", Modifier.weight(1f), destructive = true) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            armed = false
                            onClearAll()
                        }
                        PrimaryButton("先不了", Modifier.weight(1f)) { armed = false }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyCard(modifier: Modifier = Modifier) {
    XpCard(modifier) {
        Text("🔒 隐私", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
        Spacer(Modifier.height(XpSpace.s))
        listOf(
            "通知原文只在本机解析，不会上传。",
            "离开手机的只有结构化信息：金额、商家、时间、分类，而且只有你主动开启「小店」才会汇总匿名数据。",
            "小票只记录和总结，不会替你做购买决定。",
            "随时可以在这里清空全部数据，或在系统设置里关闭通知读取。",
        ).forEach {
            Text("· $it", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}
