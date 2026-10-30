package com.xiaopiao.app.ui.ledger

import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import com.xiaopiao.app.ui.components.NavBarHeight
import androidx.compose.runtime.derivedStateOf
import com.xiaopiao.app.ui.components.XpSheet
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import com.xiaopiao.app.ui.budget.BudgetPage
import com.xiaopiao.app.domain.BudgetSummary
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ReceiptLong
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
import com.xiaopiao.app.ui.components.liquidGlass
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberNavScroll
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberUndoToastState
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.components.AppTopActions
import com.xiaopiao.app.ui.components.LedgerSideLeading
import com.xiaopiao.app.ui.components.LocalTopBarHost
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.launch

/** Page margin and the gap between the cards of the ledger (docs/APP-DESIGN.md §3.7 栅格). */
private val PageInset = XpSpace.page
private val CardGap = XpSpace.cardGap

/**
 * Tracks which bills entered the list after the first emission: a new one from 记一笔, or one restored by 撤销.
 * Those rows get a one-shot insert animation; everything already on screen stays still. The marker is dropped
 * shortly after, so scrolling the row out and back in does not replay it.
 */
private class LandingTracker {
    private var known: Set<Long>? = null
    var fresh by androidx.compose.runtime.mutableStateOf<Set<Long>>(emptySet())
        private set

    fun observe(txns: List<Txn>) {
        val ids = txns.mapTo(HashSet(txns.size)) { it.id }
        val before = known
        known = ids
        // The first emission (or the first after a reload) is the list as it already was: nothing to announce.
        if (before == null) return
        fresh = ids - before
    }

    fun clear() { fresh = emptySet() }
}

