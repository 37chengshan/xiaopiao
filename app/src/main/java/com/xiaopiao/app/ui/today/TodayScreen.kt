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
import com.xiaopiao.app.ui.components.LargeTitle
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
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import com.xiaopiao.app.data.DemoSeed
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.DemoChip
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.record.RecordSheetState

private data class DayData(val day: Long, val items: List<Txn>)

/** "今日" tab: AI-first greeting, the receipt printer, per-category chips and recent-day mini receipts. */
@Composable
fun TodayScreen(container: AppContainer, record: RecordSheetState) {
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
    val haptics = rememberXpHaptics()
    val context = LocalContext.current
    val sound = remember { ReceiptSound(context) }
    DisposableEffect(sound) { onDispose { sound.release() } }
    val soundPrefs = remember { ReceiptSoundPrefs(context) }
    val soundOn by soundPrefs.observeEnabled().collectAsState(initial = true)
    SideEffect { sound.enabled = soundOn }
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
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f)
                    .graphicsLayer { val k = 1f - 0.03f * session.present.value; scaleX = k; scaleY = k }
                    .verticalScroll(scroll),
            ) {
                val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
                val todayItems = all.orEmpty().filter { it.occurredAt in today until today + DAY_MS }
                val greeting = TodayGreeting.build(
                    hour, all.orEmpty().isNotEmpty(), todayItems.size,
                    todayItems.filter { it.direction == Direction.OUT }.sumOf { it.amountCents },
                )
                TodayHeader(
                    dateLabel = ReceiptLayout.dayLabel(today),
                    count = todayItems.size,
                    spentCents = todayItems.filter { it.direction == Direction.OUT }.sumOf { it.amountCents },
                    hasDemo = all.orEmpty().any { it.note == DemoSeed.NOTE },
                    greeting = if (all == null) "我在热机，稍等一下下…" else greeting.line,
                )

                if (all == null || receipt == null) {
                    ShimmerBlock(Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.m).fillMaxWidth().height(132.dp), RoundedCornerShape(XpRadius.m))
                } else {
                PrintCard(
                    isToday = selectedDay == today,
                    demo = demo,
                    canOfferDemo = realAll.orEmpty().isEmpty(),
                    count = receipt.items.size,
                    totalCents = receipt.totalOutCents,
                    onPrint = ::startPrint,
                    onDemo = {
                        demo = true
                        toast.show("已切换到演示数据", "退出") { demo = false }
                    },
                    onExitDemo = { demo = false },
                )
                DayBreakdown(
                    if (selectedDay == today) "今日明细" else "${ReceiptLayout.dayLabel(selectedDay).substringBefore(' ')}明细",
                    receipt.items,
                )
                SectionHeader("最近几天的小票")
                RecentDays(DaySummaries.recent(all.orEmpty(), today), selectedDay, today, ::selectDay)
                }
                Spacer(Modifier.height(XpSpace.xl))
            }
            RecordDock(record)
        }
        UndoToastHost(toast, Modifier.align(Alignment.BottomCenter))
        ReceiptOverlay(
            session, lines, timeline, receipt?.let { ReceiptLayout.stampText(it) }.orEmpty(),
            onTear = ::tearByButton, onTorn = ::openShare, onSkip = { session.skip(latestTimeline) }, onClose = session::close,
        )
    }
    share?.let { ReceiptShareSheet(it) { share = null } }
}

/** Compact header: no large title on a tab root (docs/APP-DESIGN.md §2). */
@Composable
private fun TodayHeader(dateLabel: String, count: Int, spentCents: Long, hasDemo: Boolean, greeting: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.l).padding(top = XpSpace.s, bottom = XpSpace.m)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(dateLabel, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, modifier = Modifier.weight(1f))
            if (hasDemo) DemoChip()
        }
        Spacer(Modifier.height(XpSpace.xs))
        Text(
            if (count == 0) "今天还没花钱" else "今天 ¥${spentCents.yuan()}",
            style = MaterialTheme.typography.headlineMedium,
            color = XpColors.Ink,
        )
        if (count == 0) {
            Spacer(Modifier.height(XpSpace.xs))
            Text(greeting, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, maxLines = 2)
        }
    }
}

/** The day's receipt as a card: one glance at the paper, one primary action. */
@Composable
private fun PrintCard(
    isToday: Boolean,
    demo: Boolean,
    canOfferDemo: Boolean,
    count: Int,
    totalCents: Long,
    onPrint: () -> Unit,
    onDemo: () -> Unit,
    onExitDemo: () -> Unit,
) {
    XpCard(Modifier.padding(horizontal = XpSpace.l).padding(bottom = XpSpace.m)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("今日小票", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (count == 0) "今天钱包休息" else "$count 笔 · ¥${totalCents.yuan()}",
                    style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary,
                )
            }
            if (demo) DemoChip()
        }
        Spacer(Modifier.height(XpSpace.m))
        XpButton(if (isToday) "打印今日小票" else "打印这天的小票", onPrint, Modifier.fillMaxWidth())
        if (demo) {
            Spacer(Modifier.height(XpSpace.xs))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                XpButton("退出演示", onExitDemo, style = XpButtonStyle.Plain)
            }
        } else if (canOfferDemo) {
            Spacer(Modifier.height(XpSpace.xs))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                XpButton("看看示例小票", onDemo, style = XpButtonStyle.Plain)
            }
        }
    }
}

/** Always-on record dock above the tab bar: tap = manual form, long-press = voice. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordDock(record: RecordSheetState) {
    val haptics = rememberXpHaptics()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = XpSpace.l).padding(bottom = XpSpace.s, top = XpSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
    ) {
        val source = remember { MutableInteractionSource() }
        Row(
            Modifier.weight(1f).height(48.dp)
                .clip(RoundedCornerShape(XpRadius.pill))
                .background(XpColors.Fill)
                .combinedClickable(
                    interactionSource = source,
                    indication = null,
                    onClick = { haptics.select(); record.openManual() },
                    onLongClick = { haptics.thud(); record.openVoice() },
                )
                .padding(horizontal = XpSpace.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Mic, contentDescription = "长按说一句", tint = XpColors.InkSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(XpSpace.s))
            Text("记一笔 · 长按说一句", style = MaterialTheme.typography.bodyLarge, color = XpColors.InkSecondary, maxLines = 1)
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(XpColors.Primary)
                .pressableClick { haptics.select(); record.openManual() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "手动记一笔", tint = XpColors.OnPrimary, modifier = Modifier.size(24.dp))
        }
    }
}
