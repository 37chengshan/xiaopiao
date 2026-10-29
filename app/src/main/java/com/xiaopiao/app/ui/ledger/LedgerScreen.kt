package com.xiaopiao.app.ui.ledger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ReceiptLong
import com.xiaopiao.app.ui.today.ReceiptStrip
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.CategoryDef
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.category.rememberCategoryDefs
import com.xiaopiao.app.ui.components.EmptyState
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.ShimmerBlock
import com.xiaopiao.app.ui.components.UndoToastHost
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberNavScroll
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberUndoToastState
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.wallet.WalletPanel
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.launch

/** Page margin and the gap between the cards of the ledger (docs/APP-DESIGN.md §3.7 栅格). */
private val PageInset = XpSpace.page
private val CardGap = XpSpace.cardGap

/**
 * 账单 tab (docs/APP-DESIGN.md §3.2, adopted from the wallet reference per §3.7):
 * summary card → search/筛选 → day cards. Swipe or long-press to manage, calendar to jump to a day.
 */
@Composable
fun LedgerScreen(
    container: AppContainer,
    @Suppress("UNUSED_PARAMETER") onVoiceStart: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    /** 已设预算时汇总卡底条改写成「本月预算剩余 ¥x」；null = 还没设，显示设置入口。 */
    budget: BudgetSummary? = null,
    /** 预算页由 budget worker 提供；合并时接上。 */
    onOpenBudget: () -> Unit = {},
) {
    val ledger = container.ledger
    val flow = remember(container) { ledger.observeAll() }
    val txns: List<Txn>? by flow.collectAsStateWithLifecycle(initialValue = null)

    val context = LocalContext.current
    val prefs = remember(context) { LedgerPrefs(context.applicationContext) }
    val hidden by prefs.observeHidden().collectAsStateWithLifecycle(initialValue = false)
    val recent by prefs.observeRecent().collectAsStateWithLifecycle(initialValue = emptyList<String>())

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(LedgerFilter()) }
    var filterOpen by remember { mutableStateOf(false) }
    var calendarOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var selection by remember { mutableStateOf<Set<Long>?>(null) }
    var walletOpen by remember { mutableStateOf(false) }

    val toast = rememberUndoToastState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = rememberXpHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val reduceMotion = rememberReducedMotion()
    val defs = rememberCategoryDefs(container.categories)

    val now = System.currentTimeMillis()
    val minute = now / 60_000
    val all = txns.orEmpty()
    val visible = remember(all, filter, query, minute) { filter.apply(all, now, query) }
    val groups = remember(visible, minute) { LedgerGrouping.group(visible, now) }
    val summary = remember(all, minute) { LedgerTotals.summary(all, now) }
    val searchHits = remember(all, filter, query, minute, searching) {
        if (searching) filter.apply(all, now, query) else emptyList()
    }
    val searchGroups = remember(searchHits, minute) { LedgerGrouping.group(searchHits, now) }

    fun deleteTxn(txn: Txn) {
        scope.launch {
            ledger.delete(txn.id)?.let { gone ->
                toast.show("已删除「${gone.merchant}」", "撤销") { scope.launch { ledger.add(gone) } }
            }
        }
    }

    fun deleteSelected() {
        val ids = selection ?: return
        if (ids.isEmpty()) return
        scope.launch {
            val gone = ids.mapNotNull { ledger.delete(it) }
            selection = null
            if (gone.isNotEmpty()) {
                toast.show("已删除 ${gone.size} 笔", "撤销") { scope.launch { gone.forEach { ledger.add(it) } } }
            }
        }
    }

    fun exitSearch() {
        searching = false
        query = ""
        keyboard?.hide()
    }

    fun clearConditions() {
        filter = LedgerFilter()
        query = ""
    }

    fun jumpToDay(day: Long, fromSearch: Boolean = false) {
        calendarOpen = false
        val target = startOfDay(day)
        val index = groups.indexOfFirst { it.dayStart == target }
        if (index < 0) {
            toast.show("那天没有账单")
            return
        }
        // When searching, only the search page exists; jump only works from the normal list.
        if (!fromSearch) scope.launch { listState.animateScrollToItem(HEADER_ITEMS + index) }
    }

    if (searching) {
        BackHandler { exitSearch() }
    } else if (selection != null) {
        BackHandler { selection = null }
    }

    // ── Chrome ────────────────────────────────────────────────────────────────────────────────
    NavScaffold(
        title = when {
            searching -> "搜索"
            selection != null -> "已选 ${selection?.size ?: 0} 项"
            else -> "记账"
        },
        scroll = if (searching) NavScrollNone else rememberNavScroll(listState),
        onBack = if (searching) ({ exitSearch() }) else null,
        backLabel = "取消",
        leading = if (!searching && selection == null) {
            ({
                NavIconButton(Icons.Outlined.CreditCard, "卡包", onClick = { walletOpen = true })
                // Search sits left of the title so the right side keeps three items at 360dp × 1.3.
                NavIconButton(Icons.Outlined.Search, "搜索", onClick = { searching = true })
            })
        } else null,
        trailing = {
            when {
                searching -> NavIconButton(Icons.Outlined.Tune, if (filter.activeCount > 0) "筛选 ${filter.activeCount}" else "筛选", onClick = { filterOpen = true })
                selection != null -> {
                    Text(
                        if (selection?.size == visible.size && visible.isNotEmpty()) "取消全选" else "全选",
                        style = MaterialTheme.typography.bodyMedium,
                        color = XpColors.Accent,
                        modifier = Modifier.clip(RoundedCornerShape(XpRadius.pill))
                            .pressableClick(haptic = false) {
                                val ids = visible.map { it.id }.toSet()
                                selection = if (selection == ids) emptySet() else ids
                            }
                            .padding(horizontal = XpSpace.s, vertical = XpSpace.xs),
                    )
                    Text(
                        "取消",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = XpColors.Accent,
                        modifier = Modifier.clip(RoundedCornerShape(XpRadius.pill))
                            .pressableClick(haptic = false) { selection = null }
                            .padding(horizontal = XpSpace.s, vertical = XpSpace.xs),
                    )
                }
                else -> {
                    // Reference `041428` puts 按天跳转 (calendar) in the top bar; we keep the receipt
                    // strip and the gear to its right (tt 要求设置在右上角).
                    NavIconButton(Icons.Outlined.CalendarMonth, "按天跳转", onClick = { calendarOpen = true })
                    ReceiptStrip()
                    NavIconButton(Icons.Outlined.Settings, "设置", onClick = onOpenSettings)
                }
            }
        },
    ) { top ->
        when {
            txns == null -> LedgerLoading(top)
            searching -> LedgerSearchPage(
                top = top,
                defs = defs,
                query = query,
                onQuery = { query = it },
                onSubmit = { scope.launch { prefs.pushRecent(query) } },
                recent = recent,
                onRecentPick = { query = it },
                onClearRecent = { scope.launch { prefs.clearRecent() } },
                groups = searchGroups,
                hidden = hidden,
                hits = searchHits,
                onEdit = { editing = it; addOpen = true },
                onClearConditions = { clearConditions() },
                filter = filter,
                onFilter = { filterOpen = true },
            )
            else -> LedgerList(
                top = top,
                defs = defs,
                groups = groups,
                summary = summary,
                budget = budget,
                onOpenBudget = onOpenBudget,
                hidden = hidden,
                onToggleHidden = { scope.launch { prefs.setHidden(!hidden) } },
                selection = selection,
                onToggleSelect = { txn ->
                    val current = selection.orEmpty()
                    selection = if (txn.id in current) current - txn.id else current + txn.id
                },
                onLongPress = { txn -> haptics.thud(); selection = setOf(txn.id) },
                onEdit = { editing = it; addOpen = true },
                onDelete = { deleteTxn(it) },
                onAdd = { editing = null; addOpen = true },
                allEmpty = all.isEmpty(),
                listState = listState,
                reduceMotion = reduceMotion,
                onClearConditions = { clearConditions() },
            )
        }

        // Floating delete pill: 48dp, 96dp above the tab bar, disabled (Negative at 30%) until something is picked.
        if (selection != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp).padding(horizontal = 56.dp)) {
                DeleteCapsule(count = selection?.size ?: 0) { deleteSelected() }
            }
        }

        UndoToastHost(
            toast,
            Modifier.align(Alignment.BottomCenter).padding(bottom = if (selection != null) 164.dp else 96.dp),
        )
    }

    if (filterOpen) {
        LedgerFilterSheet(
            initial = filter,
            now = now,
            onApply = { filter = it; filterOpen = false },
            onDismiss = { filterOpen = false },
        )
    }
    if (calendarOpen) {
        LedgerCalendarSheet(
            anchor = LedgerRanges.monthStart(now),
            selected = null,
            today = startOfDay(now),
            onPick = { jumpToDay(it) },
            onDismiss = { calendarOpen = false },
        )
    }
    // 卡包是全屏面板：用 Dialog 铺满整屏（含底部导航），面板自带 FLAG_SECURE 与下滑关闭。
    if (walletOpen) {
        Dialog(
            onDismissRequest = { walletOpen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                // 卡号与会员码只在这块面板上，窗口级禁截屏（Dialog 有独立 window）。
                securePolicy = SecureFlagPolicy.SecureOn,
            ),
        ) {
            WalletPanel(
                container = container,
                now = System.currentTimeMillis(),
                onDismiss = { walletOpen = false },
            )
        }
    }

    if (addOpen) {
        AddTxnSheet(
            editing = editing,
            now = System.currentTimeMillis(),
            onDismiss = { addOpen = false; editing = null },
            onAddAll = { list -> scope.launch { list.forEach { ledger.add(it) } } },
            onSave = { txn -> scope.launch { if (txn.id == 0L) ledger.add(txn) else ledger.update(txn) } },
        )
    }
}