/**
 * 账单 tab (docs/APP-DESIGN.md §3.2, adopted from the wallet reference per §3.7):
 * summary card → search/筛选 → day cards. Swipe or long-press to manage, calendar to jump to a day.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    container: AppContainer,
    @Suppress("UNUSED_PARAMETER") onVoiceStart: () -> Unit = {},
    searchRequested: Boolean = false,
    onSearchConsumed: () -> Unit = {},
    /** A filter handed over by another page (统计 chart taps); applied once, then consumed. */
    filterRequest: LedgerFilter? = null,
    onFilterConsumed: () -> Unit = {},
    /** 记一笔 and 编辑账单 are one pane owned by the shell (docs/DESIGN.md §7). */
    onRecord: () -> Unit = {},
    onEdit: (txn: Txn, onDelete: (Txn) -> Unit) -> Unit = { _, _ -> },
) {
    val onOpenSettings = LocalTopBarHost.current.openSettings
    // 「设置预算」 pulls up the budget drawer over the ledger (tt: a drawer, not a page).
    var budgetOpen by rememberSaveable { mutableStateOf(false) }
    if (budgetOpen) {
        XpSheet(onDismiss = { budgetOpen = false }) {
            BudgetPage(container, onBack = { budgetOpen = false }, onOpenSettings = onOpenSettings, inSheet = true)
        }
    }
    val budgetFlow = remember(container) { container.budget.observeSummary(container.ledger) }
    val budget: BudgetSummary? by budgetFlow.collectAsStateWithLifecycle(initialValue = null)

    val ledger = container.ledger
    val flow = remember(container) { ledger.observeAll() }
    val txns: List<Txn>? by flow.collectAsStateWithLifecycle(initialValue = null)

    val context = LocalContext.current
    val prefs = remember(context) { LedgerPrefs(context.applicationContext) }
    val hidden by prefs.observeHidden().collectAsStateWithLifecycle(initialValue = false)
    val recent by prefs.observeRecent().collectAsStateWithLifecycle(initialValue = emptyList<String>())
    // null = not read yet; the one-time 左滑 hint must never fire on a guess.
    val swipeHintShown by prefs.observeSwipeHintShown().collectAsStateWithLifecycle(initialValue = null)

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(LedgerFilter()) }
    var filterOpen by remember { mutableStateOf(false) }
    var calendarOpen by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Set<Long>?>(null) }
    // 搜索 in the top-left (here or from 小票 / 统计) arrives as a request from the shell.
    LaunchedEffect(searchRequested) { if (searchRequested) { searching = true; onSearchConsumed() } }
    LaunchedEffect(filterRequest) { filterRequest?.let { filter = it; onFilterConsumed() } }

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
    // A cache hit would otherwise flash the skeleton for two frames (docs/DESIGN.md §9.5: at least 200ms).
    val showSkeleton = com.xiaopiao.app.ui.motion.rememberSkeletonVisible(txns == null)
    // New / restored rows: they land with an insert animation (see LandingTracker).
    val landings = remember { LandingTracker() }
    LaunchedEffect(visible) { landings.observe(visible) }
    LaunchedEffect(landings.fresh) {
        if (landings.fresh.isNotEmpty()) {
            kotlinx.coroutines.delay(1_200)
            landings.clear()
        }
    }

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
    // 记账 / 搜索 / 多选 are three different top bars in the same slot; cross-fade them so the header
    // does not teleport when the mode changes (docs/APP-DESIGN.md §7.5).
    val chromeMode = when {
        searching -> "search"
        selection != null -> "select"
        else -> "normal"
    }
    NavScaffold(
        title = when {
            searching -> "搜索"
            selection != null -> "已选 ${selection?.size ?: 0} 项"
            else -> "记账"
        },
        scroll = if (searching) NavScrollNone else rememberNavScroll(listState),
        onBack = if (searching) ({ exitSearch() }) else null,
        backLabel = "取消",
        leading = if (!searching && selection == null) ({ LedgerSideLeading() }) else null,
        trailing = {
            AnimatedContent(
                targetState = chromeMode,
                transitionSpec = {
                    (fadeIn(tween(160, easing = XpEase.out)) + scaleIn(tween(200, easing = XpEase.out), initialScale = 0.94f)) togetherWith fadeOut(tween(100))
                },
                label = "ledgerChrome",
            ) { mode ->
                // AnimatedContent lays its content out in a Box; without this Row the receipt and the gear
                // (and 全选 / 取消) are drawn on top of each other.
                Row(verticalAlignment = Alignment.CenterVertically) {
                when (mode) {
                    "search" -> NavIconButton(Icons.Outlined.Tune, if (filter.activeCount > 0) "筛选 ${filter.activeCount}" else "筛选", onClick = { filterOpen = true })
                    "select" -> {
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

                    else -> AppTopActions()
                }
                }
            }
        },
        overlay = { glass ->
            // Which day is passing under the top capsules once its own header has scrolled away.
            if (!searching) FloatingDayPill(listState, groups, glass, Modifier.align(Alignment.TopCenter))
            // Floating delete pill: 48dp, 96dp above the tab bar, disabled (Negative at 30%) until something is picked.
            AnimatedVisibility(
                visible = selection != null,
                enter = fadeIn(tween(160, easing = XpEase.out)) + slideInVertically(tween(220, easing = XpEase.out)) { it / 2 } + scaleIn(tween(200, easing = XpEase.out), initialScale = 0.9f),
                exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 3 } + scaleOut(tween(160), targetScale = 0.92f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp).padding(horizontal = 56.dp),
            ) {
                DeleteCapsule(count = selection?.size ?: 0, backdrop = glass) { deleteSelected() }
            }
            UndoToastHost(
                toast,
                Modifier.align(Alignment.BottomCenter).padding(bottom = if (selection != null) 164.dp else 96.dp),
                backdrop = glass,
            )
        },
    ) { top ->
        // 搜索 is a secondary page: it slides in from the right over 记账 instead of swapping between frames.
        AnimatedContent(
            targetState = searching,
            transitionSpec = {
                if (targetState) {
                    (fadeIn(tween(180, easing = XpEase.out)) + slideInHorizontally(tween(240, easing = XpEase.out)) { it / 10 }) togetherWith fadeOut(tween(120))
                } else {
                    fadeIn(tween(160, easing = XpEase.out)) togetherWith (fadeOut(tween(140)) + slideOutHorizontally(tween(200, easing = XpEase.out)) { it / 10 })
                }
            },
            label = "ledgerSearch",
            modifier = Modifier.fillMaxSize(),
        ) { inSearch ->
            when {
                showSkeleton -> LedgerLoading(top)
                inSearch -> LedgerSearchPage(
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
                    onEdit = { onEdit(it, ::deleteTxn) },
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
                    onOpenBudget = { budgetOpen = true },
                    onOpenCalendar = { calendarOpen = true },
                    hidden = hidden,
                    onToggleHidden = { scope.launch { prefs.setHidden(!hidden) } },
                    selection = selection,
                    onToggleSelect = { txn ->
                        val current = selection.orEmpty()
                        selection = if (txn.id in current) current - txn.id else current + txn.id
                    },
                    onLongPress = { txn -> haptics.thud(); selection = setOf(txn.id) },
                    onEdit = { onEdit(it, ::deleteTxn) },
                    onDelete = { deleteTxn(it) },
                    onAdd = onRecord,
                    allEmpty = all.isEmpty(),
                    listState = listState,
                    reduceMotion = reduceMotion,
                    onClearConditions = { clearConditions() },
                    fresh = landings.fresh,
                    filter = filter,
                    // One peek per install, only on a plain ledger with bills, never while picking rows or searching.
                    showSwipeHint = swipeHintShown == false && !reduceMotion &&
                        selection == null && !searching && query.isBlank() && groups.isNotEmpty(),
                    onSwipeHintShown = { scope.launch { prefs.markSwipeHintShown() } },
                )
            }
        }

    }

    if (filterOpen) {
        LedgerFilterSheet(
            initial = filter,
            now = now,
            onApply = {
                filter = it
                filterOpen = false
                // A confirmed filter rewrites the list; take the user back to the newest bill with a move,
                // not a teleport (docs/APP-DESIGN.md §3.2).
                scope.launch {
                    if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
                }
            },
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

}

/** The current narrowing in one tappable line; tapping clears it back to 本月. */
@Composable
private fun ActiveFilterChip(filter: LedgerFilter, onClear: () -> Unit) {
    val parts = buildList {
        filter.categories.forEach { add(it.label) }
        filter.direction?.let { add(if (it == Direction.OUT) "支出" else "收入") }
        when (filter.range) {
            LedgerRange.Month -> {}
            LedgerRange.Custom -> {
                val fmt = java.text.SimpleDateFormat("M/d", java.util.Locale.getDefault())
                val from = filter.customFrom?.let { fmt.format(java.util.Date(it)) } ?: "…"
                val to = filter.customTo?.let { fmt.format(java.util.Date(it)) } ?: "…"
                add("$from–$to")
            }
            else -> add(filter.range.label)
        }
        if (filter.sources.isNotEmpty()) add(filter.sources.joinToString("、") { it.label })
        if (filter.minCents != null || filter.maxCents != null) add("金额")
    }
    Row(Modifier.fillMaxWidth().padding(top = XpSpace.s), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.AccentSoft)
                .pressableClick(onClick = onClear)
                .padding(start = XpSpace.m, end = XpSpace.s, top = XpSpace.xs, bottom = XpSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = XpColors.Accent, maxLines = 1)
            Icon(Icons.Outlined.Close, contentDescription = "清除筛选", tint = XpColors.Accent, modifier = Modifier.padding(start = XpSpace.xs).size(16.dp))
        }
    }
}

