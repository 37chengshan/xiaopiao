package com.xiaopiao.app.ui.insights

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.insights.StatsAggregator
import com.xiaopiao.app.insights.StatsChart
import com.xiaopiao.app.insights.StatsBucket
import com.xiaopiao.app.insights.StatsEntry
import com.xiaopiao.app.insights.StatsGrouping
import com.xiaopiao.app.insights.StatsNarrative
import com.xiaopiao.app.insights.StatsRange
import com.xiaopiao.app.insights.StatsSnapshot
import com.xiaopiao.app.insights.startOfWeek
import com.xiaopiao.app.ui.components.BarTrend
import com.xiaopiao.app.ui.components.EmptyState
import com.xiaopiao.app.ui.components.DonutChart
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.ledger.LedgerFilter
import com.xiaopiao.app.ui.ledger.LedgerRange
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.LedgerSideLeading
import com.xiaopiao.app.ui.components.AppTopActions
import com.xiaopiao.app.ui.components.LineTrend
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.icon
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpFonts
import com.xiaopiao.app.ui.theme.XpMotion
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val ContentShape = RoundedCornerShape(XpRadius.contentCard)

// One quiet scale for the whole page (tt: 字太大 / AI 味 → native ledger sizes).
// KPI 24sp, section titles 14sp, body 13sp, hints + axes 11sp; money is always tabular.

/** KPI value: 24sp/600 tabular. */
private val KpiValue = TextStyle(
    fontFamily = XpFonts.sans, fontSize = 24.sp, lineHeight = 28.sp,
    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, fontFeatureSettings = "tnum",
)

/** Card heading: 14sp/600, one step above body — not a poster title. */
private val SectionTitle = TextStyle(
    fontFamily = XpFonts.sans, fontSize = 14.sp, lineHeight = 18.sp,
    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp,
)

/** Segment / period title: 14sp, weight flips on selection. */
private val SegmentLabel = TextStyle(
    fontFamily = XpFonts.sans, fontSize = 14.sp, lineHeight = 18.sp,
    letterSpacing = (-0.1).sp,
)

/** A row's money on the right: 14sp/600 tabular. */
private val RowMoney = TextStyle(
    fontFamily = XpFonts.sans, fontSize = 14.sp, lineHeight = 18.sp,
    fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum",
)

/** Row title: 13sp/500. */
private val RowName = TextStyle(
    fontFamily = XpFonts.sans, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium,
)

/** Small print: labels, counts, merchants, chart hints — 11sp. */
private val Hint = TextStyle(fontFamily = XpFonts.sans, fontSize = 11.sp, lineHeight = 15.sp)

/** KPI caption: one step up from [Hint] so the 24sp value keeps a readable label. */
private val KpiLabel = TextStyle(fontFamily = XpFonts.sans, fontSize = 12.sp, lineHeight = 16.sp)

/** Narrative / hint copy: 13sp with room to breathe. */
private val Body13 = TextStyle(
    fontFamily = XpFonts.sans, fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp,
)

/** The swipe cycle: 自定义 is deliberately absent — it is only entered from its own button. */
private val SwipeOrder = listOf(StatsRange.Week, StatsRange.Month, StatsRange.Year)

private fun neighborOf(range: StatsRange, forward: Boolean): StatsRange? {
    val i = SwipeOrder.indexOf(range)
    if (i < 0) return null
    return SwipeOrder.getOrNull(if (forward) i + 1 else i - 1)
}

/** White card with a 0.5dp hairline edge: separation from the canvas comes from the line, never a shadow. */
@Composable
private fun Modifier.cardSurface(shape: Shape = ContentShape): Modifier =
    clip(shape).background(XpColors.Card).border(0.5.dp, XpColors.Hairline, shape)

/**
 * 统计 tab landing page (docs/APP-DESIGN.md §3.3): 周/月/年/自定义 → AI 小结 → KPI → 趋势 → 构成 → 排行,
 * with the old 周报/订阅/后悔 pages as 「更多」. Swiping left/right moves between 周/月/年.
 */