/** Items above the first day card: the summary (search lives in the top bar, as in `041428`). */
private const val HEADER_ITEMS = 1

@Composable
private fun LedgerList(
    top: androidx.compose.ui.unit.Dp,
    defs: List<CategoryDef>,
    groups: List<DayGroup>,
    summary: LedgerSummary,
    budget: BudgetSummary?,
    onOpenBudget: () -> Unit,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    selection: Set<Long>?,
    onToggleSelect: (Txn) -> Unit,
    onLongPress: (Txn) -> Unit,
    onEdit: (Txn) -> Unit,
    onDelete: (Txn) -> Unit,
    onAdd: () -> Unit,
    allEmpty: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    reduceMotion: Boolean,
    onClearConditions: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(top = top),
        state = listState,
        contentPadding = PaddingValues(start = PageInset, end = PageInset, top = XpSpace.m, bottom = 128.dp),
    ) {
        if (selection == null) {
            item(key = "summary") { LedgerSummaryCard(summary, hidden, onToggleHidden, budget, onOpenBudget) }
        }
        if (groups.isEmpty()) {
            item(key = "empty") {
                if (allEmpty) {
                    Column(Modifier.padding(top = XpSpace.xxl)) {
                        EmptyState(
                            icon = Icons.Outlined.ReceiptLong,
                            title = "还没有账单",
                            body = "开启自动记账，或者说一句「午饭 32」。",
                            actionLabel = "记一笔",
                            onAction = onAdd,
                        )
                    }
                } else {
                    LedgerNoResults(onClearConditions = onClearConditions)
                }
            }
        }
        itemsIndexed(groups, key = { _, g -> "day-${g.dayStart}" }) { index, group ->
            Box(Modifier.padding(top = CardGap).staggerIn(if (selection != null) 0 else index, reduceMotion)) {
                LedgerDayCard(
                    group = group,
                    defs = defs,
                    hidden = hidden,
                    selection = selection,
                    onToggle = onToggleSelect,
                    onLongPress = onLongPress,
                    onEdit = onEdit,
                    onDelete = onDelete,
                )
            }
        }
    }
}

