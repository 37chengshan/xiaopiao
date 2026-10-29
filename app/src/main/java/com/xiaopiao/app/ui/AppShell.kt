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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.border
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
import com.xiaopiao.app.ui.record.RecordMethod
import com.xiaopiao.app.ui.record.RecordMethodPrefs
import com.xiaopiao.app.ui.record.RecordSheetHost
import com.xiaopiao.app.ui.record.recordMethodStore
import com.xiaopiao.app.ui.record.rememberRecordSheetState
import com.xiaopiao.app.ui.settings.SettingsScreen
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import com.xiaopiao.app.ui.today.LocalReceiptHost
import com.xiaopiao.app.ui.today.ReceiptHostOverlay
import com.xiaopiao.app.ui.today.ReceiptPage
import com.xiaopiao.app.ui.today.rememberReceiptHost
import androidx.compose.runtime.CompositionLocalProvider
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

private val NavTab.icon: ImageVector
    get() = when (this) {
        NavTab.Ledger -> Icons.Outlined.ViewList
        NavTab.Insights -> Icons.Outlined.Insights
        NavTab.Receipt -> Icons.Outlined.ReceiptLong
        NavTab.Chat -> Icons.Outlined.ChatBubbleOutline
        NavTab.Circle -> Icons.Outlined.Groups
    }

@Composable
fun AppShell(container: AppContainer, open: OpenRequest? = null, onOpenConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    var merchantMode by rememberSaveable { mutableStateOf(false) }
    var merchantSettings by rememberSaveable { mutableStateOf(false) }
    val record = rememberRecordSheetState()
    val voiceHost = com.xiaopiao.app.ui.record.rememberVoiceHost()
    val gamesRequest = remember { GamesRequest() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val onboarding = remember { com.xiaopiao.app.ui.onboarding.OnboardingPrefs(context.applicationContext) }
    val onboarded by onboarding.observeDone().collectAsState(initial = null)
    val shellScope = rememberCoroutineScope()
    val recordMethod by remember(context) { RecordMethodPrefs(context.recordMethodStore).observe() }
        .collectAsState(initial = RecordMethod.Manual)
    if (onboarded == false) {
        com.xiaopiao.app.ui.onboarding.OnboardingHost { shellScope.launch { onboarding.setDone() } }
        return
    }

    if (merchantMode) {
        // 小店版 is a whole-product mode (palette, nav, landing), not a page (docs/APP-DESIGN.md §6).
        if (merchantSettings) {
            // The same 设置 page, reached from the gear on every 小店 page; back returns to where the shop was.
            BackHandler { merchantSettings = false }
            SettingsScreen(container, onOpenMerchant = { merchantSettings = false }, onBack = { merchantSettings = false })
        } else {
            MerchantShell(container, onExit = { merchantMode = false }, onOpenSettings = { merchantSettings = true })
        }
        return
    }

    fun go(tab: NavTab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    // The resident receipt: pages place a ReceiptStrip, the ceremony itself lives here, above the navigation.
    val receiptHost = rememberReceiptHost(container) { go(NavTab.Receipt) }

    LaunchedEffect(open) {
        when (open?.target) {
            // The 21:30 reminder goes straight into the print ceremony.
            ReminderIntents.OPEN_TODAY -> { go(NavTab.Ledger); receiptHost.print(); onOpenConsumed() }
            // Insights consumes the request itself after jumping to the sub-page.
            ReminderIntents.OPEN_WEEKLY, ReminderIntents.OPEN_SUBSCRIPTIONS -> go(NavTab.Ledger)
        }
    }

    CompositionLocalProvider(LocalReceiptHost provides receiptHost) {
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = XpColors.CanvasGrouped,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Standalone pages (settings) own the screen; no tabs.
            if (NavModel.showsTabBar(current)) XpTabBar(
                current = current,
                onSelect = { go(it) },
                onRecord = { record.openDefault(recordMethod) },
                onVoice = { voiceHost.request() },
                onGames = { gamesRequest.request(); if (current != NavTab.Circle.route) go(NavTab.Circle) },
            )
        },
    ) { padding ->
        // Screens built on NavScaffold draw under the status bar themselves; Today still expects the inset applied here.
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val top = if (current == null) statusTop else 0.dp
        Box(
            Modifier.fillMaxSize()
                .background(XpColors.CanvasGrouped)
                .padding(top = top, bottom = padding.calculateBottomPadding()),
        ) {
            // Tab switches are instant: it is the most frequent navigation in the app (docs/APP-DESIGN.md §7).
            NavHost(
                nav, startDestination = NavTab.Ledger.route,
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
                        onOpenSettings = { nav.navigate("settings") },
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
                        onOpenMerchant = { merchantMode = true },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(NavTab.Receipt.route) {
                    ReceiptPage(container, receiptHost, onOpenSettings = { nav.navigate("settings") })
                }
                composable(NavTab.Chat.route) {
                    ChatScreen(container, onRecordVoice = { record.openVoice() }, onRecordManual = { record.openDefault(recordMethod) })
                }
                composable(NavTab.Circle.route) { CircleHome(container, onOpenSettings = { nav.navigate("settings") }, openGames = gamesRequest.pending, onGamesConsumed = { gamesRequest.consume() }) }

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
    }
}

private val SlotMax = 64.dp
private val BarHeight = 64.dp
private val CapsulePad = XpSpace.xs
private val NavGap = XpSpace.s
/** In circle mode ＋ grows into a solid ink circle of this size, centred in the folded capsule. */
private val BigPlus = 56.dp

/**
 * Two floating white capsules (same fill, same soft shadow). [circleMode] drives one spring progress `p`
 * (0 = default, 1 = circle open): the left capsule folds 记账 · 统计 · 小票 away and ＋ swells into a centred solid
 * circle, the right capsule opens 游戏 next to AI · 圈子 and drifts toward the middle. The spring is retargeted,
 * not restarted, so a second tap mid-way just reverses it (docs/APP-DESIGN.md §2, tt#29/33/34/69).
 */
@Composable
private fun XpTabBar(current: String?, onSelect: (NavTab) -> Unit, onRecord: () -> Unit, onVoice: () -> Unit, onGames: () -> Unit) {
    val circleMode = NavModel.isCircleMode(current)
    val reduced = com.xiaopiao.app.ui.components.rememberReducedMotion()
    val progress = remember { androidx.compose.animation.core.Animatable(if (circleMode) 1f else 0f) }
    LaunchedEffect(circleMode) {
        val target = if (circleMode) 1f else 0f
        if (reduced) progress.snapTo(target)
        else progress.animateTo(target, androidx.compose.animation.core.spring(0.85f, 320f))
    }
    val p = progress.value.coerceIn(0f, 1f)
    val dark = XpColors.isDark
    val capsuleColor = if (dark) XpColors.Surface2.copy(alpha = 0.96f) else XpColors.Card.copy(alpha = 0.96f)
    val shape = RoundedCornerShape(BarHeight / 2)
    // iOS-style float: a soft, low shadow (y≈6dp, blur≈20dp, 8–10% black). Dark mode swaps it for a 1dp highlight rim.
    val lift = if (dark) Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), shape)
    else Modifier.shadow(14.dp, shape, clip = false, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000))

    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = XpSpace.l).padding(bottom = XpSpace.s),
    ) {
        // Default state shows 4 + 2 slots; circle mode 1 + 3. Six slots plus both paddings must fit a narrow phone.
        val slot = minOf(SlotMax, (maxWidth - CapsulePad * 4 - NavGap) / 6)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Left capsule: 记账 · 统计 · ＋ · 小票 → ＋ alone, centred and enlarged in circle mode.
            Row(
                Modifier.height(BarHeight).then(lift).clip(shape).background(capsuleColor).padding(horizontal = CapsulePad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Folding(slot, 1f - p) { TabItem(NavTab.Ledger, current, Modifier.width(slot), onSelect) }
                Folding(slot, 1f - p) { TabItem(NavTab.Insights, current, Modifier.width(slot), onSelect) }
                PlusItem(Modifier.width(slot), big = p, onClick = onRecord, onLongPress = onVoice)
                Folding(slot, 1f - p) { TabItem(NavTab.Receipt, current, Modifier.width(slot), onSelect) }
            }
            Spacer(Modifier.weight(1f))
            // Right capsule: AI · 圈子, plus 游戏 sliding open in circle mode.
            Row(
                Modifier.height(BarHeight).then(lift).clip(shape).background(capsuleColor).padding(horizontal = CapsulePad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabItem(NavTab.Chat, current, Modifier.width(slot), onSelect)
                TabItem(NavTab.Circle, current, Modifier.width(slot), onSelect)
                Folding(slot, p) { CircleGameTab(Modifier.width(slot).fillMaxHeight(), onGames) }
            }
            Spacer(Modifier.weight(maxOf(0.001f, 0.55f * p)))
        }
    }
}

