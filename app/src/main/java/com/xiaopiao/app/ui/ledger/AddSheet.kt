package com.xiaopiao.app.ui.ledger

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.capture.QuickAddParser
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.Segmented
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.icon
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.launch

/** "记一笔" bottom sheet. With [editing] set it is the edit form; otherwise quick-add + manual tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTxnSheet(
    editing: Txn?,
    now: Long,
    onDismiss: () -> Unit,
    onAddAll: (List<Txn>) -> Unit,
    onSave: (Txn) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    fun close(afterHide: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { afterHide(); onDismiss() }
    }

    XpSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = XpSpace.l).padding(bottom = XpSpace.l),
            verticalArrangement = Arrangement.spacedBy(XpSpace.m),
        ) {
            if (editing != null) {
                Text("编辑账单", style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
                ManualEntryForm(editing, now) { updated -> close { onSave(updated) } }
            } else {
                Segmented(listOf("一句话", "手动"), tab, { tab = it })
                // Short cross-fade only: the tab sits under a thumb that already slid.
                AnimatedContent(
                    targetState = tab, label = "addTab",
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                ) { t ->
                    if (t == 0) QuickAddTab(now) { drafts -> close { onAddAll(drafts) } }
                    else ManualEntryForm(null, now) { txn -> close { onSave(txn) } }
                }
            }
        }
    }
}

@Composable
private fun QuickAddTab(now: Long, onConfirm: (List<Txn>) -> Unit) {
    var text by remember { mutableStateOf("") }
    val drafts = remember(text) { QuickAddParser.parse(text) }
    val haptics = rememberXpHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
        TextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), minLines = 2,
            placeholder = { Text("说一句：午饭 32，昨天打车 18 和奶茶 15", color = XpColors.InkTertiary) },
            colors = quietFieldColors(),
        )
        AnimatedContent(targetState = drafts.size, label = "draftCount", transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(80)) }) { n ->
            when {
                text.isBlank() -> Text("支持「今天 / 昨天 / 前天」和「收到工资 8000」", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                n == 0 -> Text("还没认出金额，试试『午饭 32』", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                else -> Text("认出 $n 笔，确认无误就记下～", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
            }
        }
        if (drafts.isNotEmpty()) {
            // The sheet column already has the 16dp page margin.
            run {
                GroupedSection(dividerInset = 58.dp, margin = 0.dp) {
                    drafts.forEachIndexed { i, d ->
                        item(i) {
                            val income = d.direction == Direction.IN
                            GroupedRow(
                                title = d.merchant, subtitle = d.dayLabel,
                                leading = { IconTile(d.category.icon()) },
                                trailing = {
                                    MoneyText(
                                        d.amountCents, color = if (income) XpColors.Positive else XpColors.Ink,
                                        prefix = if (income) "+" else "",
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        XpButton(
            text = if (drafts.size > 1) "全部记下（${drafts.size} 笔）" else "记下来",
            onClick = { haptics.confirm(); onConfirm(drafts.map { it.toTxn(now) }) },
            modifier = Modifier.fillMaxWidth(), enabled = drafts.isNotEmpty(),
        )
    }
}
