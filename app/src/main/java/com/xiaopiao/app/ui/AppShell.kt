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
 * Navigation (docs/APP-DESIGN.md §2): left capsule 记账 · 统计 · ＋ · AI, a detached 圈子 circle on the right.
 * In circle mode the left capsule folds to 记账 ＋ and the right circle opens into AI · 圈子 · 游戏.
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
    var gamesRequest by remember { mutableIntStateOf(0) }
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
                onRecord = { record.openDefault(recordMethod) },
                onVoice = { voiceHost.request() },
                onGames = { gamesRequest++; if (current != Tab.Circle.route) go(Tab.Circle) },
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
                    ChatScreen(container, onRecordVoice = { record.openVoice() }, onRecordManual = { record.openDefault(recordMethod) })
                }
                composable(Tab.Circle.route) { CircleHome(container, onOpenSettings = { nav.navigate("settings") }, openGames = gamesRequest) }

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

private val SlotMax = 64.dp
private val BarHeight = 64.dp
private val CapsulePad = XpSpace.xs
private val NavGap = XpSpace.s

/**
 * Two floating capsules. [circleMode] drives one spring progress `p` (0 = default, 1 = circle open) that
 * folds the left capsule (统计 / AI slide shut, ＋ stays), grows the right circle into the AI · 圈子 · 游戏
 * capsule and pulls it toward the middle. The spring is retargeted, not restarted, so a second tap mid-way
 * just reverses it (docs/APP-DESIGN.md §2, tt#29/33/34).
 */
@Composable
private fun XpTabBar(current: String?, onSelect: (Tab) -> Unit, onRecord: () -> Unit, onVoice: () -> Unit, onGames: () -> Unit) {
    val circleMode = current == Tab.Circle.route
    val reduced = com.xiaopiao.app.ui.components.rememberReducedMotion()
    val progress = remember { androidx.compose.animation.core.Animatable(if (circleMode) 1f else 0f) }
    LaunchedEffect(circleMode) {
        val target = if (circleMode) 1f else 0f
        if (reduced) progress.snapTo(target)
        else progress.animateTo(target, androidx.compose.animation.core.spring(0.85f, 320f))
    }
    val p = progress.value.coerceIn(0f, 1f)
    val dark = XpColors.isDark
    val capsuleColor = if (dark) XpColors.Surface2.copy(alpha = 0.94f) else XpColors.Card.copy(alpha = 0.94f)
    val shape = RoundedCornerShape(BarHeight / 2)
    val lift = if (dark) Modifier else Modifier.shadow(20.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))

    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = XpSpace.l).padding(bottom = XpSpace.s),
    ) {
        // Five slots plus both capsules' padding must fit on narrow phones (circle mode shows 2 + 3 slots).
        val slot = minOf(SlotMax, (maxWidth - CapsulePad * 4 - NavGap) / 5)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Left capsule: 记账 · 统计 · ＋ · AI → 记账 ＋ (tt#29).
            Row(
                Modifier.height(BarHeight).clip(shape).then(lift).background(capsuleColor).padding(horizontal = CapsulePad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabItem(Tab.Ledger, current, Modifier.width(slot), onSelect)
                Folding(slot, 1f - p) { TabItem(Tab.Insights, current, Modifier.width(slot), onSelect) }
                PlusItem(Modifier.width(slot), fade = p, onClick = onRecord, onLongPress = onVoice)
                Folding(slot, 1f - p) { TabItem(Tab.Chat, current, Modifier.width(slot), onSelect) }
            }
            Spacer(Modifier.weight(1f))
            // Right: a lone circle that opens into AI · 圈子 · 游戏 and drifts toward the middle (tt#33/34).
            val openWidth = slot * 3 + CapsulePad * 2
            Box(
                Modifier.height(BarHeight).width(androidx.compose.ui.unit.lerp(slot, openWidth, p)).clip(shape).then(lift)
                    .background(androidx.compose.ui.graphics.lerp(XpColors.Primary, capsuleColor, p))
                    .then(if (circleMode) Modifier else Modifier.pressableClick { onSelect(Tab.Circle) }),
                contentAlignment = Alignment.Center,
            ) {
                if (p < 0.6f) Icon(
                    Icons.Outlined.Groups, contentDescription = "圈子", tint = XpColors.OnPrimary,
                    modifier = Modifier.size(24.dp).graphicsLayer { alpha = (1f - p / 0.6f).coerceIn(0f, 1f) },
                )
                if (p > 0.02f) Row(
                    Modifier.requiredWidth(openWidth).height(BarHeight).padding(horizontal = CapsulePad)
                        .graphicsLayer { alpha = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TabItem(Tab.Chat, current, Modifier.width(slot), onSelect)
                    TabItem(Tab.Circle, current, Modifier.width(slot), onSelect)
                    CircleGameTab(Modifier.width(slot).fillMaxHeight(), onGames)
                }
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
 * long-press = voice. [fade] 0 → 1 washes the glyph out as the circle opens (tt#33).
 */
@Composable
private fun PlusItem(modifier: Modifier, fade: Float, onClick: () -> Unit, onLongPress: () -> Unit) {
    val haptics = rememberXpHaptics()
    var pressed by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.9f else 1f, androidx.compose.animation.core.spring(0.55f, 400f), label = "plusScale",
    )
    Column(
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
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Add, contentDescription = "记一笔 · 长按说一句",
            tint = XpColors.Ink.copy(alpha = 1f - 0.45f * fade), modifier = Modifier.size(24.dp),
        )
        Text(
            "记一笔", fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium,
            color = androidx.compose.ui.graphics.lerp(XpColors.Ink, XpColors.InkSecondary, fade),
        )
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
