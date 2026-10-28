package com.xiaopiao.app.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.data.CaptureLogEntry
import com.xiaopiao.app.importer.ImportSummary
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay

/** 数据: CSV import, demo data, the on-device notification log, and the inline two-step wipe. */
@Composable
fun DataSection(
    importing: Boolean,
    summary: ImportSummary?,
    log: List<CaptureLogEntry>,
    demoMessage: String?,
    onPickCsv: () -> Unit,
    onClearLog: () -> Unit,
    onLoadDemo: () -> Unit,
    onClearAll: () -> Unit,
) {
    var logOpen by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    val haptics = rememberXpHaptics()
    LaunchedEffect(armed) { if (armed) { delay(6000); armed = false } }

    GroupedSection(
        header = "数据", dividerInset = 58.dp, modifier = Modifier.animateContentSize(spring(1f, 400f)),
        footer = demoMessage,
    ) {
        item {
            GroupedRow(
                title = "导入账单",
                subtitle = "支付宝 / 微信导出的 CSV，补齐开通之前的记录；重复的会自动跳过",
                value = if (importing) "导入中…" else null,
                leading = { IconTile(Icons.Outlined.FileUpload) },
                chevron = !importing,
                onClick = if (importing) null else onPickCsv,
            )
        }
        if (summary != null) item("importSummary") {
            if (summary.error != null) {
                GroupedRow(title = "导入没成功", subtitle = summary.error, titleColor = XpColors.Negative)
            } else {
                GroupedRow(
                    title = "识别为${summary.format.label}账单",
                    subtitle = "新增 ${summary.imported} · 重复跳过 ${summary.duplicates} · 读取失败 ${summary.failed}",
                )
            }
        }
        item {
            GroupedRow(
                title = "载入演示数据", subtitle = "约 3 周的示例账单，会标着「演示数据」",
                leading = { IconTile(Icons.Outlined.Science) },
                onClick = onLoadDemo,
            )
        }
        item {
            GroupedRow(
                title = "通知记录", subtitle = "看看小票读到了什么、哪些没认出来（只存在本机）",
                value = "${log.size} 条", leading = { IconTile(Icons.Outlined.NotificationsNone) },
                chevron = true, onClick = { logOpen = !logOpen },
            )
        }
        if (logOpen) {
            if (log.isEmpty()) item("logEmpty") {
                GroupedRow(title = "还没有记录", subtitle = "付一笔款，这里就会出现啦。")
            } else {
                log.take(40).forEach { e -> item("log-${e.id}") { LogRow(e) } }
                item("logClear") { GroupedRow(title = "清空记录", titleColor = XpColors.Negative, onClick = onClearLog) }
            }
        }
        item {
            if (!armed) {
                GroupedRow(
                    title = "清空我的全部数据", titleColor = XpColors.Negative,
                    leading = { IconTile(Icons.Outlined.DeleteOutline, tint = XpColors.Negative) },
                    onClick = { armed = true },
                )
            } else {
                Column(Modifier.fillMaxWidth().padding(XpSpace.l), verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                    Text(
                        "真的要清空吗？所有账单和通知记录都会删除，无法撤销。",
                        style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                        XpButton("确认清空", {
                            haptics.thud(); armed = false; onClearAll()
                        }, Modifier.weight(1f), style = XpButtonStyle.Destructive, size = XpButtonSize.Small)
                        XpButton("先不了", { armed = false }, Modifier.weight(1f), style = XpButtonStyle.Secondary, size = XpButtonSize.Small)
                    }
                }
            }
        }
    }
}
