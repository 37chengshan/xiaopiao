package com.xiaopiao.app.ui

import com.xiaopiao.app.ui.components.NavGlyph
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
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
import com.xiaopiao.app.ui.components.NavBarItem
import com.xiaopiao.app.ui.components.NavBarSpec
import com.xiaopiao.app.ui.components.XpNavBar
import com.xiaopiao.app.ui.games.GamesHub
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
import com.xiaopiao.app.ui.today.LocalReceiptHost
import com.xiaopiao.app.ui.today.ReceiptHostOverlay
import com.xiaopiao.app.ui.today.ReceiptPage
import com.xiaopiao.app.ui.today.rememberReceiptHost
import androidx.compose.runtime.CompositionLocalProvider
import com.xiaopiao.app.ui.components.LocalTopBarHost
import com.xiaopiao.app.ui.components.TopBarHost
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.draw.drawBehind
import com.xiaopiao.app.ui.theme.XpEase

/** The user edition's bar: 记账 · 小票 · ⊕ · 统计 | 圈子, and 账单 · ⊕ | AI · 圈子 · 游戏 once 圈子 is open. */
private val UserNavSpec = NavBarSpec(
    a = NavBarItem(NavTab.Ledger.route, NavTab.Ledger.label, NavGlyph.Ledger),
    b = NavBarItem(NavTab.Receipt.route, NavTab.Receipt.label, NavGlyph.Receipt),
    c = NavBarItem(NavTab.Insights.route, NavTab.Insights.label, NavGlyph.Chart),
    fold = NavBarItem("fold", "账单", NavGlyph.Ledger),
    entry = NavBarItem(NavTab.Circle.route, NavTab.Circle.label, NavGlyph.People),
    sa = NavBarItem(NavTab.Chat.route, NavTab.Chat.label, NavGlyph.Chat),
    sb = NavBarItem(NavTab.Games.route, NavTab.Games.label, NavGlyph.Gamepad),
    plusLabel = "记一笔", plusDescription = "记一笔 · 长按说一句",
)

