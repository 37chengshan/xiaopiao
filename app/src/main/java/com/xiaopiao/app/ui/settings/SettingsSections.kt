package com.xiaopiao.app.ui.settings

import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.components.XpSheet
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.Segmented
import com.xiaopiao.app.ui.components.XpSwitch
import com.xiaopiao.app.ui.theme.DesignStyle
import com.xiaopiao.app.ui.theme.ThemeMode
import com.xiaopiao.app.ui.theme.XpSpace

private val IconInset = 58.dp

@Composable
fun AppearanceSection(mode: ThemeMode, style: DesignStyle, onMode: (ThemeMode) -> Unit, onStyle: (DesignStyle) -> Unit) {
    GroupedSection(header = "外观") {
        item {
            Box(Modifier.fillMaxWidth().padding(XpSpace.m)) {
                Segmented(ThemeMode.entries.map { it.label }, mode.ordinal, { onMode(ThemeMode.entries[it]) })
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(XpSpace.m)) {
                Segmented(arrayOf("Apple", "Claude").toList(), style.ordinal, { onStyle(DesignStyle.entries[it]) })
            }
        }
    }
}

@Composable
fun ReceiptSection(soundOn: Boolean, onSound: (Boolean) -> Unit) {
    GroupedSection(header = "小票", dividerInset = IconInset) {
        item {
            GroupedRow(
                title = "打印音效",
                leading = { IconTile(Icons.Outlined.VolumeUp, tint = XpColors.Info, background = XpColors.InfoSoft) },
                trailing = { XpSwitch(soundOn, onSound) },
                onClick = { onSound(!soundOn) },
            )
        }
    }
}

@Composable
fun CaptureSection(
    listenerEnabled: Boolean,
    postGranted: Boolean,
    onOpenListenerSettings: () -> Unit,
    onRequestPost: () -> Unit,
) {
    GroupedSection(header = "自动记账", dividerInset = IconInset) {
        item {
            GroupedRow(
                title = "通知读取",
                value = if (listenerEnabled) "已开启" else "去开启",
                leading = { IconTile(Icons.Outlined.Notifications, tint = XpColors.Positive, background = XpColors.PositiveSoft) },
                chevron = true,
                onClick = onOpenListenerSettings,
            )
        }
        item {
            GroupedRow(
                title = "小票提醒",
                value = if (postGranted) "已允许" else "去允许",
                leading = { IconTile(Icons.Outlined.NotificationsActive, tint = XpColors.Warning, background = XpColors.WarningSoft) },
                chevron = !postGranted,
                onClick = if (postGranted) null else onRequestPost,
            )
        }
    }
}

@Composable
fun MerchantSwitchSection(onOpen: () -> Unit) {
    GroupedSection(dividerInset = IconInset) {
        item {
            GroupedRow(
                title = "切换到小店版", chevron = true,
                leading = { IconTile(Icons.Outlined.Storefront, selected = true) },
                onClick = onOpen,
            )
        }
    }
}

@Composable
fun AboutSection(onPrivacy: () -> Unit) {
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    GroupedSection(header = "关于", dividerInset = IconInset) {
        item {
            GroupedRow(
                title = "隐私说明", value = "了解更多", chevron = true,
                leading = { IconTile(Icons.Outlined.Lock) }, onClick = onPrivacy,
            )
        }
        item { GroupedRow(title = "版本", value = version, leading = { IconTile(Icons.Outlined.Info) }) }
    }
}

/** 了解更多 sheet: the long-form privacy promises that used to sit inline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySheet(onDismiss: () -> Unit) {
    XpSheet(onDismiss, containerColor = XpColors.Canvas) {
        Column(Modifier.padding(bottom = XpSpace.xl)) {
            Text("隐私说明", Modifier.padding(horizontal = XpSpace.xl, vertical = XpSpace.s), style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
            GroupedSection(dividerInset = IconInset) {
                listOf(
                    "通知原文只在本机解析，不会上传",
                    "只留金额、商家、时间、分类",
                    "只有你主动开启小店版，才会汇总匿名数据",
                    "随时可清空全部数据",
                ).forEachIndexed { i, line ->
                    item(i) { GroupedRow(title = line, titleMaxLines = 3, leading = { IconTile(Icons.Outlined.Lock) }) }
                }
            }
        }
    }
}