/** Items above the first day card: the summary (search sits in the top bar, 按天查看 inside the summary card). */
private const val HEADER_ITEMS = 1

@Composable
private fun LedgerList(
    top: androidx.compose.ui.unit.Dp,
    defs: List<CategoryDef>,
    groups: List<DayGroup>,
    summary: LedgerSummary,
    budget: BudgetSummary?,
    onOpenBudget: () -> Unit,
    onOpenCalendar: () -> Unit,
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
    fresh: Set<Long> = emptySet(),
    filter: LedgerFilter = LedgerFilter(),
    showSwipeHint: Boolean,
    onSwipeHintShown: () -> Unit,
) {
    // One open swipe row at a time; scrolling the list folds it back.
    val swipeRows = remember { SwipeRows() }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) swipeRows.openId = null }
    androidx.compose.runtime.CompositionLocalProvider(LocalSwipeRows provides swipeRows) {
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        // Top inset as content padding, so the list scrolls under the glass top bar instead of stopping at it.
        contentPadding = PaddingValues(start = PageInset, end = PageInset, top = top + XpSpace.m, bottom = 128.dp),
    ) {
        if (selection == null) {
            // tt: the date jump lives in the summary card, not in the top bar (which keeps only 小票 and 设置 on the right).
            item(key = "summary") {
                Box(Modifier.animateItem(fadeInSpec = tween(200), fadeOutSpec = null, placementSpec = tween(220, easing = XpEase.out))) {
                    Column {
                        LedgerSummaryCard(summary, hidden, onToggleHidden, budget, onOpenBudget, onOpenCalendar = onOpenCalendar.takeIf { !allEmpty })
                        // Arriving from a chart narrows the list; say so, or two bills look like the whole month.
                        if (!filter.isDefault) ActiveFilterChip(filter, onClear = onClearConditions)
                        com.xiaopiao.app.ui.capture.CaptureNudge()
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            item(key = "empty") {
                // Empty → content: the placeholder fades away instead of vanishing between two frames.
                Box(Modifier.animateItem(fadeInSpec = tween(220, easing = XpEase.out), placementSpec = null, fadeOutSpec = tween(160))) {
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
        }
        itemsIndexed(groups, key = { _, g -> "day-${g.dayStart}" }) { index, group ->
            Box(
                Modifier.fillMaxWidth()
                    // A day card that arrives or leaves (first bill of the day, last one deleted) fades and
                    // lets the cards below slide instead of jumping.
                    .animateItem(
                        fadeInSpec = tween(220, easing = XpEase.out),
                        fadeOutSpec = tween(140),
                        placementSpec = spring(dampingRatio = 1f, stiffness = 520f),
                    )
                    .padding(top = CardGap)
                    .staggerIn(if (selection != null) 0 else index, reduceMotion),
            ) {
                LedgerDayCard(
                    group = group,
                    defs = defs,
                    hidden = hidden,
                    selection = selection,
                    onToggle = onToggleSelect,
                    onLongPress = onLongPress,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    fresh = fresh,
                    hintFirstRow = showSwipeHint && index == 0,
                    onHintShown = onSwipeHintShown,
                )
            }
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

/**
 * The day under the top capsules, in a small glass pill (tt 2026-10-30: 浮在内容上的层). It appears once that day's
 * own header has gone under the bar, so the date is never shown twice.
 */
@Composable
private fun FloatingDayPill(
    listState: androidx.compose.foundation.lazy.LazyListState,
    groups: List<DayGroup>,
    backdrop: com.kyant.backdrop.Backdrop,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topInset = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues().calculateTopPadding()
    val line = with(density) { (topInset + NavBarHeight + 18.dp).toPx() }
    val headerPx = with(density) { 44.dp.toPx() }
    val day by remember(groups) {
        derivedStateOf {
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.offset + it.size > line }
            val key = item?.key as? String
            if (item == null || key == null || !key.startsWith("day-") || item.offset > line - headerPx) null
            else groups.firstOrNull { "day-${it.dayStart}" == key }
        }
    }
    // Keep the last day while fading out, so the text does not blank before the pill is gone.
    var shown by remember { mutableStateOf<DayGroup?>(null) }
    if (day != null) shown = day
    AnimatedVisibility(
        visible = day != null,
        enter = fadeIn(tween(160, easing = XpEase.out)) + scaleIn(tween(220, easing = XpEase.out), initialScale = 0.9f),
        exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.94f),
        modifier = modifier.padding(top = topInset + NavBarHeight + 6.dp),
    ) {
        val g = shown ?: return@AnimatedVisibility
        Row(
            Modifier.height(30.dp)
                .liquidGlass(
                    backdrop, RoundedCornerShape(XpRadius.pill),
                    // Rows pass right under it: mostly solid, so the date reads first and the glass shows at the rim.
                    film = XpColors.Card.copy(alpha = if (XpColors.isDark) 0.86f else 0.84f),
                    blur = 12.dp, shadow = 12.dp,
                )
                .padding(horizontal = XpSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(g.header, style = MaterialTheme.typography.labelMedium, color = XpColors.Ink, maxLines = 1)
            if (g.outCents > 0) {
                Text(
                    "  支 ${g.outCents.yuan()}",
                    style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DeleteCapsule(count: Int, backdrop: com.kyant.backdrop.Backdrop? = null, onClick: () -> Unit) {
    val enabled = count > 0
    val fill by androidx.compose.animation.animateColorAsState(
        targetValue = if (enabled) XpColors.Negative else XpColors.Negative.copy(alpha = 0.3f),
        animationSpec = tween(180, easing = XpEase.out),
        label = "deletePill",
    )
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            // Red glass: the bills being picked stay visible through it.
            .liquidGlass(
                backdrop,
                RoundedCornerShape(XpRadius.pill),
                film = fill.copy(alpha = fill.alpha * 0.82f),
                rim = XpColors.Negative.copy(alpha = 0.25f),
                fallback = fill,
            )
            .pressableClick(enabled = enabled, scaleDown = 0.96f, haptic = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // The count changes while the pill is already up; cross-fade it instead of swapping the label.
        AnimatedContent(
            targetState = count,
            transitionSpec = { fadeIn(tween(140, easing = XpEase.out)) togetherWith fadeOut(tween(100)) },
            label = "deleteCount",
        ) { n ->
            Text(
                if (n > 0) "删除 $n 笔" else "删除",
                style = MaterialTheme.typography.labelLarge,
                color = XpColors.OnAccent,
            )
        }
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
