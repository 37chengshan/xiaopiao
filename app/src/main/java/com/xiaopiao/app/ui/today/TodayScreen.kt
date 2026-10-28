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

    fun tear(velocityPx: Float) {
        val r = receipt ?: return
        val isToday = selectedDay == today
        val content = ShareContent(
            full = ReceiptLayout.build(r, isToday, demo, expanded = true, meta = meta),
            brief = ReceiptLayout.build(r, isToday, demo, brief = true, meta = meta),
            stampText = ReceiptLayout.stampText(r),
            fileKey = ReceiptLayout.isoDay(selectedDay),
        )
        session.tear(velocityPx, with(density) { 900.dp.toPx() }, { share = content }, reduced)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer { val k = 1f - 0.03f * session.present.value; scaleX = k; scaleY = k }
                .verticalScroll(scroll),
        ) {
            val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
            val todayItems = all.orEmpty().filter { it.occurredAt in today until today + DAY_MS }
            val greeting = TodayGreeting.build(
                hour, all.orEmpty().isNotEmpty(), todayItems.size,
                todayItems.filter { it.direction == Direction.OUT }.sumOf { it.amountCents },
            )
            LargeTitle(title = greeting.hello, subtitle = ReceiptLayout.dayLabel(today))
            AiBubble(if (all == null) "我在热机，稍等一下下…" else greeting.line, reduced)

            if (all == null || receipt == null) {
                ShimmerBlock(Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.m).fillMaxWidth().height(132.dp), RoundedCornerShape(XpRadius.m))
            } else {
                PrintEntry(
                    isToday = selectedDay == today,
                    demo = demo,
                    canOfferDemo = realAll.orEmpty().isEmpty(),
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
            Spacer(Modifier.height(XpSpace.xxl))
        }
        UndoToastHost(toast, Modifier.align(Alignment.BottomCenter))
        ReceiptOverlay(
            session, lines, timeline, receipt?.let { ReceiptLayout.stampText(it) }.orEmpty(),
            onTear = ::tear, onSkip = { session.skip(latestTimeline) }, onClose = session::close,
        )
    }
    share?.let { ReceiptShareSheet(it) { share = null } }
}

/** Quiet entry point on Today: one calm card, one primary action. The print itself happens in [ReceiptOverlay]. */
@Composable
private fun PrintEntry(
    isToday: Boolean,
    demo: Boolean,
    canOfferDemo: Boolean,
    onPrint: () -> Unit,
    onDemo: () -> Unit,
    onExitDemo: () -> Unit,
) {
    XpCard(Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.m)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                Text(if (isToday) "今日小票" else "这天的小票", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                if (demo) Pill("演示数据")
            }
            Text("点一下，从屏幕顶部印出来。", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
            XpButton(if (isToday) "打印今日小票" else "打印这天的小票", onPrint, Modifier.fillMaxWidth(), leading = "🖨️")
            if (demo) XpButton("退出演示", onExitDemo, Modifier.fillMaxWidth(), style = XpButtonStyle.Plain)
            else if (canOfferDemo) XpButton("看看示例小票", onDemo, Modifier.fillMaxWidth(), style = XpButtonStyle.Secondary)
        }
    }
}

/** "AI speaks first": avatar + a chat bubble whose text types in (instantly when motion is reduced). */
@Composable
private fun AiBubble(text: String, reduced: Boolean) {
    var shown by remember(text) { mutableIntStateOf(if (reduced) text.length else 0) }
    LaunchedEffect(text) {
        for (i in shown..text.length) { shown = i; delay(26) }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.s), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(XpColors.AccentSoft), contentAlignment = Alignment.Center) { Text("🧾") }
        Box(
            Modifier.padding(start = XpSpace.s).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                .background(XpColors.Card).padding(horizontal = XpSpace.m + 2.dp, vertical = XpSpace.m - 2.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink.copy(alpha = 0f))
            Text(text.take(shown), style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
        }
    }
}
