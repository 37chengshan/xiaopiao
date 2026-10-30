package com.xiaopiao.app.ui.settings

import kotlinx.coroutines.delay
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Schedule

import com.xiaopiao.app.R
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.components.XpSheet
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.Segmented
import com.xiaopiao.app.ui.components.XpSwitch
import androidx.compose.material.icons.outlined.Lightbulb
import com.xiaopiao.app.ui.theme.ColorTheme
import com.xiaopiao.app.ui.LaunchSide
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.xiaopiao.app.ui.theme.ThemeMode
import com.xiaopiao.app.ui.theme.XpSpace

private val IconInset = 58.dp

@Composable
fun AppearanceSection(mode: ThemeMode, onMode: (ThemeMode) -> Unit) {
    GroupedSection(header = "外观") {
        item {
            Box(Modifier.fillMaxWidth().padding(XpSpace.m)) {
                Segmented(ThemeMode.entries.map { it.label }, mode.ordinal, { onMode(ThemeMode.entries[it]) })
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
    listenerConnected: Boolean,
    lastSeenAt: Long,
    postGranted: Boolean,
    onOpenListenerSettings: () -> Unit,
    onRequestPost: () -> Unit,
    onReconnect: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val now = rememberUpdatedClock()
    GroupedSection(header = "自动记账", dividerInset = IconInset) {
        item {
            GroupedRow(
                title = "通知读取",
                value = when {
                    !listenerEnabled -> stringResource(R.string.capture_off)
                    listenerConnected -> stringResource(R.string.capture_connected)
                    else -> stringResource(R.string.capture_disconnected)
                },
                leading = { IconTile(Icons.Outlined.Notifications, tint = XpColors.Positive, background = XpColors.PositiveSoft) },
                chevron = true,
                onClick = onOpenListenerSettings,
            )
        }
        if (listenerEnabled && !listenerConnected) {
            item {
                GroupedRow(
                    title = stringResource(R.string.capture_reconnect),
                    value = stringResource(R.string.capture_reconnect_hint),
                    leading = { IconTile(Icons.Outlined.Refresh, tint = XpColors.Warning, background = XpColors.WarningSoft) },
                    chevron = true,
                    onClick = onReconnect,
                )
            }
        }
        item {
            GroupedRow(
                title = stringResource(R.string.capture_last_seen),
                value = lastSeenText(lastSeenAt, now),
                leading = { IconTile(Icons.Outlined.Schedule, tint = XpColors.Info, background = XpColors.InfoSoft) },
            )
        }
        item {
            GroupedRow(
                title = stringResource(R.string.capture_keep_alive),
                value = stringResource(R.string.capture_keep_alive_hint),
                leading = { IconTile(Icons.Outlined.BatterySaver, tint = XpColors.InkSecondary, background = XpColors.Fill) },
                chevron = true,
                onClick = onOpenAppSettings,
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

/** Re-reads the clock every 30s so "3 分钟前" never goes stale while the screen stays open. */
@Composable
private fun rememberUpdatedClock(): Long {
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun lastSeenText(lastSeenAt: Long, now: Long): String {
    if (lastSeenAt <= 0L) return stringResource(R.string.capture_last_seen_never)
    val ago = (now - lastSeenAt).coerceAtLeast(0L)
    return when {
        ago < 60_000 -> stringResource(R.string.capture_just_now)
        ago < 3_600_000 -> stringResource(R.string.capture_ago_minutes, (ago / 60_000).toInt())
        ago < 86_400_000 -> stringResource(R.string.capture_ago_hours, (ago / 3_600_000).toInt())
        else -> stringResource(R.string.capture_ago_days, (ago / 86_400_000).toInt())
    }
}

@Composable
fun EditionSwitchSection(inMerchant: Boolean, onSwitch: () -> Unit) {
    GroupedSection(dividerInset = IconInset) {
        item {
            GroupedRow(
                title = if (inMerchant) "切换到个人版" else "切换到小店版", chevron = true,
                leading = { IconTile(if (inMerchant) Icons.Outlined.Person else Icons.Outlined.Storefront, selected = true) },
                onClick = onSwitch,
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

@Composable
fun ReplayOnboardingSection(onReplay: () -> Unit) {
    GroupedSection(header = "引导") {
        item {
            GroupedRow(
                title = "重看引导",
                leading = { IconTile(Icons.Outlined.Lightbulb, tint = XpColors.Warning, background = XpColors.WarningSoft) },
                onClick = onReplay,
            )
        }
    }
}

/** 主题配色: five three-colour swatches spread with weights, so 360dp and 411dp both fit; the name sits under each. */
@Composable
fun ColorThemeSection(theme: ColorTheme, onTheme: (ColorTheme) -> Unit) {
    GroupedSection(header = "主题配色") {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = XpSpace.s, vertical = XpSpace.m),
                verticalAlignment = Alignment.Top,
            ) {
                ColorTheme.entries.forEach { choice ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        ThemeSwatch(choice, selected = choice == theme) { onTheme(choice) }
                    }
                }
            }
        }
    }
}

/** A rounded tile: the accent fills the left, highlight and tint stack on the right. Selected = ink ring + check. */
@Composable
private fun ThemeSwatch(choice: ColorTheme, selected: Boolean, onClick: () -> Unit) {
    val (accent, highlight, tint) = choice.swatches(XpColors.isDark)
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.semantics { contentDescription = "主题 ${choice.label}" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).border(BorderStroke(2.dp, if (selected) XpColors.Ink else Color.Transparent), RoundedCornerShape(18.dp)).padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(Modifier.fillMaxSize().clip(shape)) {
                Box(Modifier.weight(3f).fillMaxHeight().background(accent), contentAlignment = Alignment.Center) {
                    if (selected) Icon(
                        Icons.Outlined.Check, contentDescription = null, tint = com.xiaopiao.app.ui.theme.Contrast.onAccent(accent),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Column(Modifier.weight(2f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(highlight))
                    Box(Modifier.weight(1f).fillMaxWidth().background(tint))
                }
            }
        }
        Text(
            choice.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) XpColors.Ink else XpColors.InkSecondary,
            maxLines = 1,
            modifier = Modifier.padding(top = XpSpace.xs),
        )
    }
}

@Composable
fun LaunchSideSection(side: LaunchSide, onSide: (LaunchSide) -> Unit) {
    GroupedSection(header = "打开 App 时进入") {
        item {
            Box(Modifier.fillMaxWidth().padding(XpSpace.m)) {
                Segmented(LaunchSide.entries.map { it.label }, side.ordinal, { onSide(LaunchSide.entries[it]) })
            }
        }
    }
}