@Composable
fun AppShell(container: AppContainer, open: OpenRequest? = null, onOpenConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    var merchantMode by rememberSaveable { mutableStateOf(false) }
    var merchantSettings by rememberSaveable { mutableStateOf(false) }
    val merchantState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val record = rememberRecordSheetState()
    val voiceHost = com.xiaopiao.app.ui.record.rememberVoiceHost()
    val context = androidx.compose.ui.platform.LocalContext.current
    val onboarding = remember { com.xiaopiao.app.ui.onboarding.OnboardingPrefs(context.applicationContext) }
    val onboarded by onboarding.observeDone().collectAsState(initial = null)
    val shellScope = rememberCoroutineScope()
    val navPrefs = remember(context) { NavPrefs(context.applicationContext.navStore) }
    val launchSide by navPrefs.observeLaunch().collectAsState(initial = null)
    // Fixed once per process (and restored with it): changing 打开 App 时进入 in 设置 must not re-route a running app.
    var startRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var sideMemo by rememberSaveable { mutableStateOf(NavSide.Ledger) }
    var lastLedger by rememberSaveable { mutableStateOf(NavTab.Ledger) }
    // The AI page raises this while its session drawer is open, so the bar steps out of the way.
    var navBarHidden by remember { mutableStateOf(false) }
    // 卡包 opens from the top-left of every ledger-side page, so it lives here, not inside 记账.
    var walletOpen by remember { mutableStateOf(false) }
    // 搜索 from 小票 / 统计 = jump to 记账, which enters search and clears this.
    var ledgerSearch by remember { mutableStateOf(false) }
    LaunchedEffect(launchSide) { if (startRoute == null) launchSide?.let { startRoute = NavModel.startRoute(it) } }
    if (onboarded == false) {
        com.xiaopiao.app.ui.onboarding.OnboardingHost { shellScope.launch { onboarding.setDone() } }
        return
    }

    if (merchantMode) {
        // 小店版 is a whole-product mode (palette, nav, landing), not a page (docs/APP-DESIGN.md §6).
        if (merchantSettings) {
            // The same 设置 page, reached from the gear on every 小店 page; back returns to where the shop was.
            BackHandler { merchantSettings = false }
            // Same page, but its edition row now leads back to the personal edition.
            SettingsScreen(container, inMerchant = true, onSwitchEdition = { merchantSettings = false; merchantMode = false }, onBack = { merchantSettings = false })
        } else {
            // SaveableStateProvider keeps the shop's tab / sub-page while the settings page replaces it.
            merchantState.SaveableStateProvider("merchant") {
                MerchantShell(container, onExit = { merchantMode = false }, onOpenSettings = { merchantSettings = true })
            }
        }
        return
    }

    val start = startRoute ?: run {
        Box(Modifier.fillMaxSize().background(XpColors.CanvasGrouped))
        return
    }
    val side = NavModel.sideOf(current) ?: NavModel.sideOf(start) ?: sideMemo
    androidx.compose.runtime.SideEffect {
        sideMemo = side
        lastLedger = NavModel.lastLedger(current, lastLedger)
    }

    fun go(tab: NavTab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    // The resident receipt: pages place a ReceiptStrip, the ceremony itself lives here, above the navigation.
    val receiptHost = rememberReceiptHost(container) { go(NavTab.Receipt) }
    val topBarHost = remember(nav) {
        TopBarHost(
            openSettings = { nav.navigate("settings") },
            openWallet = { walletOpen = true },
            openSearch = { ledgerSearch = true; go(NavTab.Ledger) },
        )
    }

    LaunchedEffect(open) {
        when (open?.target) {
            // The 21:30 reminder goes straight into the print ceremony.
            ReminderIntents.OPEN_TODAY -> { go(NavTab.Ledger); receiptHost.print(); onOpenConsumed() }
            // Insights consumes the request itself after jumping to the sub-page.
            ReminderIntents.OPEN_WEEKLY, ReminderIntents.OPEN_SUBSCRIPTIONS -> go(NavTab.Ledger)
        }
    }

    // System back from 游戏 returns to 圈子, not to the start of the stack.
    BackHandler(enabled = current == NavTab.Games.route) { go(NavTab.Circle) }

    // Liquid-glass source: the page is recorded here and the tab bar (a sibling, never inside it) samples it.
    val glass = rememberLayerBackdrop()
    // Ledger-side tabs pad their own lists past the bar, so they can run underneath it and show through the glass.
    // The rest (circle chat input, games…) still stop above it.
    val underBar = current == NavTab.Ledger.route || current == NavTab.Receipt.route || current == NavTab.Insights.route ||
        // The AI session drawer covers the whole screen, including where the (hidden) bar sat.
        (navBarHidden && current == NavTab.Chat.route)

    CompositionLocalProvider(LocalReceiptHost provides receiptHost, LocalTopBarHost provides topBarHost) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = XpColors.CanvasGrouped,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Standalone pages (settings) own the screen; no tabs.
            if (NavModel.showsTabBar(current)) XpNavBar(
                spec = UserNavSpec,
                social = side == NavSide.Social,
                selectedId = NavModel.selected(current)?.route,
                onSelect = { id -> NavTab.entries.firstOrNull { it.route == id }?.let(::go) },
                onFold = { go(NavModel.destination(NavSlot.Fold, lastLedger) ?: NavTab.Ledger) },
                onPlus = { record.openManual() },
                onPlusLongPress = { voiceHost.request() },
                hidden = navBarHidden && current == NavTab.Chat.route,
                backdrop = glass,
            )
        },
    ) { padding ->
        // Screens built on NavScaffold draw under the status bar themselves; Today still expects the inset applied here.
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val top = if (current == null) statusTop else 0.dp
        Box(
            Modifier.fillMaxSize()
                .layerBackdrop(glass)
                // What shows behind the bar on pages that stop above it: match the page, never a grey band (tt).
                .background(if (current == NavTab.Circle.route || current == NavTab.Games.route || current == NavTab.Chat.route) XpColors.Card else XpColors.CanvasGrouped)
                .padding(top = top, bottom = if (underBar) 0.dp else padding.calculateBottomPadding()),
        ) {
            // Tab switches are instant: it is the most frequent navigation in the app (docs/APP-DESIGN.md §7).
            NavHost(
                nav, startDestination = start,
                // Tabs are a frequent switch: keep it near-instant, only a 120ms crossfade so the
                // screen does not "pop". No slide — that would slow down a dozens-times-a-day action.
                enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)) },
                exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)) },
                popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)) },
                popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(90)) },
            ) {
                composable(NavTab.Ledger.route) {
                    LedgerScreen(
                        container,
                        searchRequested = ledgerSearch,
                        onSearchConsumed = { ledgerSearch = false },
                    )
                }
                composable(NavTab.Insights.route) {
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
                        onSwitchEdition = { merchantMode = true },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(NavTab.Receipt.route) {
                    ReceiptPage(container, receiptHost)
                }
                composable(NavTab.Chat.route) {
                    ChatScreen(
                        container, onRecordVoice = { record.openVoice() }, onRecordManual = { record.openManual() },
                        onOpenSettings = { nav.navigate("settings") },
                        // The session drawer covers the page; the tab bar steps aside while it is open.
                        onDrawerOpenChange = { navBarHidden = it },
                    )
                }
                composable(NavTab.Circle.route) { CircleHome(container, onOpenSettings = { nav.navigate("settings") }, onOpenGames = { go(NavTab.Games) }) }
                // 游戏 is its own tab of the social side, not a state inside 圈子: nothing saved can reopen it by itself.
                composable(NavTab.Games.route) { GamesHub(onBack = { go(NavTab.Circle) }, onOpenSettings = { nav.navigate("settings") }) }

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
    ReceiptHostOverlay(receiptHost)
    // 卡包是全屏面板：用 Dialog 铺满整屏（含底部导航），面板自带 FLAG_SECURE 与下滑关闭。
    if (walletOpen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { walletOpen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                // 卡号与会员码只在这块面板上，窗口级禁截屏（Dialog 有独立 window）。
                securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn,
            ),
        ) {
            com.xiaopiao.app.ui.wallet.WalletPanel(
                container = container,
                now = System.currentTimeMillis(),
                onDismiss = { walletOpen = false },
            )
        }
    }
    }
}
