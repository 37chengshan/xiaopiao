package com.xiaopiao.app.ui.circle
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import com.xiaopiao.app.ui.components.MoneyText

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
import com.xiaopiao.app.ui.tr

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
            Text(tr("谁能看到什么", "Who can see what"), style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
            Text(
                tr(
                    "共享范围按项授权，默认只给汇总、不给明细。撤回后，群里已经发过的小票也会立刻脱敏。",
                    "Sharing is granted line by line: totals by default, never details. Revoking also masks receipts already posted.",
                ),
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
                header = if (selected.isMe) tr("家人能看到的", "What your family can see")
                else tr("「${selected.name}」愿意分享的", "What ${selected.name} shares"),
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
                                value = if (selected.shows(share)) tr("已共享", "Shared") else tr("未共享", "Not shared"),
                            )
                        }
                    }
                }
            }
            Text(
                if (selected.isMe) {
                    tr(
                        "改动立即生效。单笔明细是小票全文的前提；关掉今日总额，明细与小票也会一起隐藏。",
                        "Changes apply immediately. Transaction details gate the full receipt; turning off today's total hides both.",
                    )
                } else {
                    tr(
                        "家人的共享范围由 TA 自己决定，这里只能看。圈子数据只在家人之间，绝不进商家聚合。",
                        "Each family member controls their own sharing, so this is read-only. Circle data stays in the circle, never in merchant aggregates.",
                    )
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

/** Opened by tapping a receipt card: the receipt as it was printed, on the same thermal paper as 小票. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleReceiptSheet(post: ReceiptPost, author: CircleMember?, now: Long, onDismiss: () -> Unit) {
    val view = post.viewFor(author?.shares ?: emptyMap())
    val lines = remember(post) {
        post.items.groupBy { it.category }.map { (c, items) -> com.xiaopiao.app.ui.today.SharedReceiptLine(c, items.sumOf { it.cents }) }
    }
    XpSheet(onDismiss = onDismiss) {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = XpSpace.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            com.xiaopiao.app.ui.today.SharedReceiptPaper(
                dayStart = post.dayStart,
                totalCents = view.totalCents ?: post.totalCents,
                count = post.entryCount,
                lines = if (view.slices != null) lines else emptyList(),
                remark = post.remark,
                snapshot = post.snapshot,
                isToday = post.dayStart == com.xiaopiao.app.domain.startOfDay(now),
                postedAt = post.at,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = XpSpace.l))
        }
    }
}