/** Search page: one field, the recent list, and the matching rows shown as the same day cards. */
@Composable
private fun LedgerSearchPage(
    top: androidx.compose.ui.unit.Dp,
    defs: List<CategoryDef>,
    query: String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    recent: List<String>,
    onRecentPick: (String) -> Unit,
    onClearRecent: () -> Unit,
    groups: List<DayGroup>,
    hidden: Boolean,
    hits: List<Txn>,
    onEdit: (Txn) -> Unit,
    onClearConditions: () -> Unit,
    filter: LedgerFilter,
    onFilter: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = top).padding(horizontal = PageInset)) {
        Row(Modifier.fillMaxWidth().padding(top = XpSpace.m), verticalAlignment = Alignment.CenterVertically) {
            LedgerSearchField(query, onQuery, onSubmit, Modifier.weight(1f))
            Text(
                if (filter.activeCount > 0) "筛选 ${filter.activeCount}" else "筛选",
                style = MaterialTheme.typography.bodyLarge,
                color = if (filter.activeCount > 0) XpColors.Accent else XpColors.Ink,
                modifier = Modifier
                    .clip(RoundedCornerShape(XpRadius.pill))
                    .pressableClick(haptic = false, onClick = onFilter)
                    .padding(start = XpSpace.m, end = XpSpace.xs, top = XpSpace.s, bottom = XpSpace.s),
            )
        }
        if (query.isBlank()) {
            LedgerRecentSearches(recent, onRecentPick, onClearRecent)
            return@Column
        }
        val outTotal = hits.filter { it.direction == Direction.OUT }.sumOf { it.amountCents }
        Text(
            "${hits.size} 笔 · 支出 ${if (hidden) "¥ $AMOUNT_MASK" else "¥ ${outTotal.yuan()}"}",
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkSecondary,
            modifier = Modifier.padding(top = XpSpace.m, bottom = XpSpace.xs),
        )
        if (hits.isEmpty()) {
            LedgerNoResults(onClearConditions = onClearConditions)
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 128.dp)) {
            itemsIndexed(groups, key = { _, g -> "hit-${g.dayStart}" }) { _, group ->
                Box(Modifier.padding(top = CardGap)) {
                    LedgerDayCard(
                        group = group,
                        defs = defs,
                        hidden = hidden,
                        selection = null,
                        onToggle = {},
                        onLongPress = {},
                        onEdit = onEdit,
                        onDelete = {},
                        swipeToDelete = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteCapsule(count: Int, onClick: () -> Unit) {
    val enabled = count > 0
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .clip(RoundedCornerShape(XpRadius.pill))
            .background(if (enabled) XpColors.Negative else XpColors.Negative.copy(alpha = 0.3f))
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            if (count > 0) "删除 $count 笔" else "删除",
            style = MaterialTheme.typography.labelLarge,
            color = XpColors.OnAccent,
        )
    }
}

@Composable
private fun LedgerLoading(top: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxSize().padding(top = top).padding(horizontal = PageInset, vertical = XpSpace.m), verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
        ShimmerBlock(Modifier.fillMaxWidth().height(116.dp), RoundedCornerShape(XpRadius.contentCard))
        ShimmerBlock(Modifier.fillMaxWidth().height(40.dp), RoundedCornerShape(XpRadius.pill))
        repeat(4) { ShimmerBlock(Modifier.fillMaxWidth().height(56.dp), RoundedCornerShape(XpRadius.listCard)) }
    }
}
