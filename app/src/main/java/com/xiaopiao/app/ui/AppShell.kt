package com.xiaopiao.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.OpenRequest
import com.xiaopiao.app.ui.chat.ChatScreen
import com.xiaopiao.app.ui.circle.CircleHome
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.pressableScale
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.insights.InsightsScreen
import com.xiaopiao.app.ui.ledger.LedgerScreen
import com.xiaopiao.app.ui.merchant.MerchantShell
import com.xiaopiao.app.ui.record.RecordSheetHost
import com.xiaopiao.app.ui.record.rememberRecordSheetState
import com.xiaopiao.app.ui.settings.SettingsScreen
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.today.TodayScreen
import com.xiaopiao.app.work.ReminderIntents

/**
 * Five slots (docs/APP-DESIGN.md §2): 对话 · 账本 · [首页·记账+小票] · 圈子 · 我的.
 * The centre button is the product's core action: it opens the record dock's home and long-press starts voice.
 */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Chat("chat", "对话", Icons.Outlined.ChatBubbleOutline),
    Ledger("ledger", "账本", Icons.Outlined.ViewList),
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
    val record = rememberRecordSheetState()

    if (merchantMode) {
        // 小店版 is a whole-product mode (palette, nav, landing), not a page (docs/APP-DESIGN.md §6).
        MerchantShell(container, onExit = { merchantMode = false })
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
            ReminderIntents.OPEN_WEEKLY, ReminderIntents.OPEN_SUBSCRIPTIONS -> go(Tab.Ledger)
        }
    }

    Scaffold(
        containerColor = if (current == Tab.Today.route || current == null) XpColors.Canvas else XpColors.CanvasGrouped,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            XpTabBar(
                current = current,
                onSelect = { go(it) },
                onVoice = { record.openVoice() },
            )
        },
    ) { padding ->
        // Screens built on NavScaffold draw under the status bar themselves; Today still expects the inset applied here.
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val top = if (current == Tab.Today.route || current == null) statusTop else 0.dp
        Box(
            Modifier.fillMaxSize()
                .background(if (current == Tab.Today.route || current == null) XpColors.Canvas else XpColors.CanvasGrouped)
                .padding(top = top, bottom = padding.calculateBottomPadding()),
        ) {
            // Tab switches are instant: it is the most frequent navigation in the app (docs/APP-DESIGN.md §7).
            NavHost(
                nav, startDestination = Tab.Today.route,
                enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None },
            ) {
                composable(Tab.Chat.route) {
                    ChatScreen(container, onRecordVoice = { record.openVoice() }, onRecordManual = { record.openManual() })
                }
                composable(Tab.Ledger.route) { LedgerScreen(container) }
                composable(Tab.Today.route) { TodayScreen(container, record) }
                composable(Tab.Circle.route) { CircleHome(container) }
                composable(Tab.Settings.route) { SettingsScreen(container, onOpenMerchant = { merchantMode = true }) }
            }
        }
    }
    RecordSheetHost(record, container)
}

/**
 * Translucent capsule with a raised round primary button in the middle. The primary button floats 14dp above
 * the bar; long-press starts voice recording (docs/APP-DESIGN.md §4.2).
 */
@Composable
private fun XpTabBar(current: String?, onSelect: (Tab) -> Unit, onVoice: () -> Unit) {
    val capsule = RoundedCornerShape(32.dp)
    val dark = XpColors.isDark
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        Box(
            Modifier.fillMaxWidth().padding(top = 16.dp).height(64.dp)
                // Glass-style floating material. Real backdrop blur needs a library; until then an opaque-ish surface.
                .then(if (dark) Modifier else Modifier.shadow(24.dp, capsule, ambientColor = Color(0x1A000000), spotColor = Color(0x1A000000)))
                .clip(capsule)
                .background(if (dark) XpColors.Surface2.copy(alpha = 0.94f) else XpColors.Card.copy(alpha = 0.94f)),
        )
        Row(Modifier.fillMaxWidth().padding(top = 16.dp).height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f)) {
                TabItem(Tab.Chat, current, Modifier.weight(1f), onSelect)
                TabItem(Tab.Ledger, current, Modifier.weight(1f), onSelect)
            }
            Spacer(Modifier.width(78.dp))
            Row(Modifier.weight(1f)) {
                TabItem(Tab.Circle, current, Modifier.weight(1f), onSelect)
                TabItem(Tab.Settings, current, Modifier.weight(1f), onSelect)
            }
        }
        PrimaryTab(
            Tab.Today,
            selected = current?.startsWith(Tab.Today.route) == true,
            Modifier.align(Alignment.TopCenter),
            onClick = { onSelect(Tab.Today) },
            onLongClick = onVoice,
        )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrimaryTab(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptics = rememberXpHaptics()
    Box(
        modifier.size(66.dp).pressableScale(source, 0.92f)
            .graphicsLayer { alpha = if (pressed) 0.85f else 1f }
            .background(XpColors.CanvasGrouped, CircleShape).padding(4.dp)
            .clip(CircleShape).background(XpColors.Primary)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = { haptics.select(); onClick() },
                onLongClick = { haptics.thud(); onLongClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(tab.icon, contentDescription = if (selected) "今日" else "今日 · 长按说一句", tint = XpColors.OnPrimary, modifier = Modifier.size(28.dp))
    }
}
