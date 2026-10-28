package com.xiaopiao.app.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
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
import com.xiaopiao.app.ui.components.LargeTitle
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

    val receipt = remember(data, all) { data?.let { DailyReceiptBuilder.build(it.day, it.items, all.orEmpty()) } }
    val lines = remember(receipt, expanded, demo, selectedDay) {
        receipt?.let { ReceiptLayout.build(it, selectedDay == today, demo, expanded) }.orEmpty()
    }
    val reduced = rememberReducedMotion()
    val timeline = remember(lines, reduced) { PrintTimeline.build(lines, reduced) }

    val scope = rememberCoroutineScope()
    val haptics = rememberXpHaptics()
    val session = remember { PrintSession(scope, haptics) }
    val layer = rememberGraphicsLayer()
    val toast = rememberUndoToastState()
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val density = LocalDensity.current

    val latestTimeline by rememberUpdatedState(timeline)
    val latestLines by rememberUpdatedState(lines)
    fun startPrint() = session.start(latestTimeline, reduced)

    var pendingPrint by remember { mutableStateOf(false) }
    LaunchedEffect(pendingPrint, data) {
        if (pendingPrint && data != null) { pendingPrint = false; startPrint() }
    }

    fun selectDay(day: Long) {
        expanded = false
        selectedDay = day
        pendingPrint = true
        scope.launch { scroll.animateScrollTo(0) }
    }

    fun tearAndShare() {
        val plain = ReceiptLayout.toPlainText(latestLines)
        session.tear(
            flyPx = with(density) { 900.dp.toPx() },
            capture = { layer.toImageBitmap() },
            onTorn = { bmp ->
                scope.launch { ReceiptShare.share(context, bmp, plain, "$selectedDay") }
                toast.show("小票已撕下 ✂️ 记得分享给朋友", "再印一张") { startPrint() }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
            val todayItems = all.orEmpty().filter { it.occurredAt in today until today + DAY_MS }
            val greeting = TodayGreeting.build(
                hour, all.orEmpty().isNotEmpty(), todayItems.size,
                todayItems.filter { it.direction == Direction.OUT }.sumOf { it.amountCents },
            )
            LargeTitle(title = greeting.hello, subtitle = ReceiptLayout.dayLabel(today))
            AiBubble(if (all == null) "我在热机，稍等一下下…" else greeting.line, reduced)

            if (all == null || receipt == null) {
                StageSkeleton()
            } else {
                ReceiptStage(
                    session, lines, timeline, ReceiptLayout.stampText(receipt), layer, expanded,
                    onToggleMore = { expanded = !expanded },
                    onPrint = ::startPrint,
                    onTear = ::tearAndShare,
                    modifier = Modifier.padding(top = XpSpace.m),
                )
                StageControls(
                    phase = session.phase,
                    isToday = selectedDay == today,
                    demo = demo,
                    canOfferDemo = realAll.orEmpty().isEmpty(),
                    onPrint = ::startPrint,
                    onShare = ::tearAndShare,
                    onDemo = {
                        demo = true; expanded = false; session.reset()
                        toast.show("已切换到演示数据", "退出") { demo = false; session.reset() }
                    },
                    onExitDemo = { demo = false; session.reset() },
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

/** Loading state for the stage: shimmering printer body and paper. */
@Composable
private fun StageSkeleton() {
    Column(Modifier.fillMaxWidth().padding(top = XpSpace.m), horizontalAlignment = Alignment.CenterHorizontally) {
        ShimmerBlock(Modifier.width(PaperWidth + 44.dp).height(PrinterHeight), RoundedCornerShape(22.dp))
        ShimmerBlock(Modifier.width(PaperWidth).height(220.dp).padding(top = 0.dp), RoundedCornerShape(4.dp))
    }
}
