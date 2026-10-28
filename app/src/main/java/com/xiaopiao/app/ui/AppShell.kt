package com.xiaopiao.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.components.pressableScale
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
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
import com.xiaopiao.app.ui.game.CircleScreen
import com.xiaopiao.app.ui.insights.InsightsScreen
import com.xiaopiao.app.ui.ledger.LedgerScreen
import com.xiaopiao.app.ui.merchant.MerchantScreen
import com.xiaopiao.app.ui.settings.SettingsScreen
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.today.TodayScreen
import com.xiaopiao.app.work.ReminderIntents

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Ledger("ledger", "账本", Icons.Outlined.ViewList),
    Insights("insights", "洞察", Icons.Outlined.Insights),
    Today("today", "今日", Icons.Outlined.ReceiptLong),
    Circle("circle", "圈子", Icons.Outlined.Groups),
    Settings("settings", "我的", Icons.Outlined.Person),
}

@Composable
fun AppShell(container: AppContainer, open: OpenRequest? = null, onOpenConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    var merchantMode by rememberSaveable { mutableStateOf(false) }
    if (merchantMode) {
        // 小店版: the former B-side tab, entered from 我的 and left through its own back button.
        Box(Modifier.fillMaxSize().background(XpColors.Canvas).windowInsetsPadding(WindowInsets.navigationBars)) {
            MerchantScreen(container, onExit = { merchantMode = false })
        }
        return
    }

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
        containerColor = if (current == Tab.Today.route || current == null) XpColors.Canvas else XpColors.CanvasGrouped,
        contentWindowInsets = WindowInsets(0),
        bottomBar = { XpTabBar(current) { go(it) } },
    ) { padding ->
        // Screens built on NavScaffold draw under the status bar themselves; Today still expects the inset applied here.
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val top = if (current == Tab.Today.route || current == null) statusTop else 0.dp
        Box(Modifier.fillMaxSize().background(if (current == Tab.Today.route || current == null) XpColors.Canvas else XpColors.CanvasGrouped).padding(top = top, bottom = padding.calculateBottomPadding())) {
            // Tab switches are instant: it is the most frequent navigation in the app (docs/DESIGN.md §5).
            NavHost(
                nav, startDestination = Tab.Today.route,
                enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None },
            ) {
                composable(Tab.Today.route) { TodayScreen(container) }
                composable(Tab.Ledger.route) { LedgerScreen(container) }
                composable(Tab.Insights.route) { InsightsScreen(container, open, onOpenConsumed) }
                composable(Tab.Circle.route) { CircleScreen(container) }
                composable(Tab.Settings.route) { SettingsScreen(container, onOpenMerchant = { merchantMode = true }) }
            }
        }
    }
}

/**
 * Translucent bar with a raised round primary button in the middle (今日 / 记账对话) and the other tabs on
 * either side. The primary button floats 14dp above the bar; selected tabs turn clay.
 */
@Composable
private fun XpTabBar(current: String?, onSelect: (Tab) -> Unit) {
    val capsule = RoundedCornerShape(32.dp)
    val dark = XpColors.isDark
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        Box(
            Modifier.fillMaxWidth().padding(top = 16.dp).height(64.dp)
                // Glass-style floating material. Real backdrop blur needs a library; until then an opaque-ish surface.
                .then(if (dark) Modifier else Modifier.shadow(24.dp, capsule, ambientColor = Color(0x1A141413), spotColor = Color(0x1A141413)))
                .clip(capsule)
                .background(if (dark) XpColors.Surface2.copy(alpha = 0.94f) else XpColors.Card.copy(alpha = 0.94f)),
        )
        Row(Modifier.fillMaxWidth().padding(top = 16.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f)) {
                TabItem(Tab.Ledger, current, Modifier.weight(1f), onSelect)
                TabItem(Tab.Insights, current, Modifier.weight(1f), onSelect)
            }
            Spacer(Modifier.width(78.dp))
            Row(Modifier.weight(1f)) {
                TabItem(Tab.Circle, current, Modifier.weight(1f), onSelect)
                TabItem(Tab.Settings, current, Modifier.weight(1f), onSelect)
            }
        }
        PrimaryTab(Tab.Today, current?.startsWith(Tab.Today.route) == true, Modifier.align(Alignment.TopCenter)) { onSelect(Tab.Today) }
    }
}

@Composable
private fun TabItem(tab: Tab, current: String?, modifier: Modifier, onSelect: (Tab) -> Unit) {
    val selected = current?.startsWith(tab.route) == true
    val color = if (selected) XpColors.Accent else XpColors.InkSecondary
    Column(
        modifier.fillMaxHeight().pressableClick(haptic = false, scaleDown = 0.94f) { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp))
        Text(tab.label, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun PrimaryTab(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = rememberXpHaptics()
    Box(
        modifier.size(66.dp).pressableScale(source, 0.92f)
            .graphicsLayer { alpha = if (pressed) 0.85f else 1f }
            .background(XpColors.CanvasGrouped, CircleShape).padding(4.dp)
            .clip(CircleShape).background(if (selected) XpColors.Accent else XpColors.Ink)
            .clickable(source, null) { haptics.select(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = if (selected) XpColors.OnAccent else XpColors.Canvas, modifier = Modifier.size(28.dp))
    }
}
