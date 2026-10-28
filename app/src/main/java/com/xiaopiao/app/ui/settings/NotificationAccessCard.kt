package com.xiaopiao.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.ledger.PrimaryButton
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

@Composable
fun NotificationAccessCard(
    listenerEnabled: Boolean,
    postGranted: Boolean,
    onOpenListenerSettings: () -> Unit,
    onRequestPost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    XpCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.m)) {
            Text("🔔", style = MaterialTheme.typography.headlineLarge)
            Column(Modifier.weight(1f)) {
                Text("自动记账", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                AnimatedContent(listenerEnabled, label = "access") { on ->
                    Text(
                        if (on) "已开启 · 支付宝 / 微信付款会自动入账" else "还没开启 · 开启后付款自动入账",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) XpColors.Positive else XpColors.Warning,
                    )
                }
            }
        }
        Spacer(Modifier.height(XpSpace.m))
        Text(
            "原文只在本机解析：小票只读取支付宝和微信的支付通知，解析成「金额、商家、时间、分类」后就不再使用原文，原文不会离开手机。",
            style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary,
        )
        Spacer(Modifier.height(XpSpace.m))
        PrimaryButton(
            text = if (listenerEnabled) "管理通知读取权限" else "去开启通知读取",
            modifier = Modifier.fillMaxWidth(), onClick = onOpenListenerSettings,
        )
        if (!postGranted) {
            Spacer(Modifier.height(XpSpace.l))
            Text("小票提醒", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
            Text(
                "每晚小票打印好了，轻轻提醒你一下。",
                style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            PrimaryButton("允许发送提醒", Modifier.fillMaxWidth(), onClick = onRequestPost)
        }
    }
}
