package com.xiaopiao.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
        bottomBar = {
            NavigationBar(containerColor = XpColors.Card.copy(alpha = 0.94f)) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current?.startsWith(tab.route) == true,
                        onClick = { go(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = XpColors.Accent,
                            selectedTextColor = XpColors.Accent,
                            indicatorColor = XpColors.AccentSoft,
                            unselectedIconColor = XpColors.InkSecondary,
                            unselectedTextColor = XpColors.InkSecondary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(XpColors.Canvas).padding(padding)) {
            NavHost(nav, startDestination = Tab.Today.route) {
                composable(Tab.Today.route) { TodayScreen(container) }
                composable(Tab.Ledger.route) { LedgerScreen(container) }
                composable(Tab.Insights.route) { InsightsScreen(container, open, onOpenConsumed) }
                composable(Tab.Merchant.route) { MerchantScreen(container) }
                composable(Tab.Settings.route) { SettingsScreen(container) }
            }
        }
    }
}
