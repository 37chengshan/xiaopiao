package com.xiaopiao.app.ui.insights

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.OpenRequest
import com.xiaopiao.app.data.DemoSeed
import com.xiaopiao.app.ui.components.NavScrollNone
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import com.xiaopiao.app.ui.budget.BudgetPage
import com.xiaopiao.app.ui.components.NavIconButton
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

    val page = route
    if (page == null) {
        StatsScreen(
            container = container,
            onOpenAwards = { route = InsightsRoute.AWARDS },
            onOpenBudget = { route = InsightsRoute.BUDGET },
            onOpenSubscriptions = { route = InsightsRoute.SUBSCRIPTIONS },
            onOpenRegret = { route = InsightsRoute.REGRET },
            onPrint = onPrint,
            onOpenSettings = onOpenSettings,
        )
        return
    }

    BackHandler { route = null }
    NavScaffold(
        page.label,
        NavScrollNone,
        onBack = { route = null },
        trailing = { NavIconButton(Icons.Outlined.Settings, "设置", onClick = onOpenSettings) },
    ) { top ->
        Column(Modifier.fillMaxSize().padding(top = top)) {
            // Keep the page viewport clear of the floating tab bar so charts and the last row are never cut.
            Box(Modifier.padding(bottom = 88.dp)) {
                when (page) {
                    InsightsRoute.BUDGET -> BudgetPage(container, onBack = { route = null }, onOpenSettings = onOpenSettings)
                    InsightsRoute.AWARDS -> AwardsPage(input)
                    InsightsRoute.SUBSCRIPTIONS -> SubscriptionPage(input)
                    InsightsRoute.REGRET -> RegretPage(input)
                }
            }
        }
    }
}