@Composable
fun StatsScreen(
    container: AppContainer,
    onOpenAwards: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenRegret: () -> Unit,
    onPrint: (() -> Unit)? = null,
    /** Chart taps land in 账单, filtered to the tapped category and this period (tt: app 所有东西都需要联动). */
    onOpenLedger: (LedgerFilter) -> Unit = {},
) {
    val txns by container.ledger.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Keeps "本周/本月" honest without recomputing on every frame.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    var range by rememberSaveable { mutableStateOf(StatsRange.Week) }
    // 「更多」 is the fifth tab: 预算 / 每周小票 / 订阅侦探 / 后悔模拟 (tt 2026-10-29).
    var more by rememberSaveable { mutableStateOf(false) }
    var anchor by rememberSaveable { mutableLongStateOf(now) }
    var direction by rememberSaveable { mutableStateOf(Direction.OUT) }
    var grouping by rememberSaveable { mutableStateOf(StatsGrouping.Primary) }
    var customFrom by rememberSaveable { mutableLongStateOf(StatsAggregator.startOfMonth(now)) }
    var customTo by rememberSaveable { mutableLongStateOf(startOfDay(now)) }

    var weekSheet by remember { mutableStateOf(false) }
    var rangeSheet by remember { mutableStateOf(false) }

    val reduceMotion = rememberReducedMotion()
    val snapshot = remember(txns, range, anchor, direction, grouping, customFrom, customTo, now) {
        StatsAggregator.build(
            history = txns, now = now, range = range, anchor = anchor,
            direction = direction, grouping = grouping,
            customFrom = customFrom, customTo = customTo,
        )
    }
    val narrative = remember(snapshot) { StatsNarrative.line(snapshot) }

    val scope = rememberCoroutineScope()
    // The page follows the finger (dragPx) and settles with a spring (slide); the two add up.
    val slide = remember { Animatable(0f) }
    var dragPx by remember { mutableFloatStateOf(0f) }

    NavScaffold(
        "统计",
        NavScrollNone,
        leading = { LedgerSideLeading() },
        trailing = { AppTopActions() },
    ) { top ->
        // The top inset is scroll content, not a frame: cards slide up under the glass capsules.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val pageWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val density = LocalDensity.current
            val commitPx = with(density) { maxOf(44.dp.toPx(), pageWidth * 0.14f) }
            // 自定义 has no neighbours in the swipe cycle; swiping is off while it is showing.
            val swipeable = range != StatsRange.Custom && !more

            Box(
                Modifier.fillMaxSize().clipToBounds()
                    .pointerInput(swipeable, reduceMotion, pageWidth, range) {
                        if (!swipeable) return@pointerInput
                        var axis = 0            // 0 = undecided, 1 = horizontal (paging), -1 = vertical (let the list scroll)
                        var totalX = 0f
                        var totalY = 0f
                        // Finger travel after damping: the commit threshold must work even without the visual slide.
                        var dragged = 0f
                        val slop = viewConfiguration.touchSlop

                        detectHorizontalDragGestures(
                            onDragStart = {
                                axis = 0
                                totalX = 0f
                                totalY = 0f
                                dragged = 0f
                                dragPx = 0f
                                scope.launch { slide.stop() }
                            },
                            onDragCancel = {
                                if (reduceMotion) {
                                    dragPx = 0f
                                    scope.launch { slide.snapTo(0f) }
                                } else {
                                    val travelled = slide.value + dragPx
                                    if (travelled == 0f) {
                                        dragPx = 0f
                                    } else {
                                        scope.launch {
                                            slide.snapTo(travelled)
                                            dragPx = 0f
                                            slide.animateTo(0f, XpMotion.gentle())
                                        }
                                    }
                                }
                            },
                            onDragEnd = {
                                val travelled = if (reduceMotion) dragged else slide.value + dragPx
                                val next = if (axis == 1 && abs(travelled) >= commitPx) {
                                    neighborOf(range, forward = travelled < 0f)
                                } else null

                                if (reduceMotion) {
                                    dragPx = 0f
                                    scope.launch { slide.snapTo(0f) }
                                    if (next != null) range = next
                                } else {
                                    // dragPx keeps painting the release position until the Animatable owns it.
                                    scope.launch {
                                        slide.snapTo(travelled)
                                        dragPx = 0f
                                        if (next == null) {
                                            slide.animateTo(0f, XpMotion.gentle())
                                        } else {
                                            // Old page leaves in the drag direction, new page enters from the far side.
                                            val forward = travelled < 0f
                                            val exit = if (forward) minOf(travelled, -pageWidth * 0.18f)
                                            else maxOf(travelled, pageWidth * 0.18f)
                                            slide.animateTo(exit, tween(90, easing = XpEase.out))
                                            range = next
                                            slide.snapTo(if (forward) pageWidth * 0.10f else -pageWidth * 0.10f)
                                            slide.animateTo(0f, XpMotion.gentle())
                                        }
                                    }
                                }
                            },
                            onHorizontalDrag = { change, amount ->
                                if (axis == 0) {
                                    totalX += amount
                                    totalY += change.position.y - change.previousPosition.y
                                    axis = when {
                                        // Clearly vertical: never translate, so a diagonal scroll scrolls.
                                        abs(totalY) > abs(totalX) + slop -> -1
                                        abs(totalX) >= slop -> 1
                                        else -> 0
                                    }
                                }
                                if (axis != 1) return@detectHorizontalDragGestures
                                // At 周报's left edge / 年报's right edge the page resists instead of leaving.
                                val canGo = neighborOf(range, forward = amount < 0f) != null
                                val step = if (canGo) amount else amount * 0.3f
                                dragged = (dragged + step).coerceIn(-pageWidth * 0.5f, pageWidth * 0.5f)
                                if (!reduceMotion) dragPx = dragged
                            },
                        )
                    },
            ) {
                Column(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            val dx = slide.value + dragPx
                            translationX = dx
                            alpha = 1f - (abs(dx) / pageWidth * 0.6f).coerceIn(0f, 0.25f)
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = XpSpace.page)
                        .padding(top = top + XpSpace.xs, bottom = 128.dp),
                    verticalArrangement = Arrangement.spacedBy(XpSpace.m),
                ) {
                    RangeSegments(range, more, onSelect = { range = it; more = false }, onMore = { more = true })
                    if (more) {
                        MoreSection(onOpenBudget, onOpenAwards, onOpenSubscriptions, onOpenRegret)
                        return@Column
                    }
                    val openEntry: (StatsEntry) -> Unit = { entry ->
                        onOpenLedger(
                            LedgerFilter(
                                direction = snapshot.direction, range = LedgerRange.Custom, categories = setOf(entry.category),
                                customFrom = snapshot.from, customTo = snapshot.to - DAY,
                            ),
                        )
                    }
                    PeriodRow(
                        snapshot = snapshot,
                        range = range,
                        onShift = { step ->
                            anchor = StatsAggregator.shiftAnchor(range, anchor, step)
                        },
                        onOpenPicker = { if (range == StatsRange.Week) weekSheet = true else rangeSheet = true },
                        direction = direction,
                        onDirection = { direction = it },
                    )
                    if (snapshot.entries.isEmpty()) {
                        // Empty is normal here, so say what the page will become instead of showing blank cards.
                        EmptyState(
                            icon = Icons.Outlined.ReceiptLong,
                            title = "这一段还没有账单",
                            body = "记下第一笔后，这里会长出趋势、构成和排行。",
                            modifier = Modifier.cardSurface().padding(vertical = XpSpace.m),
                        )
                    } else {
                        if (narrative != null) {
                            AiSummaryCard(
                                title = snapshot.aiTitle,
                                line = narrative,
                                onOpen = onOpenAwards,
                                modifier = Modifier.enterOnChange(snapshot.hashCode(), 0, reduceMotion),
                            )
                        }
                        KpiCard(snapshot, Modifier.enterOnChange(snapshot.hashCode(), 1, reduceMotion))
                        TrendCard(
                            snapshot, Modifier.enterOnChange(snapshot.hashCode(), 2, reduceMotion),
                            onPickBucket = { b -> anchor = b.start },
                        )
                        CompositionCard(
                            snapshot = snapshot,
                            grouping = grouping,
                            onGrouping = { grouping = it },
                            onOpen = openEntry,
                            modifier = Modifier.enterOnChange("${snapshot.hashCode()}-$grouping", 3, reduceMotion),
                        )
                        RankingCard(snapshot, Modifier.enterOnChange("${snapshot.hashCode()}-$grouping", 4, reduceMotion), onOpen = openEntry)
                    }
                }
            }
        }
    }

    if (weekSheet) {
        WeekPickerSheet(
            anchorWeekStart = startOfWeek(anchor),
            currentWeekStart = startOfWeek(now),
            onPick = { start ->
                anchor = start
                range = StatsRange.Week
                weekSheet = false
            },
            onDismiss = { weekSheet = false },
        )
    }
    if (rangeSheet) {
        RangePickerSheet(
            from = customFrom,
            to = customTo,
            onApply = { from, to ->
                customFrom = from
                customTo = to
                anchor = from
                range = StatsRange.Custom
                rangeSheet = false
            },
            onDismiss = { rangeSheet = false },
        )
    }
}