/** Shrinks to nothing as [fraction] goes 1 → 0 while the child keeps its size, sliding shut from the right edge. */
@Composable
private fun Folding(full: androidx.compose.ui.unit.Dp, fraction: Float, content: @Composable () -> Unit) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        Modifier.width(full * f).height(BarHeight).clipToBounds().graphicsLayer { alpha = f },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.requiredWidth(full).fillMaxHeight(), contentAlignment = Alignment.Center) { content() }
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

/**
 * 记一笔: same slot, icon size and caption as its neighbours, no fill (tt#34/36). Tap = default input,
 * long-press = voice. [big] 0 → 1 (circle mode) swells it into a centred solid ink circle with a 28dp glyph.
 */
@Composable
private fun PlusItem(modifier: Modifier, big: Float, onClick: () -> Unit, onLongPress: () -> Unit) {
    val haptics = rememberXpHaptics()
    var pressed by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.92f else 1f, androidx.compose.animation.core.spring(0.55f, 450f), label = "plusScale",
    )
    Box(
        modifier.fillMaxHeight().graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val long = awaitLongPressOrCancellation(down.id)
                    if (long == null) {
                        pressed = false
                        haptics.select()
                        onClick()
                    } else {
                        haptics.thud()
                        onLongPress()
                        waitForUpOrCancellation()
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(BigPlus).graphicsLayer { scaleX = big; scaleY = big; alpha = big }
                .clip(CircleShape).background(XpColors.Primary),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(
                Icons.Outlined.Add, contentDescription = "记一笔 · 长按说一句",
                tint = androidx.compose.ui.graphics.lerp(XpColors.Ink, XpColors.OnPrimary, big),
                modifier = Modifier.size(androidx.compose.ui.unit.lerp(24.dp, 28.dp, big)),
            )
            // The caption belongs to the small state; the big circle stands alone.
            Text(
                "记一笔", fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, color = XpColors.Ink,
                modifier = Modifier.graphicsLayer { alpha = 1f - big }.then(if (big > 0.5f) Modifier.height(0.dp) else Modifier),
            )
        }
    }
}

@Composable
private fun TabItem(tab: NavTab, current: String?, modifier: Modifier, onSelect: (NavTab) -> Unit) {
    val selected = current?.startsWith(tab.route) == true
    val color = if (selected) XpColors.Ink else XpColors.InkSecondary
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
            .background(XpColors.Fill.copy(alpha = pill))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
        Text(tab.label, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
    }
}
