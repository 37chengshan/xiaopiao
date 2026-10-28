package com.xiaopiao.app.ui.ledger

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.data.DemoSeed
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.LargeTitle
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.ShimmerBlock
import com.xiaopiao.app.ui.components.UndoToastHost
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberNavScroll
import com.xiaopiao.app.ui.components.rememberUndoToastState
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun LedgerScreen(container: AppContainer) {
    val ledger = container.ledger
    val flow = remember(container) { ledger.observeAll() }
    val txns: List<Txn>? by flow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    val toast = rememberUndoToastState()
    val listState = rememberLazyListState()
    val now = System.currentTimeMillis()
    val hasDemo = txns.orEmpty().any { it.note == DemoSeed.NOTE }

    fun openAdd(txn: Txn?) { editing = txn; sheetOpen = true }

    NavScaffold("账本", rememberNavScroll(listState), demo = hasDemo) { top ->
        AnimatedContent(
            targetState = when { txns == null -> 0; txns.orEmpty().isEmpty() -> 1; else -> 2 },
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "ledgerState",
        ) { state ->
            when (state) {
                0 -> LedgerLoading(top)
                1 -> Column(Modifier.fillMaxSize().padding(top = top)) {
                    LargeTitle("账本")
                    LedgerEmptyState(onAdd = { openAdd(null) })
                }
                else -> {
                    val groups = remember(txns, now / 60_000) { LedgerGrouping.group(txns.orEmpty(), now) }
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = top, bottom = 104.dp)) {
                        item(key = "title") { LargeTitle("账本", txns?.let { monthSummary(it, now) }) }
                        groups.forEach { group ->
                            item(key = "day-${group.dayStart}") {
                                DaySection(
                                    group,
                                    onDelete = { txn ->
                                        scope.launch {
                                            ledger.delete(txn.id)?.let { gone ->
                                                toast.show("已删除「${gone.merchant}」", "撤销") { scope.launch { ledger.add(gone) } }
                                            }
                                        }
                                    },
                                    onEdit = { openAdd(it) },
                                )
                            }
                        }
                    }
                }
            }
        }

        UndoToastHost(toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
        AddFab(Modifier.align(Alignment.BottomEnd).padding(XpSpace.l)) { openAdd(null) }
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

/** The screen's one clay element: a floating capsule that opens the 记一笔 sheet. */
@Composable
private fun AddFab(modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.shadow(8.dp, shape, ambientColor = XpColors.Ink.copy(alpha = 0.2f), spotColor = XpColors.Ink.copy(alpha = 0.2f))
            .clip(shape).background(XpColors.Accent).pressableClick(onClick = onClick)
            .height(48.dp).padding(horizontal = XpSpace.l + 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = XpColors.OnAccent, modifier = Modifier.size(20.dp))
        Text("记一笔", style = MaterialTheme.typography.labelLarge, color = XpColors.OnAccent)
    }
}

@Composable
private fun LedgerLoading(top: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxSize().padding(top = top)) {
        LargeTitle("账本")
        Column(Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.l), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            repeat(5) { ShimmerBlock(Modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(12.dp)) }
        }
    }
}

private fun monthSummary(txns: List<Txn>, now: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1) }
    val monthStart = com.xiaopiao.app.domain.startOfDay(cal.timeInMillis)
    val out = txns.filter { it.direction == Direction.OUT && it.occurredAt >= monthStart }.sumOf { it.amountCents }
    return "本月支出 ¥${out.yuan()}"
}
