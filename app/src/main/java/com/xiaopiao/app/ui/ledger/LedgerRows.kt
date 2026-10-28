package com.xiaopiao.app.ui.ledger

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.data.DemoSeed
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

@Composable
fun EmojiBubble(emoji: String, size: Dp = 40.dp, selected: Boolean = false, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(
        if (selected) XpColors.AccentSoft else XpColors.Canvas,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "bubble",
    )
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = (size.value * 0.5f).sp)
    }
}

@Composable
fun SourceChip(label: String) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(XpColors.Canvas)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) { Text(label, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary) }
}

@Composable
fun DayHeader(group: DayGroup) {
    Row(
        Modifier.fillMaxWidth().background(XpColors.Canvas)
            .padding(horizontal = XpSpace.l, vertical = XpSpace.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(group.label, style = MaterialTheme.typography.labelLarge, color = XpColors.Ink)
        val summary = buildString {
            append(if (group.outCents == 0L) "零消费" else "支出 ¥${group.outCents.yuan()}")
            if (group.inCents > 0) append("  收入 +¥${group.inCents.yuan()}")
        }
        Text(summary, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
    }
}

@Composable
fun TxnRow(txn: Txn, onClick: () -> Unit) {
    Column(Modifier.background(XpColors.Card)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(horizontal = XpSpace.l, vertical = XpSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmojiBubble(txn.category.emoji)
            Spacer(Modifier.width(XpSpace.m))
            Column(Modifier.weight(1f)) {
                Text(
                    txn.merchant, style = MaterialTheme.typography.bodyLarge, color = XpColors.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SourceChip(txn.source.label)
                    Text(
                        "${txn.category.label} · ${LedgerGrouping.clock(txn.occurredAt)}",
                        style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                    )
                    if (txn.note == DemoSeed.NOTE) Pill(DemoSeed.NOTE)
                }
            }
            Spacer(Modifier.width(XpSpace.s))
            val income = txn.direction == Direction.IN
            Text(
                (if (income) "+¥" else "¥") + txn.amountCents.yuan(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (income) XpColors.Positive else XpColors.Ink,
                textAlign = TextAlign.End,
            )
        }
        HorizontalDivider(Modifier.padding(start = 68.dp), thickness = 0.5.dp, color = XpColors.Hairline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTxnRow(txn: Txn, onDelete: () -> Unit, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onDelete()
            true
        } else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val bg by animateColorAsState(
                if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) XpColors.Stamp else XpColors.Canvas,
                label = "swipeBg",
            )
            Box(Modifier.fillMaxWidth().background(bg).padding(horizontal = XpSpace.xl), contentAlignment = Alignment.CenterEnd) {
                Text("删除", color = XpColors.Card, style = MaterialTheme.typography.labelLarge)
            }
        },
    ) { TxnRow(txn, onClick) }
}

@Composable
fun LedgerEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = XpSpace.xxl, vertical = XpSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmojiBubble("🧾", size = 88.dp)
        Spacer(Modifier.size(XpSpace.l))
        Text("还没有账单～", style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
        Spacer(Modifier.size(XpSpace.s))
        Text(
            "打开通知读取，或者说一句『午饭 32』",
            style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, textAlign = TextAlign.Center,
        )
    }
}

/** Snackbar-style toast with an undo action. */
@Composable
fun UndoToast(message: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(XpColors.Ink)
            .padding(start = XpSpace.l, end = XpSpace.s, top = XpSpace.xs, bottom = XpSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = XpColors.Card, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(XpSpace.s))
        Box(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onUndo).padding(horizontal = XpSpace.m, vertical = XpSpace.s)) {
            Text("撤销", color = XpColors.Accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}