// ── segments ──────────────────────────────────────────────────────────────────────────────────

/** 周报 / 月报 / 年报 / 自定义 / 更多: 14sp labels, 2dp accent underline as wide as the selected text. */
@Composable
private fun RangeSegments(selected: StatsRange, more: Boolean, onSelect: (StatsRange) -> Unit, onMore: () -> Unit) {
    val density = LocalDensity.current
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp)) {
        (StatsRange.entries.map { it.label to it } + ("更多" to null)).forEach { (label, range) ->
            val on = if (range == null) more else !more && range == selected
            var textWidth by remember { mutableStateOf(24.dp) }
            val underline by animateDpAsState(if (on) textWidth else 0.dp, tween(180, easing = XpEase.out), label = "underline")
            Column(
                Modifier.weight(1f).pressableClick(haptic = false) { if (range == null) onMore() else onSelect(range) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    style = SegmentLabel.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (on) XpColors.Ink else XpColors.InkTertiary,
                    maxLines = 1,
                    modifier = Modifier.onSizeChanged { textWidth = with(density) { it.width.toDp() } },
                )
                Spacer(Modifier.height(5.dp))
                Box(Modifier.width(underline).height(2.dp).clip(RoundedCornerShape(1.dp)).background(XpColors.Accent))
            }
        }
    }
}

