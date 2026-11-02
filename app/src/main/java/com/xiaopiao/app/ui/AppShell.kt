package com.xiaopiao.app.ui

import com.xiaopiao.app.ui.components.NavGlyph
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.IntOffset
import com.xiaopiao.app.ui.motion.XpMotion
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.motion.XpTiming
import kotlin.math.abs
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.zIndex
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.xiaopiao.app.ui.tr
import com.xiaopiao.app.ui.motion.LocalPageEntrance
import com.xiaopiao.app.ui.motion.PageEntranceSeen

/** The user edition's bar: 记账 · 小票 · ⊕ · 统计 | 圈子, and 账单 · ⊕ | AI · 生活 · 圈子 once 圈子 is open. */
private val UserNavSpec get() = NavBarSpec(
    a = NavBarItem(NavTab.Ledger.route, NavTab.Ledger.label, NavGlyph.Ledger),
    b = NavBarItem(NavTab.Receipt.route, NavTab.Receipt.label, NavGlyph.Receipt),
    c = NavBarItem(NavTab.Insights.route, NavTab.Insights.label, NavGlyph.Chart),
    fold = NavBarItem("fold", tr("账单", "Ledger"), NavGlyph.Ledger),
    entry = NavBarItem(NavTab.Circle.route, NavTab.Circle.label, NavGlyph.People),
    sa = NavBarItem(NavTab.Chat.route, NavTab.Chat.label, NavGlyph.Chat),
    sb = if (NavModel.lifeTab) NavBarItem(NavTab.Life.route, NavTab.Life.label, NavGlyph.Leaf)
    else NavBarItem(NavTab.Games.route, NavTab.Games.label, NavGlyph.Gamepad),
    plusLabel = tr("记一笔", "Record"), plusDescription = tr("记一笔 · 长按说一句", "Record · Hold to speak"),
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
    val voiceHost = com.xiaopiao.app.ui.record.rememberVoiceHost(container)
    val voiceCancelPx = com.xiaopiao.app.ui.record.rememberVoiceCancelThresholdPx()
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
    var ledgerFilter by remember { mutableStateOf<com.xiaopiao.app.ui.ledger.LedgerFilter?>(null) }
    LaunchedEffect(launchSide) { if (startRoute == null) launchSide?.let { startRoute = NavModel.startRoute(it) } }
    val onboardingUser by container.account.user.collectAsState()
    var onboardingAccountOpen by rememberSaveable { mutableStateOf(false) }
    if (onboarded == false) {
        com.xiaopiao.app.ui.onboarding.OnboardingHost(onLogin = { onboardingAccountOpen = true }, signedIn = onboardingUser != null, onDone = { shellScope.launch { onboarding.setDone() } })
        if (onboardingAccountOpen) com.xiaopiao.app.ui.account.AccountSheet(container.account) { onboardingAccountOpen = false }
        return
    }

    if (merchantMode) {
        // 小店版 is a whole-product mode (palette, nav, landing), not a page (docs/APP-DESIGN.md §6).
        if (merchantSettings) {
            // The same 设置 page, reached from the gear on every 小店 page; back returns to where the shop was.
            BackHandler { merchantSettings = false }
            // Same page, but its edition row now leads back to the personal edition.
            SettingsScreen(container, inMerchant = true, onSwitchEdition = { merchantSettings = false; merchantMode = false }, onBack = { merchantSettings = false }, backLabel = tr("小店", "Shop"))
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

    // No saveState / restoreState: tab destinations are empty shells (the pages live in the keep-alive host), so
    // saving only ever captured a pushed 设置 and restored it on the next visit to that tab (tt 2026-11-02:
    // 在游戏/圈子点 AI、账单会跳到设置).
    fun go(tab: NavTab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id)
        launchSingleTop = true
    }

    // The resident receipt: pages place a ReceiptStrip, the ceremony itself lives here, above the navigation.
    val receiptHost = rememberReceiptHost(container) { go(NavTab.Receipt) }
    // 设置 is a push page, and its 返回 names the tab whose gear opened it (移动七条 §1).
    val settingsBack = remember { mutableStateOf(NavTab.Ledger.label) }
    fun openSettingsFrom(tab: NavTab?) {
        settingsBack.value = (tab ?: NavTab.Ledger).label
        nav.navigate("settings")
    }
    val currentTab by androidx.compose.runtime.rememberUpdatedState(NavTab.entries.firstOrNull { it.route == current })
    val topBarHost = remember(nav) {
        TopBarHost(
            openSettings = { openSettingsFrom(currentTab) },
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
    // Pages built on NavScaffold publish their own recording through LocalPageGlassSink — the bar then samples
    // that one instead, so the page is only recorded once per frame.
    val glass = rememberLayerBackdrop()
    // Ledger-side tabs pad their own lists past the bar, so they can run underneath it and show through the glass.
    // The rest (circle chat input, games…) still stop above it.
    val underBar = current == NavTab.Ledger.route || current == NavTab.Receipt.route || current == NavTab.Insights.route ||
        // AI / 圈子 float their input above the bar; their content, like 游戏's wall, scrolls under the glass.
        current == NavTab.Chat.route || current == NavTab.Circle.route || current == NavTab.Games.route ||
        current == NavTab.Life.route

    // Back dispatcher for hidden tab pages: nothing ever dispatches to it, so their BackHandlers stay inert.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val inertBack = remember(lifecycleOwner) {
        object : androidx.activity.OnBackPressedDispatcherOwner {
            override val onBackPressedDispatcher = androidx.activity.OnBackPressedDispatcher()
            override val lifecycle get() = lifecycleOwner.lifecycle
        }
    }
    // Which tab pages have been built at least once; they stay in the tree from then on.
    val visitedTabs = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<String, Unit>() }

    // ── Tab motion (tt 2026-11-02: 组内左右滑 + 切换动画统一) ──────────────────────────────────────────────
    // Every tab switch is one horizontal slide in bar order (记账 · 小票 · 统计 | AI · 生活 · 圈子 · 游戏); a swipe
    // drags the same two pages and hands its release position to that slide. Fades only under reduced motion.
    val reduced = rememberReducedMotion()
    val shown = current ?: start
    // The tab drawn underneath a pushed page (设置), so popping it slides back onto a page that is already there.
    val lastTab = remember { arrayOf(start) }
    val baseTab = if (NavModel.selected(shown) != null) shown else lastTab[0]
    var pageWidth by remember { mutableFloatStateOf(0f) }
    val settledTab = remember { arrayOf(baseTab) }
    val swipeStart = remember { arrayOf<Float?>(null) }
    val slide = remember(baseTab) {
        val from = NavTab.entries.firstOrNull { it.route == settledTab[0] }
        val to = NavTab.entries.firstOrNull { it.route == baseTab }
        val startPx = swipeStart[0].also { swipeStart[0] = null }
        if (reduced || from == null || to == null || from == to || pageWidth <= 0f) null
        else (if (to.ordinal > from.ordinal) 1 else -1).let { dir -> TabSlide(from.route, to.route, startPx ?: dir * pageWidth, dir) }
    }
    androidx.compose.runtime.SideEffect { settledTab[0] = baseTab; lastTab[0] = baseTab }
    // Build the side's other pages once the shell is idle, so the first swipe has something to drag in.
    LaunchedEffect(side) {
        kotlinx.coroutines.delay(1_500)
        (if (side == NavSide.Ledger) NavModel.LEDGER_SIDE else NavModel.SOCIAL_SIDE).forEach { visitedTabs[it.route] = Unit }
    }
    // The finger's page offset while a swipe is undecided; [dragFor] is the page it was started on.
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragFor by remember { mutableStateOf<String?>(null) }
    var dragPeek by remember { mutableStateOf<NavTab?>(null) }
    LaunchedEffect(slide) {
        val s = slide ?: return@LaunchedEffect
        s.progress.animateTo(1f, XpMotion.snappy(0.001f))
        s.done = true
        dragX = 0f; dragFor = null; dragPeek = null
    }
    val currentSlide by androidx.compose.runtime.rememberUpdatedState(slide)

    // Debug builds only: frame timing straight from Choreographer (gfxinfo is empty on the test phone).
    com.xiaopiao.app.ui.DebugFrameMonitor()

    CompositionLocalProvider(
        LocalReceiptHost provides receiptHost, LocalTopBarHost provides topBarHost,
        com.xiaopiao.app.ui.account.LocalAccount provides container.account,
    ) {
    Box(
        Modifier.fillMaxSize()
            // 语音结果条（没听清 / 出错）不用等满时间：手指落在任何地方就收起（tt 2026-11-02）。
            // 只在 Initial 阶段旁观，不消费，这一下点击照常落到下面的按钮上。
            .pointerInput(voiceHost) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    if (voiceHost.outcome != null) voiceHost.outcome = null
                }
            },
    ) {
    Scaffold(
        containerColor = XpColors.CanvasGrouped,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Standalone pages (settings) own the screen; no tabs.
            XpNavBar(
                spec = UserNavSpec,
                social = side == NavSide.Social,
                selectedId = NavModel.selected(current)?.route,
                onSelect = { id -> NavTab.entries.firstOrNull { it.route == id }?.let(::go) },
                onFold = { go(NavModel.destination(NavSlot.Fold, lastLedger) ?: NavTab.Ledger) },
                onPlus = { record.openManual() },
                onPlusLongPress = { voiceHost.request() },
                onPlusHold = { dy -> voiceHost.hold(dy, voiceCancelPx) },
                onPlusRelease = { voiceHost.release() },
                hidden = !NavModel.showsTabBar(current) || (navBarHidden && current == NavTab.Chat.route),
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
                .onSizeChanged { pageWidth = it.width.toFloat() }
                .pointerInput(shown, reduced) {
                    // Only a horizontal drag no child has taken (pager, wheel, row swipe…) switches pages, and only
                    // within the current side: inner gestures win, the shell gets what is left at their edges.
                    val here = NavModel.selected(shown) ?: return@pointerInput
                    if (NavTab.entries.none { it.route == shown }) return@pointerInput
                    val slop = viewConfiguration.touchSlop
                    val fling = 900.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (currentSlide?.done == false) return@awaitEachGesture
                        var tx = 0f
                        var ty = 0f
                        var raw = 0f
                        var dragging = false
                        val velocity = VelocityTracker()
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!dragging) {
                                if (change.isConsumed) return@awaitEachGesture
                                val d = change.positionChange()
                                tx += d.x; ty += d.y
                                if (abs(ty) > slop && abs(ty) * 1.5f >= abs(tx)) return@awaitEachGesture
                                if (abs(tx) <= slop || abs(tx) <= abs(ty) * 1.5f) continue
                                dragging = true
                                dragFor = shown
                                NavModel.neighbor(shown, 1)?.let { visitedTabs[it.route] = Unit }
                                NavModel.neighbor(shown, -1)?.let { visitedTabs[it.route] = Unit }
                            } else {
                                raw += change.positionChange().x
                            }
                            velocity.addPointerInputChange(change)
                            change.consume()
                            val next = NavModel.neighbor(shown, if (raw < 0f) 1 else -1)
                            dragPeek = next
                            // At either end of the side the page resists instead of leaving.
                            if (!reduced) dragX = if (next == null) raw * 0.3f else raw.coerceIn(-pageWidth, pageWidth)
                        }
                        if (!dragging) return@awaitEachGesture
                        val step = if (raw < 0f) 1 else -1
                        val next = NavModel.neighbor(shown, step)
                        val v = velocity.calculateVelocity().x
                        val commit = next != null && (abs(raw) > pageWidth / 3f || -v * step > fling)
                        if (commit && next != here) {
                            if (!reduced) swipeStart[0] = dragX + step * pageWidth else { dragX = 0f; dragFor = null; dragPeek = null }
                            go(next!!)
                        } else {
                            shellScope.launch {
                                animate(dragX, 0f, animationSpec = XpMotion.snappy()) { value, _ -> dragX = value }
                                dragFor = null; dragPeek = null
                            }
                        }
                    }
                }
                // What shows behind the bar on pages that stop above it: match the page, never a grey band (tt).
                // Light: the chat-like pages sit on card white. Dark: they must sit on the canvas, or every
                // bubble / receipt card on them is the same #1C1C1E and the page reads as one flat slab.
                .background(
                    if (current == NavTab.Circle.route || current == NavTab.Games.route || current == NavTab.Chat.route) {
                        if (XpColors.isDark) XpColors.Canvas else XpColors.Card
                    } else {
                        XpColors.CanvasGrouped
                    },
                )
                .padding(top = top, bottom = if (underBar || !NavModel.showsTabBar(current)) 0.dp else padding.calculateBottomPadding()),
        ) {
            CompositionLocalProvider(
                com.xiaopiao.app.ui.components.LocalTabBarInset provides
                    if (underBar && !navBarHidden) padding.calculateBottomPadding() else 0.dp,
            ) {
            // Tab pages stay composed once visited (tt 2026-10-31): switching used to tear the page down and build
            // its whole node tree again — the device trace showed 207-248ms of LayoutNode.attach in one frame. Now a
            // switch only changes which page is drawn, while the NavHost (whose tab destinations are empty shells)
            // still owns the back stack, so 返回, deep links and the pushed-page transitions behave as before.
            val tabBarInset = padding.calculateBottomPadding()
            NavTab.entries.forEach { tab ->
                val visible = shown == tab.route
                if (visible) visitedTabs[tab.route] = Unit
                if (visitedTabs.containsKey(tab.route)) {
                    val s = slide?.takeUnless { it.done }
                    val x: (() -> Float)? = when {
                        s != null && tab.route == s.to -> { { s.toX() } }
                        s != null && tab.route == s.from -> { { s.fromX(pageWidth) } }
                        dragFor == shown && tab.route == shown -> { { dragX } }
                        dragFor == shown && tab == dragPeek -> {
                            val side = if (tab.ordinal > (NavModel.selected(shown)?.ordinal ?: 0)) 1 else -1
                            ({ dragX + side * pageWidth })
                        }
                        else -> null
                    }
                    TabPageSlot(
                        visible = visible,
                        drawn = baseTab == tab.route,
                        x = x,
                        topInset = if (current == null) statusTop else 0.dp,
                        background = if (tab.route == NavTab.Circle.route || tab.route == NavTab.Games.route || tab.route == NavTab.Chat.route) {
                            if (XpColors.isDark) XpColors.Canvas else XpColors.Card
                        } else {
                            XpColors.CanvasGrouped
                        },
                    ) {
                        // A hidden page keeps its composition but must not keep its back handlers: 游戏's
                        // "back → 圈子" used to steal 返回 from 设置 while 游戏 sat hidden underneath.
                        val backOwner = if (visible) androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current ?: inertBack else inertBack
                        CompositionLocalProvider(
                            androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides backOwner,
                            LocalPageEntrance provides pageEntrance(tab.route),
                            com.xiaopiao.app.ui.components.LocalTabBarInset provides
                                if (!navBarHidden) tabBarInset else 0.dp,
                        ) {
                            when (tab) {
                                NavTab.Ledger -> LedgerScreen(
                                    container,
                                    searchRequested = ledgerSearch,
                                    onSearchConsumed = { ledgerSearch = false },
                                    filterRequest = ledgerFilter,
                                    onFilterConsumed = { ledgerFilter = null },
                                    onRecord = { record.openManual() },
                                    onEdit = { txn, onDelete -> record.openEdit(txn, onDelete) },
                                )
                                NavTab.Insights -> InsightsScreen(
                                    container,
                                    open,
                                    onOpenConsumed,
                                    onOpenSettings = { openSettingsFrom(NavTab.Insights) },
                                    onOpenLedger = { f -> ledgerFilter = f; go(NavTab.Ledger) },
                                    onRecord = { record.openManual() },
                                )
                                NavTab.Receipt -> ReceiptPage(container, receiptHost)
                                NavTab.Chat -> ChatScreen(
                                    container, onRecordVoice = { record.openVoice() }, onRecordManual = { record.openManual() },
                                    onOpenSettings = { openSettingsFrom(NavTab.Chat) },
                                    onDrawerOpenChange = { navBarHidden = it },
                                )
                                NavTab.Circle -> CircleHome(
                                    container,
                                    onOpenSettings = { openSettingsFrom(NavTab.Circle) },
                                    onOpenGames = { go(NavTab.Games) },
                                )
                                NavTab.Life -> com.xiaopiao.app.ui.life.LifeHome()
                                NavTab.Games -> GamesHub(
                                    onBack = { go(NavTab.Circle) },
                                    onOpenCircle = { go(NavTab.Circle) },
                                    onOpenSettings = { openSettingsFrom(NavTab.Games) },
                                )
                            }
                        }
                    }
                }
            }
            NavHost(
                nav, startDestination = start,
                modifier = Modifier.zIndex(2f),
                // Tab destinations are empty shells (the slide above is the tab motion); pushed pages slide in
                // from the trailing edge like every XpPushHost sub-page.
                enterTransition = { XpMotion.enterFade() },
                exitTransition = { XpMotion.exitFade() },
                popEnterTransition = { XpMotion.enterFade() },
                popExitTransition = { XpMotion.exitFade() },
            ) {
                // Tab destinations are shells: their pages live in the keep-alive host above. The graph still records
                // which tab is current, which is what the bar, the insets and 返回 read.
                NavTab.entries.forEach { tab -> page(tab.route) { } }
                page("settings", push = true) {
                    SettingsScreen(
                        container,
                        onSwitchEdition = { merchantMode = true },
                        onBack = { nav.popBackStack() },
                        backLabel = settingsBack.value,
                    )
                }

            }
            }
        }
    }
        com.xiaopiao.app.ui.record.VoiceOverlay(
            host = voiceHost,
            onRecord = { txn -> record.openManual(prefill = txn) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp - com.xiaopiao.app.ui.record.VoiceShadowRoom),
            backdrop = glass,
        )
    }
    RecordSheetHost(record, container)
    ReceiptHostOverlay(receiptHost)
    // 卡包是浮层卡片（不铺满，底部露出页面）：Dialog 只提供一块可以禁截屏（FLAG_SECURE）、
    // 能吃到跨窗口模糊的独立窗口，卡片本身由 WalletPanel 画在中间。
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

