package com.xiaopiao.app.ui.ledger

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.capture.QuickAddParser
import com.xiaopiao.app.capture.QuickDraft
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
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

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = XpColors.Card, shape = RoundedCornerShape(topStart = XpRadius.l, topEnd = XpRadius.l),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = XpSpace.l).padding(bottom = XpSpace.l).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(XpSpace.m),
        ) {
            if (editing != null) {
                Text("编辑账单", style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
                ManualEntryForm(editing, now) { updated -> close { onSave(updated) } }
            } else {
                Segmented(listOf("一句话", "手动"), tab, { tab = it })
                AnimatedContent(
                    targetState = tab, label = "addTab",
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 4 * dir } + fadeIn())
                            .togetherWith(slideOutHorizontally { -it / 4 * dir } + fadeOut())
                    },
                ) { t ->
                    if (t == 0) QuickAddTab(now) { drafts -> close { onAddAll(drafts) } }
                    else ManualEntryForm(null, now) { txn -> close { onSave(txn) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddTab(now: Long, onConfirm: (List<Txn>) -> Unit) {
    var text by remember { mutableStateOf("") }
    val drafts = remember(text) { QuickAddParser.parse(text) }
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
        TextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), minLines = 2,
            placeholder = { Text("说一句：午饭 32，昨天打车 18 和奶茶 15", color = XpColors.InkTertiary) },
            colors = quietFieldColors(),
        )
        AnimatedContent(targetState = drafts.size, label = "draftCount") { n ->
            when {
                text.isBlank() -> Text("支持「今天 / 昨天 / 前天」和「收到工资 8000」", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
                n == 0 -> Text("还没认出金额，试试『午饭 32』", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
                else -> Text("认出 $n 笔，确认无误就记下～", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
            }
        }
        AnimatedVisibility(drafts.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(XpSpace.s), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                drafts.forEach { DraftChip(it) }
            }
        }
        PrimaryButton(
            text = if (drafts.size > 1) "全部记下（${drafts.size} 笔）" else "记下来",
            modifier = Modifier.fillMaxWidth(), enabled = drafts.isNotEmpty(),
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onConfirm(drafts.map { it.toTxn(now) })
        }
    }
}

@Composable
private fun DraftChip(d: QuickDraft) {
    val sign = if (d.direction == Direction.IN) "+" else ""
    SelectChip("${d.category.emoji} ${d.merchant} $sign¥${d.amountCents.yuan()} · ${d.dayLabel}", selected = true, onClick = {})
}