// ── period row ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun PeriodRow(
    snapshot: StatsSnapshot,
    range: StatsRange,
    onShift: (Int) -> Unit,
    onOpenPicker: () -> Unit,
    direction: Direction,
    onDirection: (Direction) -> Unit,
) {
    val custom = range == StatsRange.Custom
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!custom) CircleArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上一期") { onShift(-1) }
        Row(
            Modifier.clip(RoundedCornerShape(XpRadius.pill))
                .pressableClick(haptic = false, onClick = onOpenPicker)
                .padding(horizontal = if (custom) 2.dp else XpSpace.s, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (custom) "${StatsAggregator.md(snapshot.from)}-${StatsAggregator.md(snapshot.to - DAY)}"
                else snapshot.periodTitle,
                style = SegmentLabel.copy(fontWeight = FontWeight.Medium),
                color = XpColors.Ink,
                maxLines = 1,
            )
            if (custom) {
                Spacer(Modifier.width(4.dp))
                Text("▾", style = Hint, color = XpColors.InkSecondary)
            }
        }
        if (!custom) CircleArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下一期") { onShift(1) }
        Spacer(Modifier.weight(1f))
        DirectionChips(direction, onDirection)
    }
}

private const val DAY = 86_400_000L

@Composable
private fun DirectionChips(direction: Direction, onDirection: (Direction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        listOf(Direction.OUT to "支出", Direction.IN to "收入").forEach { (value, label) ->
            val on = value == direction
            Box(
                Modifier.height(30.dp).clip(RoundedCornerShape(XpRadius.pill))
                    .background(if (on) XpColors.Fill else androidx.compose.ui.graphics.Color.Transparent)
                    .pressableClick(haptic = false) { onDirection(value) }
                    .padding(horizontal = XpSpace.m),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = Body13.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (on) XpColors.Ink else XpColors.InkSecondary,
                )
            }
        }
    }
}