/** A tab switch in flight: [to] slides in from [startPx] while [from] stays one page width behind it. */
private class TabSlide(val from: String, val to: String, val startPx: Float, val dir: Int) {
    val progress = Animatable(0f)
    var done by mutableStateOf(false)
    fun toX(): Float = startPx * (1f - progress.value)
    fun fromX(width: Float): Float = toX() - dir * width
}

/**
 * One keep-alive tab page: it stays composed after its first visit. [visible] makes it the interactive page,
 * [drawn] keeps it on screen (also under a pushed 设置), and [x] — set while it takes part in a slide or a
 * swipe — places it horizontally.
 *
 * A page that is not interactive holds a consuming pointer handler, so a tap can never reach the page behind;
 * once fully faded it also moves off screen, out of hit testing, so it cannot swallow the shell's swipe.
 */
@Composable
private fun TabPageSlot(
    visible: Boolean,
    drawn: Boolean,
    x: (() -> Float)?,
    topInset: androidx.compose.ui.unit.Dp,
    background: Color,
    content: @Composable () -> Unit,
) {
    // Only reduced motion (or a jump with no slide) ever sees this fade; slides draw both pages at full alpha.
    val alpha by animateFloatAsState(
        targetValue = if (drawn) 1f else 0f,
        animationSpec = if (drawn) XpMotion.fade(160) else XpMotion.fadeAway(),
        label = "tab-alpha",
    )
    Box(
        Modifier.fillMaxSize()
            .zIndex(if (visible) 1f else if (x != null) 0.5f else 0f)
            .graphicsLayer {
                if (x != null) {
                    this.alpha = 1f
                    translationX = x()
                } else {
                    this.alpha = alpha
                    translationX = if (alpha == 0f) 100_000f else 0f
                }
            }
            .background(background)
            .padding(top = topInset)
            .then(
                if (visible) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    .changes.forEach { it.consume() }
                            }
                        }
                    }
                },
            ),
    ) { content() }
}

