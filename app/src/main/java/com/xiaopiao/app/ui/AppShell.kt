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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.today.TodayScreen
import com.xiaopiao.app.work.ReminderIntents
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Add
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.drawBehind
import com.xiaopiao.app.ui.theme.XpEase

/**
 * Five slots (docs/APP-DESIGN.md §2): 对话 · 账本 · [首页·记账+小票] · 圈子 · 我的.
 * The centre button is the product's core action: it opens the record dock's home and long-press starts voice.
 */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Ledger("ledger", "记账", Icons.Outlined.ViewList),
    Insights("insights", "统计", Icons.Outlined.Insights),
    Chat("chat", "AI", Icons.Outlined.ChatBubbleOutline),
    Circle("circle", "圈子", Icons.Outlined.Groups),
}

@Composable
fun AppShell(container: AppContainer, open: OpenRequest? = null, onOpenConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    var merchantMode by rememberSaveable { mutableStateOf(false) }
    val record = rememberRecordSheetState()
    val voiceHost = com.xiaopiao.app.ui.record.rememberVoiceHost()
    var printSignal by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val onboarding = remember { com.xiaopiao.app.ui.onboarding.OnboardingPrefs(context.applicationContext) }
    val onboarded by onboarding.observeDone().collectAsState(initial = null)
    val shellScope = rememberCoroutineScope()
    if (onboarded == false) {
        com.xiaopiao.app.ui.onboarding.OnboardingHost { shellScope.launch { onboarding.setDone() } }
        return
    }

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
            ReminderIntents.OPEN_TODAY -> { go(Tab.Ledger); onOpenConsumed() }
            // Insights consumes the request itself after jumping to the sub-page.
            ReminderIntents.OPEN_WEEKLY, ReminderIntents.OPEN_SUBSCRIPTIONS -> go(Tab.Ledger)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = XpColors.CanvasGrouped,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // The print page owns the screen; no tabs while the paper is coming out.
            if (current != "print" && current != "settings") XpTabBar(
                current = current,
                onSelect = { go(it) },
                onRecord = { record.openManual() },
                onVoice = { voiceHost.request() },
            )
        },
    ) { padding ->
        // Screens built on NavScaffold draw under the status bar themselves; Today still expects the inset applied here.
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val top = if (current == "print" || current == null) statusTop else 0.dp
        Box(
            Modifier.fillMaxSize()
                .background(XpColors.CanvasGrouped)
                .padding(top = top, bottom = padding.calculateBottomPadding()),
        ) {
            // Tab switches are instant: it is the most frequent navigation in the app (docs/APP-DESIGN.md §7).
            NavHost(
                nav, startDestination = Tab.Ledger.route,
                // Tabs are a frequent switch: keep it near-instant, only a 120ms crossfade so the
                // screen does not "pop". No slide — that would slow down a dozens-times-a-day action.
                enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)) },
                exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)) },
                popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)) },
                popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)) },
            ) {
                composable(Tab.Ledger.route) {
                    LedgerScreen(
                        container,
                        onPrint = { printSignal++; nav.navigate("print") },
                        onOpenSettings = { nav.navigate("settings") },
                    )
                }
                composable(Tab.Insights.route) {
                    InsightsScreen(
                        container,
                        open,
                        onOpenConsumed,
                        onOpenSettings = { nav.navigate("settings") },
                    )
                }
                composable(
                    "settings",
                    enterTransition = {
                        androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.spring(0.9f, 340f)) { it / 3 } +
                            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160))
                    },
                    exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)) },
                    popExitTransition = {
                        androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.spring(0.9f, 320f)) { it / 3 } +
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140))
                    },
                ) {
                    SettingsScreen(
                        container,
                        onOpenMerchant = { merchantMode = true },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("print") {
                    // One job: print today's receipt, then leave.
                    BackHandler { nav.popBackStack() }
                    TodayScreen(container, record, printSignal, printOnly = true, onDone = { nav.popBackStack() }, onVoiceStart = { voiceHost.request() })
                }
                composable(Tab.Chat.route) {
                    ChatScreen(container, onRecordVoice = { record.openVoice() }, onRecordManual = { record.openManual() })
                }
                composable(Tab.Circle.route) { CircleHome(container, onOpenSettings = { nav.navigate("settings") }) }

            }
        }
    }
        com.xiaopiao.app.ui.record.VoiceOverlay(
            host = voiceHost,
            onTranscript = { text -> record.openText(text) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp),
        )
    }
    RecordSheetHost(record, container)
}

/**
 * Translucent capsule with a raised round primary button in the middle. The primary button floats 14dp above
 * the bar; long-press starts voice recording (docs/APP-DESIGN.md §4.2).
 */