@Composable
private fun CircleArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    // 44dp touch target; the visible pill stays 26dp (mobile guideline: tappable ≥44).
    Box(
        Modifier.size(44.dp).pressableClick(haptic = true, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(XpColors.Fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = XpColors.Ink, modifier = Modifier.size(15.dp))
        }
    }
}

// ── cards ─────────────────────────────────────────────────────────────────────────────────────

/** Quiet summary: hairline card, 11sp label, 13sp copy, inline link. No avatar, fill, or accent chrome. */
@Composable
private fun AiSummaryCard(title: String, line: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().cardSurface()
            .pressableClick(haptic = false, onClick = onOpen)
            .padding(horizontal = XpSpace.l, vertical = XpSpace.m),
    ) {
        Text(title, style = Hint.copy(fontWeight = FontWeight.SemiBold), color = XpColors.InkSecondary)
        Spacer(Modifier.height(4.dp))
        Text(line, style = Body13, color = XpColors.InkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        // Its own line: inline after a 2-line clamp it was the first thing the ellipsis ate at 1.3×.
        Text(
            "查看 AI 分析 ›",
            style = Body13.copy(fontWeight = FontWeight.Medium),
            color = XpColors.Accent,
            maxLines = 1,
        )
    }
}

/** 2×2 card with a hairline cross; 自定义 shows only the two cells it can honestly compute. */
@Composable
private fun KpiCard(snapshot: StatsSnapshot, modifier: Modifier = Modifier) {
    val isSpend = snapshot.direction == Direction.OUT
    // Count-up values are read outside buildList: they are composable, the builder is not.
    val totalText = StatsAggregator.grouped(animatedCents(snapshot.total))
    val avgText = StatsAggregator.grouped(animatedCents(snapshot.avgPerDay))
    val balanceText = StatsAggregator.grouped(animatedCents(snapshot.balance))
    val cells = buildList {
        add(KpiCell(if (isSpend) "本期支出" else "本期收入", "¥ $totalText"))
        add(KpiCell(if (isSpend) "日均支出" else "日均收入", "¥ $avgText"))
        if (snapshot.range != StatsRange.Custom) {
            val percent = snapshot.delta?.percent
            add(
                KpiCell(
                    snapshot.deltaCaption,
                    value = when {
                        percent == null -> "—"
                        percent == 0 -> "0%"
                        percent > 0 -> "▲ $percent%"
                        else -> "▼ ${-percent}%"
                    },
                    tone = when {
                        percent == null || percent == 0 -> Tone.Plain
                        (percent > 0) == isSpend -> Tone.Negative
                        else -> Tone.Positive
                    },
                ),
            )
        }
        add(
            KpiCell(
                "收支结余",
                "¥ $balanceText",
                tone = if (snapshot.balance < 0) Tone.Negative else Tone.Plain,
            ),
        )
    }
    Column(modifier.fillMaxWidth().cardSurface()) {
        cells.chunked(2).forEachIndexed { index, row ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(XpColors.Hairline))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                row.forEachIndexed { cellIndex, cell ->
                    if (cellIndex > 0) Box(Modifier.width(0.5.dp).height(52.dp).background(XpColors.Hairline))
                    KpiCellView(cell, Modifier.weight(1f))
                }
            }
        }
    }
}

