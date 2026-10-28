package com.xiaopiao.app.ui.circle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.SelectCapsule
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.XpSwitch
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * Per-member visibility matrix. Tapping a family member shows what that member shares; only the "我"
 * column is editable, and a toggle re-masks every past card immediately (state lives in the parent).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleShareSheet(
    members: List<CircleMember>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onToggle: (CircleShare, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = members.firstOrNull { it.id == selectedId } ?: members.first()
    XpSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = XpSpace.l),
        ) {
            Text("谁能看到什么", style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
            Text(
                "共享范围按项授权，默认只给汇总、不给明细。撤回后，群里已经发过的小票也会立刻脱敏。",
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(top = XpSpace.xs),
            )
            Spacer(Modifier.height(XpSpace.m))
            Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                members.forEach { member ->
                    SelectCapsule(member.name, selected = member.id == selected.id, onClick = { onSelect(member.id) })
                }
            }
            GroupedSection(
                header = if (selected.isMe) "家人能看到的" else "「${selected.name}」愿意分享的",
                margin = 0.dp,
                dividerInset = XpSpace.l,
            ) {
                CircleShare.entries.forEach { share ->
                    item(share.name) {
                        if (selected.isMe) {
                            GroupedRow(
                                title = share.label,
                                subtitle = share.hint,
                                trailing = {
                                    XpSwitch(
                                        checked = selected.shows(share),
                                        onCheckedChange = { onToggle(share, it) },
                                    )
                                },
                            )
                        } else {
                            GroupedRow(
                                title = share.label,
                                subtitle = share.hint,
                                value = if (selected.shows(share)) "已共享" else "未共享",
                            )
                        }
                    }
                }
            }
            Text(
                if (selected.isMe) {
                    "改动立即生效。单笔明细是小票全文的前提；关掉今日总额，明细与小票也会一起隐藏。"
                } else {
                    "家人的共享范围由 TA 自己决定，这里只能看。圈子数据只在家人之间，绝不进商家聚合。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(top = XpSpace.m),
            )
            Spacer(Modifier.height(XpSpace.l))
            XpButton(
                text = "好了",
                onClick = onDismiss,
                style = XpButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(XpSpace.xl))
        }
    }
}

/** Placeholder for the two 圈子 mini games; nothing is simulated or scored yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleGameSheet(title: String, onDismiss: () -> Unit) {
    XpSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.l)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
            Text(
                "还在路上。这一轮先把「家人小票群聊」做扎实，游戏下一轮再上。",
                style = MaterialTheme.typography.bodyMedium,
                color = XpColors.InkSecondary,
                modifier = Modifier.padding(top = XpSpace.s),
            )
            Text(
                "娱乐，不构成建议。",
                style = MaterialTheme.typography.labelMedium,
                color = XpColors.InkTertiary,
                modifier = Modifier.padding(top = XpSpace.m),
            )
            Spacer(Modifier.height(XpSpace.l))
            XpButton(
                text = "知道了",
                onClick = onDismiss,
                style = XpButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(XpSpace.xl))
        }
    }
}