@Composable
private fun XpTabBar(current: String?, onSelect: (Tab) -> Unit, onRecord: () -> Unit, onVoice: () -> Unit) {
    val circleMode = current == Tab.Circle.route
    val capsule = RoundedCornerShape(32.dp)
    val dark = XpColors.isDark
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = XpSpace.l).padding(bottom = XpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left capsule: 记账 / 统计 / ＋ / AI — collapses to 记账 alone while the circle is open (tt#29).
        Row(
            Modifier.height(64.dp).clip(capsule)
                .then(if (dark) Modifier else Modifier.shadow(20.dp, capsule, ambientColor = Color(0x14000000), spotColor = Color(0x14000000)))
                .background(if (dark) XpColors.Surface2.copy(alpha = 0.94f) else XpColors.Card.copy(alpha = 0.94f))
                .padding(horizontal = XpSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(Tab.Ledger, current, Modifier.width(64.dp).fillMaxHeight(), onSelect)
            androidx.compose.animation.AnimatedVisibility(
                visible = !circleMode,
                enter = androidx.compose.animation.expandHorizontally(androidx.compose.animation.core.spring(0.85f, 320f)) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.shrinkHorizontally(androidx.compose.animation.core.spring(0.85f, 320f)) + androidx.compose.animation.fadeOut(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TabItem(Tab.Insights, current, Modifier.width(64.dp).fillMaxHeight(), onSelect)
                    CenterAction(onClick = onRecord, onLongPress = onVoice)
                    TabItem(Tab.Chat, current, Modifier.width(64.dp).fillMaxHeight(), onSelect)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // Right side: a single detached circle for 圈子, which expands into AI / 圈子 / 游戏.
        if (!circleMode) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(XpColors.Primary)
                    .pressableClick { onSelect(Tab.Circle) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Groups, contentDescription = "圈子", tint = XpColors.OnPrimary, modifier = Modifier.size(24.dp))
            }
        } else {
            Row(
                Modifier.height(64.dp).clip(capsule)
                    .background(if (dark) XpColors.Surface2.copy(alpha = 0.94f) else XpColors.Card.copy(alpha = 0.94f))
                    .padding(horizontal = XpSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabItem(Tab.Chat, current, Modifier.width(64.dp).fillMaxHeight(), onSelect)
                TabItem(Tab.Circle, current, Modifier.width(64.dp).fillMaxHeight(), onSelect)
                CircleGameTab(Modifier.width(64.dp).fillMaxHeight()) { onSelect(Tab.Circle) }
            }
        }
    }
}

/** 游戏 entry that lives inside the circle capsule; it opens the circle's game section. */
@Composable
private fun CircleGameTab(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.pressableClick(haptic = false, scaleDown = 0.94f) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.SportsEsports, contentDescription = "游戏", tint = XpColors.InkSecondary, modifier = Modifier.size(24.dp))
        Text("游戏", fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, color = XpColors.InkSecondary)
    }
}

/** The record action, sized like its neighbours because it now lives inside the capsule. */
@Composable
private fun CenterAction(onClick: () -> Unit, onLongPress: () -> Unit) {
    val haptics = rememberXpHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.9f else 1f, androidx.compose.animation.core.spring(0.55f, 400f), label = "actionScale",
    )
    Box(
        Modifier.padding(horizontal = XpSpace.xs).size(46.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape).background(XpColors.Primary)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val long = awaitLongPressOrCancellation(down.id)
                    if (long == null) {
                        haptics.select()
                        onClick()
                    } else {
                        haptics.thud()
                        onLongPress()
                        waitForUpOrCancellation()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = "记一笔 · 长按说一句", tint = XpColors.OnPrimary, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun TabItem(tab: Tab, current: String?, modifier: Modifier, onSelect: (Tab) -> Unit) {
    val selected = current?.startsWith(tab.route) == true
    val color = if (selected) XpColors.Accent else XpColors.InkSecondary
    val iconScale by androidx.compose.animation.core.animateFloatAsState(
        if (selected) 1.1f else 1f,
        androidx.compose.animation.core.spring(0.5f, 500f),
        label = "tabScale",
    )
    // Selected tab grows a soft pill behind it; unselected tabs stay flat so there is one focus.
    val pill by androidx.compose.animation.core.animateFloatAsState(
        if (selected) 1f else 0f,
        androidx.compose.animation.core.spring(0.7f, 380f),
        label = "tabPill",
    )
    Box(
        modifier.fillMaxHeight().pressableClick(haptic = false, scaleDown = 0.94f) { onSelect(tab) },
        contentAlignment = Alignment.Center,
    ) {
    androidx.compose.foundation.layout.Column(
        Modifier.graphicsLayer { alpha = 0.999f }
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(XpColors.Fill.copy(alpha = 0.7f * pill))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
        Text(tab.label, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
    }
}

/** Centre action: tap = manual form, long-press = voice. Not a destination. */