private enum class Tone { Plain, Positive, Negative }

private data class KpiCell(val label: String, val value: String, val tone: Tone = Tone.Plain)

@Composable
private fun KpiCellView(cell: KpiCell, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = XpSpace.l, vertical = 10.dp)) {
        Text(cell.label, style = KpiLabel, color = XpColors.InkSecondary, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        // One line that shrinks to fit (「¥ -1,234.56」 at 360dp × 1.3 used to wrap and show only 「¥」).
        androidx.compose.foundation.text.BasicText(
            cell.value,
            style = KpiValue.copy(
                color = when (cell.tone) {
                    Tone.Plain -> XpColors.Ink
                    Tone.Positive -> XpColors.Positive
                    Tone.Negative -> XpColors.Negative
                },
            ),
            maxLines = 1,
            softWrap = false,
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 26.sp, stepSize = 1.sp),
        )
    }
}

@Composable
private fun TrendCard(snapshot: StatsSnapshot, modifier: Modifier = Modifier, onPickBucket: (StatsBucket) -> Unit = {}) {
    val noun = if (snapshot.direction == Direction.OUT) "支出" else "收入"
    val title = when (snapshot.range) {
        StatsRange.Week -> "近 5 周$noun"
        StatsRange.Month -> "${StatsAggregator.md(snapshot.from).substringBefore('/')}月${noun}趋势"
        StatsRange.Year -> "年度$noun 趋势"
        StatsRange.Custom -> "区间$noun 趋势"
    }
    Column(
        modifier.fillMaxWidth().cardSurface()
            .padding(horizontal = XpSpace.l, vertical = XpSpace.m),
    ) {
        Text(title, style = SectionTitle, color = XpColors.Ink)
        Spacer(Modifier.height(XpSpace.s))
        when (snapshot.chart) {
            // Tap a bar to move the whole page to that period.
            StatsChart.Bars -> BarTrend(
                snapshot.buckets,
                selected = snapshot.buckets.indexOfFirst { it.start == snapshot.from },
                onSelect = { i -> onPickBucket(snapshot.buckets[i]) },
            )
            StatsChart.Line -> LineTrend(
                snapshot.buckets,
                tickEvery = snapshot.xTickEvery,
                highlightIndex = snapshot.highlightIndex,
            )
        }
    }
}

