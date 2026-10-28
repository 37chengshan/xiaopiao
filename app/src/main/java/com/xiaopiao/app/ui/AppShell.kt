package com.xiaopiao.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.xiaopiao.app.ui.components.pressableClick
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.OpenRequest
import com.xiaopiao.app.ui.insights.InsightsScreen
import com.xiaopiao.app.ui.ledger.LedgerScreen
import com.xiaopiao.app.ui.merchant.MerchantScreen
import com.xiaopiao.app.ui.settings.SettingsScreen
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.today.TodayScreen
import com.xiaopiao.app.work.ReminderIntents

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "今日", Icons.Outlined.ReceiptLong),
    Ledger("ledger", "账本", Icons.Outlined.ViewList),
    Insights("insights", "洞察", Icons.Outlined.Insights),
    Merchant("merchant", "小店", Icons.Outlined.Storefront),
    Settings("settings", "我的", Icons.Outlined.Settings),
}

@Composable
fun AppShell(container: AppContainer, open: OpenRequest? = null, onOpenConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    fun go(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    LaunchedEffect(open) {
        when (open?.target) {
            ReminderIntents.OPEN_TODAY -> { go(Tab.Today); onOpenConsumed() }
            // Insights consumes the request itself after jumping to the sub-page.
            ReminderIntents.OPEN_WEEKLY, ReminderIntents.OPEN_SUBSCRIPTIONS -> go(Tab.Insights)
        }
    }

    Scaffold(
        containerColor = XpColors.Canvas,
        contentWindowInsets = WindowInsets(0),
        bottomBar = { XpTabBar(current) { go(it) } },
    ) { padding ->
        // Screens built on NavScaffold draw under the status bar themselves; Today still expects the inset applied here.
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val top = if (current == Tab.Today.route || current == null) statusTop else 0.dp
        Box(Modifier.fillMaxSize().background(XpColors.Canvas).padding(top = top, bottom = padding.calculateBottomPadding())) {
            // Tab switches are instant: it is the most frequent navigation in the app (docs/DESIGN.md §5).
            NavHost(
                nav, startDestination = Tab.Today.route,
                enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None },
            ) {
                composable(Tab.Today.route) { TodayScreen(container) }
                composable(Tab.Ledger.route) { LedgerScreen(container) }
                composable(Tab.Insights.route) { InsightsScreen(container, open, onOpenConsumed) }
                composable(Tab.Merchant.route) { MerchantScreen(container) }
                composable(Tab.Settings.route) { SettingsScreen(container) }
            }
        }
    }
}

/** Translucent material bar: hairline on top, clay icon+label when selected, ink-3 otherwise. No indicator pill. */
@Composable
private fun XpTabBar(current: String?, onSelect: (Tab) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .drawBehind {
                drawRect(XpColors.Material.copy(alpha = 0.94f))
                drawRect(XpColors.Hairline, Offset.Zero, Size(size.width, 0.5.dp.toPx()))
            }
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { tab ->
                val selected = current?.startsWith(tab.route) == true
                val color = if (selected) XpColors.Accent else XpColors.InkSecondary
                Column(
                    Modifier.weight(1f).fillMaxSize().pressableClick(haptic = false, scaleDown = 0.94f) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp))
                    Text(tab.label, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, color = color)
                }
            }
        }
    }
}
