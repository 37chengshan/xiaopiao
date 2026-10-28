package com.xiaopiao.app.ui.ledger

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.LargeTitle
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerScreen(container: AppContainer) {
    val ledger = container.ledger
    val flow = remember(container) { ledger.observeAll() }
    val txns: List<Txn>? by flow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var undo by remember { mutableStateOf<Txn?>(null) }
    val now = System.currentTimeMillis()

    LaunchedEffect(undo) {
        if (undo != null) { delay(4000); undo = null }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LargeTitle("账本", subtitle = txns?.let { monthSummary(it, now) })
            AnimatedContent(
                targetState = when { txns == null -> 0; txns.orEmpty().isEmpty() -> 1; else -> 2 },
                transitionSpec = { fadeIn(spring()) togetherWith fadeOut(spring()) },
                label = "ledgerState",
            ) { state ->
                when (state) {
                    0 -> Box(Modifier.fillMaxSize()) {}
                    1 -> LedgerEmptyState()
                    else -> {
                        val groups = remember(txns, now / 60_000) { LedgerGrouping.group(txns.orEmpty(), now) }
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 112.dp)) {
                            groups.forEach { group ->
                                stickyHeader(key = "day-${group.dayStart}") { DayHeader(group) }
                                items(group.items, key = { it.id }) { txn ->
                                    Box(Modifier.animateItem()) {
                                        SwipeableTxnRow(
                                            txn,
                                            onDelete = { scope.launch { ledger.delete(txn.id)?.let { undo = it } } },
                                            onClick = { editing = txn; sheetOpen = true },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = undo != null, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
            enter = slideInVertically(spring(dampingRatio = 0.75f)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            var shown by remember { mutableStateOf(undo) }
            if (undo != null) shown = undo
            shown?.let { gone ->
                UndoToast("已删除「${gone.merchant}」", onUndo = {
                    undo = null
                    scope.launch { ledger.add(gone) }
                })
            }
        }

        ExtendedFloatingActionButton(
            onClick = { editing = null; sheetOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(XpSpace.l),
            containerColor = XpColors.Accent, contentColor = XpColors.Card,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp, pressedElevation = 1.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("记一笔") },
        )
    }

    if (sheetOpen) {
        AddTxnSheet(
            editing = editing, now = System.currentTimeMillis(),
            onDismiss = { sheetOpen = false; editing = null },
            onAddAll = { list -> scope.launch { list.forEach { ledger.add(it) } } },
            onSave = { txn ->
                scope.launch { if (txn.id == 0L) ledger.add(txn) else ledger.update(txn) }
            },
        )
    }
}

private fun monthSummary(txns: List<Txn>, now: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1) }
    val monthStart = com.xiaopiao.app.domain.startOfDay(cal.timeInMillis)
    val out = txns.filter { it.direction == Direction.OUT && it.occurredAt >= monthStart }.sumOf { it.amountCents }
    return "本月支出 ¥${out.yuan()}"
}
