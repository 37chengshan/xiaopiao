package com.xiaopiao.app.ui.ledger

import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.MiniBars
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
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ReceiptLong
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
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NumberTicker
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.DemoChip
import com.xiaopiao.app.ui.components.ShimmerBlock
import com.xiaopiao.app.ui.components.UndoToastHost
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberNavScroll
import com.xiaopiao.app.ui.components.rememberUndoToastState
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpFonts
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun LedgerScreen(container: AppContainer, onOpenInsights: () -> Unit = {}, onPrint: () -> Unit = {}, onVoiceStart: () -> Unit = {}) {
    val ledger = container.ledger
    val flow = remember(container) { ledger.observeAll() }
    val txns: List<Txn>? by flow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var sheetOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(LedgerFilter()) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    val toast = rememberUndoToastState()
    val listState = rememberLazyListState()
    val now = System.currentTimeMillis()
    val hasDemo = txns.orEmpty().any { it.note == DemoSeed.NOTE }
    val reduceMotion = rememberReducedMotion()

    fun openAdd(txn: Txn?) { editing = txn; sheetOpen = true }

    NavScaffold(
        "记账",
        rememberNavScroll(listState),
        demo = hasDemo,
        trailing = {
            NavIconButton(Icons.Outlined.Insights, "统计", onOpenInsights)
            NavIconButton(Icons.Outlined.ReceiptLong, "打印今日小票", onPrint)
        },
    ) { top ->
        AnimatedContent(
            targetState = when { txns == null -> 0; txns.orEmpty().isEmpty() -> 1; else -> 2 },
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "ledgerState",
        ) { state ->
            when (state) {
                0 -> LedgerLoading(top)
                1 -> Column(Modifier.fillMaxSize().padding(top = top)) {
                    LedgerEmptyState(onAdd = { openAdd(null) })
                }
                else -> {
                    val visible = filter.apply(txns.orEmpty(), now, query)
                    val groups = remember(visible, now / 60_000) { LedgerGrouping.group(visible, now) }
                    // One composed entrance per data load: the list rises 8dp and fades in, then stays still.
                    val enter = remember(visible.isEmpty()) { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(visible.isEmpty()) {
                        if (reduceMotion) enter.snapTo(1f) else { enter.snapTo(0f); enter.animateTo(1f, tween(220, easing = XpEase.out)) }
                    }
                    LazyColumn(
                        Modifier.fillMaxSize().graphicsLayer {
                            alpha = enter.value
                            translationY = (1f - enter.value) * 8.dp.toPx()
                        },
                        state = listState,
                        contentPadding = PaddingValues(top = top, bottom = 200.dp),
                    ) {
                        item(key = "search") {
                            LedgerSearchBar(
                                query = query,
                                onQuery = { query = it },
                                filterActive = filter.active,
                                onFilter = { filterOpen = true },
                            )
                        }
                        item(key = "hero") { LedgerHero(visible, now) }
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
        com.xiaopiao.app.ui.components.RecordBall(
            onManual = { openAdd(null) },
            onVoice = onVoiceStart,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = XpSpace.l, bottom = 84.dp),
        )
    }

    if (filterOpen) LedgerFilterSheet(filter, { filter = it; filterOpen = false }, { filterOpen = false })

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
        modifier.clip(shape).background(XpColors.Primary).pressableClick(onClick = onClick)
            .height(48.dp).padding(horizontal = XpSpace.l + 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = XpColors.OnPrimary, modifier = Modifier.size(20.dp))
        Text("记一笔", style = MaterialTheme.typography.labelLarge, color = XpColors.OnPrimary)
    }
}

@Composable
private fun LedgerLoading(top: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxSize().padding(top = top)) {
        Column(Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.l), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            repeat(5) { ShimmerBlock(Modifier.fillMaxWidth().height(52.dp), RoundedCornerShape(12.dp)) }
        }
    }
}

/** Hero: this month's spend as one big number, with a 14-day mini chart (last bar = today, in clay). */
@Composable
private fun LedgerHero(txns: List<Txn>, now: Long) {
    val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1) }
    val monthStart = com.xiaopiao.app.domain.startOfDay(cal.timeInMillis)
    val out = txns.filter { it.direction == Direction.OUT }
    val monthTotal = out.filter { it.occurredAt >= monthStart }.sumOf { it.amountCents }
    val today = com.xiaopiao.app.domain.startOfDay(now)
    val day = 24L * 60 * 60 * 1000
    val bars = (13 downTo 0).map { d ->
        val from = today - d * day
        out.filter { it.occurredAt >= from && it.occurredAt < from + day }.sumOf { it.amountCents }.toFloat()
    }
    Column(Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.s)) {
        XpCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("本月支出", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary, modifier = Modifier.weight(1f))
            }
            NumberTicker(
                text = monthTotal.yuan(),
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = XpFonts.sans),
                rollFromZero = false,   // the KPI must never show zeros while it rolls
                staggerMs = 28,
            )
            MiniBars(bars, Modifier.padding(top = XpSpace.m))
        }
    }
}
