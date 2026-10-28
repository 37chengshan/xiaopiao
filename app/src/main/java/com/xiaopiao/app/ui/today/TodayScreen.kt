package com.xiaopiao.app.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.insights.DailyReceiptBuilder
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.xiaopiao.app.R
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.components.SectionHeader
import com.xiaopiao.app.ui.components.ShimmerBlock
import com.xiaopiao.app.ui.components.UndoToastHost
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberUndoToastState
import com.xiaopiao.app.ui.onboarding.OnboardingPrefs
import com.xiaopiao.app.ui.onboarding.OnboardingScreen
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Calendar

private data class DayData(val day: Long, val items: List<Txn>)

/** "今日" tab: AI-first greeting, the receipt printer, per-category chips and recent-day mini receipts. */
@Composable
fun TodayScreen(container: AppContainer) {
    val today = remember { startOfDay(System.currentTimeMillis()) }
    var selectedDay by rememberSaveable { mutableLongStateOf(today) }
    var demo by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val realAll by remember { container.ledger.observeAll() }.collectAsState(initial = null)
    val sample = remember(today) { SampleData.build(today) }
    val all: List<Txn>? = if (demo) sample else realAll

    var dayData by remember { mutableStateOf<DayData?>(null) }
    LaunchedEffect(selectedDay, demo) {
        val flow = if (demo) flowOf(sample.filter { it.occurredAt in selectedDay until selectedDay + DAY_MS }.sortedBy { it.occurredAt })
        else container.ledger.observeRange(selectedDay, selectedDay + DAY_MS)
        flow.collect { dayData = DayData(selectedDay, it) }
    }
    val data = dayData?.takeIf { it.day == selectedDay }

    val printedAt = remember(selectedDay, demo) { System.currentTimeMillis() }
    val receipt = remember(data, all) { data?.let { DailyReceiptBuilder.build(it.day, it.items, all.orEmpty()) } }
    val meta = remember(all, selectedDay, printedAt) {
        val prev = all.orEmpty().filter { it.direction == Direction.OUT && it.occurredAt in (selectedDay - DAY_MS) until selectedDay }
        ReceiptMeta(printedAt, if (prev.isEmpty()) null else prev.sumOf { it.amountCents })
    }
    val lines = remember(receipt, demo, selectedDay, meta) {
        receipt?.let { ReceiptLayout.build(it, selectedDay == today, demo, meta = meta) }.orEmpty()
    }
    val reduced = rememberReducedMotion()
    val timeline = remember(lines, reduced) { PrintTimeline.build(lines, reduced) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = remember { ReceiptHaptics(context) }
    val sound = remember { ReceiptSound(context) }
    DisposableEffect(sound) { onDispose { sound.release() } }
    val soundPrefs = remember { ReceiptSoundPrefs(context) }
    val soundOn by soundPrefs.observeEnabled().collectAsState(initial = true)
    val vibrationOn by soundPrefs.observeVibration().collectAsState(initial = true)
    SideEffect { sound.enabled = soundOn; haptics.enabled = vibrationOn }
    val session = remember { PrintSession(scope, haptics, sound) }
    val toast = rememberUndoToastState()
    var share by remember { mutableStateOf<ShareContent?>(null) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    val latestTimeline by rememberUpdatedState(timeline)
    fun startPrint() = session.open(latestTimeline, reduced)

    var pendingPrint by remember { mutableStateOf(false) }
    LaunchedEffect(pendingPrint, data) {
        if (pendingPrint && data != null) { pendingPrint = false; startPrint() }
    }

    fun selectDay(day: Long) {
        selectedDay = day
        pendingPrint = true
    }

    fun openShare() {
        val r = receipt ?: return
        val isToday = selectedDay == today
        share = ShareContent(
            full = ReceiptLayout.build(r, isToday, demo, expanded = true, meta = meta),
            brief = ReceiptLayout.build(r, isToday, demo, brief = true, meta = meta),
            stampText = ReceiptLayout.stampText(r),
            fileKey = ReceiptLayout.isoDay(selectedDay).substringBefore(' '),
        )
    }

    fun tearByButton(velocityPx: Float) =
        session.tear(velocityPx, with(density) { 900.dp.toPx() }, ::openShare, reduced)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().graphicsLayer { val k = 1f - 0.03f * session.present.value; scaleX = k; scaleY = k }) {
            Column(Modifier.weight(1f).verticalScroll(scroll)) {
                val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
                val todayItems = all.orEmpty().filter { it.occurredAt in today until today + DAY_MS }
                val greeting = TodayGreeting.build(
                    hour, all.orEmpty().isNotEmpty(), todayItems.size,
                    todayItems.filter { it.direction == Direction.OUT }.sumOf { it.amountCents },
                )
                val dayLabel = ReceiptLayout.dayLabel(selectedDay)
                TodayHeader(
                    caption = ReceiptLayout.dayLabel(today),
                    demo = demo,
                    spendLabel = if (selectedDay == today) stringResource(R.string.today_spend)
                    else stringResource(R.string.today_spend_day, dayLabel.substringBefore(' ')),
                    spendCents = receipt?.totalOutCents ?: 0L,
                    line = if (all == null) stringResource(R.string.today_warming_up) else greeting.line,
                    reduced = reduced,
                )
                if (all == null || receipt == null) {
                    ShimmerBlock(Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.m).fillMaxWidth().height(96.dp), RoundedCornerShape(XpRadius.m))
                } else {
                    DayBreakdown(stringResource(R.string.today_breakdown), receipt.items)
                    SectionHeader(stringResource(R.string.today_recent))
                    RecentDays(DaySummaries.recent(all.orEmpty(), today), selectedDay, today, ::selectDay)
                }
                Spacer(Modifier.height(XpSpace.l))
            }
            if (all != null && receipt != null) {
                PrintDock(
                    isToday = selectedDay == today, demo = demo, canOfferDemo = realAll.orEmpty().isEmpty(),
                    onPrint = ::startPrint,
                    onDemo = { demo = true; toast.show("已切换到演示数据", "退出") { demo = false } },
                    onExitDemo = { demo = false },
                )
            }
        }
        UndoToastHost(toast, Modifier.align(Alignment.BottomCenter))
        ReceiptOverlay(
            session, lines, timeline, receipt?.let { ReceiptLayout.stampText(it) }.orEmpty(),
            onTear = ::tearByButton, onTorn = ::openShare, onSkip = { session.skip(latestTimeline) }, onClose = session::close,
        )
    }
    share?.let { ReceiptShareSheet(it) { share = null } }
    val onboarding = remember { OnboardingPrefs(context) }
    val onboarded by onboarding.observeDone().collectAsState(initial = true)
    if (!onboarded) OnboardingScreen { scope.launch { onboarding.markDone() } }
}