/** 环形图 + 主分类/子分类 switch; labels are gray, only the leader lines carry colour. */
@Composable
private fun CompositionCard(
    snapshot: StatsSnapshot,
    grouping: StatsGrouping,
    onGrouping: (StatsGrouping) -> Unit,
    onOpen: (StatsEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember(snapshot.entries, grouping) { mutableStateOf(0) }
    val noun = if (snapshot.direction == Direction.OUT) "支出" else "收入"
    Column(
        modifier.fillMaxWidth().cardSurface()
            .padding(horizontal = XpSpace.l, vertical = XpSpace.m),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${noun}构成", style = SectionTitle, color = XpColors.Ink, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.xs)) {
                StatsGrouping.entries.forEach { g ->
                    val on = g == grouping
                    Box(
                        Modifier.height(22.dp).clip(RoundedCornerShape(XpRadius.pill))
                            .background(if (on) XpColors.Fill else androidx.compose.ui.graphics.Color.Transparent)
                            .pressableClick(haptic = false) { onGrouping(g) }
                            .padding(horizontal = XpSpace.s + 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            g.label,
                            style = Hint.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                            color = if (on) XpColors.Ink else XpColors.InkSecondary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(XpSpace.xs))
        DonutChart(
            entries = snapshot.entries, selected = selected, onSelect = { selected = it },
            onOpen = { i -> snapshot.entries.getOrNull(i)?.let(onOpen) },
        )
    }
}

/** Ranking rows: faint rank, 32dp tile, name over a 2dp share bar, 「N 笔 · 商户」, amount right. */
@Composable
private fun RankingCard(snapshot: StatsSnapshot, modifier: Modifier = Modifier, onOpen: (StatsEntry) -> Unit = {}) {
    var expanded by remember(snapshot.hashCode()) { mutableStateOf(false) }
    val rows = if (expanded) snapshot.entries else snapshot.entries.take(8)
    val maxCents = snapshot.entries.firstOrNull()?.cents ?: 1L
    Column(modifier.fillMaxWidth().cardSurface()) {
        Text(
            "${snapshot.periodTitle}${if (snapshot.direction == Direction.OUT) "支出" else "收入"}排行",
            style = SectionTitle,
            color = XpColors.Ink,
            modifier = Modifier.padding(start = XpSpace.l, end = XpSpace.l, top = XpSpace.m, bottom = XpSpace.s),
        )
        rows.forEachIndexed { index, entry ->
            if (index > 0) Box(Modifier.fillMaxWidth().padding(start = 50.dp).height(0.5.dp).background(XpColors.Hairline))
            RankRow(index + 1, entry, maxCents) { onOpen(entry) }
        }
        if (snapshot.entries.size > 8) {
            Text(
                if (expanded) "收起" else "查看全部 ›",
                style = Body13,
                color = XpColors.Accent,
                modifier = Modifier.fillMaxWidth().pressableClick(haptic = false) { expanded = !expanded }
                    .padding(horizontal = XpSpace.l, vertical = XpSpace.m),
            )
        }
    }
}

@Composable
private fun RankRow(rank: Int, entry: StatsEntry, maxCents: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressableClick(haptic = true, onClick = onClick).padding(horizontal = XpSpace.l, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank.toString(),
            style = Hint,
            color = XpColors.InkTertiary,
            modifier = Modifier.width(16.dp),
        )
        IconTile(entry.category.icon(), size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = RowName, color = XpColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(XpColors.Fill)) {
                Box(
                    Modifier.fillMaxWidth((entry.cents.toFloat() / maxCents).coerceIn(0.02f, 1f))
                        .height(2.dp).clip(RoundedCornerShape(1.dp)).background(XpColors.Accent.copy(alpha = 0.55f)),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                if (entry.detail.isNullOrBlank()) "${entry.count} 笔" else "${entry.count} 笔 · ${entry.detail}",
                style = Hint,
                color = XpColors.InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(XpSpace.m))
        Text(
            "¥ ${StatsAggregator.grouped(entry.cents)}",
            style = RowMoney,
            color = XpColors.Ink,
            maxLines = 1,
        )
    }
}

/** The 「更多」 tab: the four pages that used to own the whole 统计 tab (§3.3 keeps them reachable). */
@Composable
private fun MoreSection(
    onBudget: () -> Unit,
    onAwards: () -> Unit,
    onSubscriptions: () -> Unit,
    onRegret: () -> Unit,
) {
    GroupedSection(dividerInset = 20.dp, margin = 0.dp) {
        item("budget") {
            GroupedRow("预算", subtitle = "每日 / 每周 / 每月额度与提醒", chevron = true, onClick = onBudget)
        }
        item("awards") {
            GroupedRow("每周小票", subtitle = "头衔、徽章与周报", chevron = true, onClick = onAwards)
        }
        item("subs") {
            GroupedRow("订阅侦探", subtitle = "找出一直在扣钱的订阅", chevron = true, onClick = onSubscriptions)
        }
        item("regret") {
            GroupedRow("后悔模拟", subtitle = "重来一次，这笔还会买吗", chevron = true, onClick = onRegret)
        }
    }
}

// ── motion ────────────────────────────────────────────────────────────────────────────────────

/** Cards fade and rise 4dp whenever the data behind them changes, staggered by [index]. */
@Composable
private fun Modifier.enterOnChange(key: Any, index: Int, reduceMotion: Boolean): Modifier {
    val anim = remember(key) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduceMotion) {
            delay((index * 30).toLong())
            anim.animateTo(1f, tween(200, easing = XpEase.out))
        }
    }
    return this.graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * 4.dp.toPx()
    }
}
