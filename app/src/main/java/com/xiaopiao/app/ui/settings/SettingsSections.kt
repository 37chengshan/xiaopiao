package com.xiaopiao.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
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
                title = "打印音效", subtitle = "打印和盖章时的小声音，会跟随系统静音",
                leading = { IconTile(Icons.Outlined.VolumeUp) },
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
    GroupedSection(
        header = "自动记账", dividerInset = IconInset,
        footer = "小票只读取支付宝和微信的支付通知，解析成「金额、商家、时间、分类」后就不再使用原文；原文不会离开手机。",
    ) {
        item {
            GroupedRow(
                title = "通知读取",
                subtitle = if (listenerEnabled) "支付宝 / 微信付款会自动入账" else "开启后，付款会自动入账",
                value = if (listenerEnabled) "已开启" else "未开启",
                leading = { IconTile(Icons.Outlined.Notifications) },
                chevron = true,
                onClick = onOpenListenerSettings,
            )
        }
        item {
            GroupedRow(
                title = "小票提醒", subtitle = "每晚小票打印好了，轻轻提醒你一下",
                value = if (postGranted) "已允许" else "未允许",
                leading = { IconTile(Icons.Outlined.NotificationsActive) },
                chevron = !postGranted,
                onClick = if (postGranted) null else onRequestPost,
            )
        }
    }
}

@Composable
fun PrivacySection() {
    GroupedSection(header = "隐私", dividerInset = IconInset) {
        listOf(
            "通知原文只在本机解析，不会上传。",
            "离开手机的只有结构化信息：金额、商家、时间、分类；只有你主动开启「小店」才会汇总匿名数据。",
            "小票只记录和总结，不会替你做购买决定。",
            "随时可以在这里清空全部数据，或在系统设置里关闭通知读取。",
        ).forEachIndexed { i, line ->
            item(i) {
                GroupedRow(
                    title = line, titleMaxLines = 4,
                    leading = { IconTile(Icons.Outlined.Lock) },
                )
            }
        }
    }
}

@Composable
fun AboutSection() {
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    GroupedSection(header = "关于", dividerInset = IconInset) {
        item {
            GroupedRow(
                title = "今日小票", subtitle = "认真生活，留下凭证。仅作记录，非正式票据。",
                value = version, leading = { IconTile(Icons.Outlined.Info) },
            )
        }
    }
}
