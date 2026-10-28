package com.xiaopiao.app.ui.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.data.DemoSeed
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.EmptyState
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.components.icon
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay

/** One calendar day as an inset grouped section: label + day total on top, one row per transaction. */
@Composable
fun DaySection(group: DayGroup, onDelete: (Txn) -> Unit, onEdit: (Txn) -> Unit) {
    GroupedSection(
        header = group.label,
        headerTrailing = { DayTotal(group) },
        dividerInset = 58.dp,
    ) {
        group.items.forEach { txn ->
            item(txn.id) { SwipeableTxnRow(txn, onDelete = { onDelete(txn) }, onClick = { onEdit(txn) }) }
        }
    }
}

@Composable
private fun DayTotal(group: DayGroup) {
    Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s), verticalAlignment = Alignment.CenterVertically) {
        if (group.outCents == 0L) {
            Text("零消费", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
        } else {
            Text("支出", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
            MoneyText(group.outCents, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
        }
        if (group.inCents > 0) {
            Text("收入", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
            MoneyText(group.inCents, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary, prefix = "+")
        }
    }
}

@Composable
fun TxnRow(txn: Txn, onClick: () -> Unit) {
    val income = txn.direction == Direction.IN
    val auto = txn.source == Source.NOTIFICATION || txn.source == Source.CSV
    // The category already shows as its icon, and the day header owns the date: no repeat, no clock.
    GroupedRow(
        title = txn.merchant,
        subtitle = txn.note.takeIf { it.isNotBlank() && it != DemoSeed.NOTE }
            ?: if (auto) txn.source.label else null,
        leading = { IconTile(txn.category.icon()) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                MoneyText(
                    txn.amountCents, style = MaterialTheme.typography.titleMedium,
                    color = if (income) XpColors.Positive else XpColors.Ink, prefix = if (income) "+" else "",
                )
            }
        },
        modifier = Modifier.background(XpColors.Card),
        onClick = onClick,
    )
}

/** Swipe left to delete: Negative fill reveals a trash icon, the row collapses, the host offers an undo toast. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTxnRow(txn: Txn, onDelete: () -> Unit, onClick: () -> Unit) {
    val haptics = rememberXpHaptics()
    var gone by remember(txn.id) { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.4f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { haptics.thud(); gone = true; true } else false
        },
    )
    // Let the row finish collapsing before the data change removes it from the list.
    LaunchedEffect(gone) { if (gone) { delay(180); onDelete() } }
    AnimatedVisibility(
        visible = !gone,
        exit = shrinkVertically(tween(160, easing = com.xiaopiao.app.ui.theme.XpEase.out)) + fadeOut(tween(120)),
    ) {
        SwipeToDismissBox(
            state = state,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                val swiping = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
                Box(
                    Modifier.fillMaxWidth().background(if (swiping) XpColors.Negative else XpColors.Card).padding(horizontal = XpSpace.xl),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (swiping) Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", tint = XpColors.OnAccent, modifier = Modifier.size(22.dp))
                }
            },
        ) { TxnRow(txn, onClick) }
    }
}

@Composable
fun LedgerEmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = XpSpace.xxl)) {
        EmptyState(
            icon = Icons.Outlined.Receipt,
            title = "还没有账单",
            body = "开启自动记账，或者说一句「午饭 32」。",
            actionLabel = "记一笔",
            onAction = onAdd,
        )
    }
}
