package com.xiaopiao.app.ui.insights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.OpenRequest

import com.xiaopiao.app.data.DemoSeed
import com.xiaopiao.app.ui.components.NavScrollNone
import androidx.compose.material.icons.Icons
import com.xiaopiao.app.ui.budget.BudgetPage
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.work.ReminderIntents
import com.xiaopiao.app.work.ReminderScheduler

private enum class InsightsRoute(val label: String) { BUDGET("预算"), AWARDS("每周小票"), SUBSCRIPTIONS("订阅侦探"), REGRET("后悔模拟") }

/**
 * 统计 tab. The landing page is [StatsScreen] (docs/APP-DESIGN.md §3.3); the older 周报 / 订阅 / 后悔
 * pages stay reachable from its 「更多」 section instead of owning the whole tab (§3.7).
 */
@Composable
fun InsightsScreen(
    container: AppContainer,
    open: OpenRequest? = null,
    onOpenConsumed: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onBack: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") embedded: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onPrint: (() -> Unit)? = null,
    onOpenLedger: (com.xiaopiao.app.ui.ledger.LedgerFilter) -> Unit = {},
    /** 记一笔 from the stats empty state (the shell owns the record pane). */
    onRecord: () -> Unit = {},
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ReminderScheduler.ensureScheduled(context) }

    val real by container.ledger.observeAll().collectAsState(emptyList())
    val demo = real.any { it.note == DemoSeed.NOTE }
    var route by rememberSaveable { mutableStateOf<InsightsRoute?>(null) }
    val now = remember { System.currentTimeMillis() }
    val input = InsightsInput(real, now, demo)

    LaunchedEffect(open) {
        val target = when (open?.target) {
            ReminderIntents.OPEN_WEEKLY -> InsightsRoute.AWARDS
            ReminderIntents.OPEN_SUBSCRIPTIONS -> InsightsRoute.SUBSCRIPTIONS
            ReminderIntents.OPEN_BUDGET -> InsightsRoute.BUDGET
            else -> return@LaunchedEffect
        }
        route = target
        onOpenConsumed()
    }

    // Sub-pages push in from the trailing edge like every other second-level page (tt 2026-11-02: 动画统一).
    com.xiaopiao.app.ui.motion.XpPushHost(
        page = route?.let { page -> { InsightsSubPage(page, input, container, onOpenSettings) { route = null } } },
        onBack = { route = null },
    ) {
        StatsScreen(
            container = container,
            onOpenAwards = { route = InsightsRoute.AWARDS },
            onOpenBudget = { route = InsightsRoute.BUDGET },
            onOpenSubscriptions = { route = InsightsRoute.SUBSCRIPTIONS },
            onOpenRegret = { route = InsightsRoute.REGRET },
            onPrint = onPrint,
            onOpenLedger = onOpenLedger,
            onRecord = onRecord,
        )
    }
}

@Composable
private fun InsightsSubPage(
    page: InsightsRoute,
    input: InsightsInput,
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val backLabel = "统计"
    // 预算 brings its own NavScaffold (it is also the drawer over 记账); wrapping it again stacked two bars.
    if (page == InsightsRoute.BUDGET) {
        BudgetPage(
            container,
            onBack = onBack,
            onOpenSettings = onOpenSettings,
            backLabel = backLabel,
        )
        return
    }
    NavScaffold(
        page.label,
        NavScrollNone,
        onBack = onBack,
        backLabel = backLabel,
    ) { top ->
        // One bar only: the sub-pages draw their own scrolling content, the reserved height travels into their
        // scroll padding so the page runs under the capsules.
        Box(Modifier.fillMaxSize()) {
            when (page) {
                InsightsRoute.AWARDS -> AwardsPage(input, topPad = top)
                InsightsRoute.SUBSCRIPTIONS -> SubscriptionPage(input, topPad = top)
                InsightsRoute.REGRET -> RegretPage(input, topPad = top)
                // Handled above: 预算 owns its own chrome.
                InsightsRoute.BUDGET -> Unit
            }
        }
    }
}