/**
 * Every destination goes through here. First visit in this process → the page plays its entrance; coming back to
 * it (tab switch, 返回) → it is shown already settled, so switching tabs never reads as "the page refreshed".
 */
private fun androidx.navigation.NavGraphBuilder.page(
    route: String,
    push: Boolean = false,
    content: @Composable () -> Unit,
) {
    composable(
        route = route,
        enterTransition = if (push) { { pushIn() } } else null,
        popExitTransition = if (push) { { pushOut() } } else null,
    ) {
        CompositionLocalProvider(LocalPageEntrance provides pageEntrance(route)) { content() }
    }
}

/** True exactly once per route per process (see [PageEntranceSeen]). */
@Composable
private fun pageEntrance(route: String): Boolean = remember(route) { PageEntranceSeen.routes.add(route) }

/** Same distance and timing as [com.xiaopiao.app.ui.motion.XpPushHost]: in from a quarter width at the trailing edge. */
private fun pushIn() =
    slideInHorizontally(XpMotion.snappy(IntOffset.VisibilityThreshold)) { (it * XpTiming.PushTravel).toInt() } +
        fadeIn(XpMotion.fade(XpTiming.PushIn))

private fun pushOut() =
    slideOutHorizontally(XpMotion.fadeAway(XpTiming.PopOut)) { (it * XpTiming.PushTravel).toInt() } +
        fadeOut(XpMotion.fadeAway(XpTiming.PopOut))
