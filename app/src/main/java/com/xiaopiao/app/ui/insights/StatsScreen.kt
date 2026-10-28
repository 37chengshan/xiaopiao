package com.xiaopiao.app.ui.insights

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.insights.StatsAggregator
import com.xiaopiao.app.insights.StatsChart
import com.xiaopiao.app.insights.StatsEntry
import com.xiaopiao.app.insights.StatsGrouping
import com.xiaopiao.app.insights.StatsNarrative
import com.xiaopiao.app.insights.StatsRange
import com.xiaopiao.app.insights.StatsSnapshot
import com.xiaopiao.app.insights.startOfWeek
import com.xiaopiao.app.ui.components.BarTrend
import com.xiaopiao.app.ui.components.DonutChart
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.LineTrend
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.today.ReceiptStrip
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.icon
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpFonts
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(XpRadius.l)
private val ContentShape = RoundedCornerShape(XpRadius.contentCard)

/** KPI value style: 26sp/600 tabular (docs/APP-DESIGN.md §3.7). */
private val KpiValue = TextStyle(
    fontFamily = XpFonts.sans, fontSize = 26.sp, lineHeight = 32.sp,
    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, fontFeatureSettings = "tnum",
)

private val Money17 = TextStyle(
    fontFamily = XpFonts.sans, fontSize = 17.sp, lineHeight = 22.sp,
    fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum",
)

/**
 * 统计 tab landing page (docs/APP-DESIGN.md §3.3, adopted from the wallet reference per §3.7):
 * 周/月/年/自定义 → AI 小结 → KPI → 趋势 → 构成 → 排行, with the old 周报/订阅/后悔 pages as 「更多」.
 */
@Composable
fun StatsScreen(
    container: AppContainer,
    onOpenAwards: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenRegret: () -> Unit,
    onPrint: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
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

    NavScaffold(
        "统计",
        NavScrollNone,
        trailing = {
            ReceiptStrip()
            NavIconButton(Icons.Outlined.Settings, "设置", onClick = onOpenSettings)
        },
    ) { top ->
        Column(
            Modifier.fillMaxSize().padding(top = top)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = XpSpace.page)
                .padding(top = XpSpace.s, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(XpSpace.cardGap),
        ) {
            RangeSegments(range) { range = it }
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
            if (narrative != null) {
                AiSummaryCard(
                    title = snapshot.aiTitle,
                    line = narrative,
                    onOpen = onOpenAwards,
                    modifier = Modifier.enterOnChange(snapshot.hashCode(), 0, reduceMotion),
                )
            }
            KpiCard(snapshot, Modifier.enterOnChange(snapshot.hashCode(), 1, reduceMotion))
            TrendCard(snapshot, Modifier.enterOnChange(snapshot.hashCode(), 2, reduceMotion))
            if (snapshot.entries.isNotEmpty()) {
                CompositionCard(
                    snapshot = snapshot,
                    grouping = grouping,
                    onGrouping = { grouping = it },
                    modifier = Modifier.enterOnChange("${snapshot.hashCode()}-$grouping", 3, reduceMotion),
                )
                RankingCard(snapshot, Modifier.enterOnChange("${snapshot.hashCode()}-$grouping", 4, reduceMotion))
            }
            MoreSection(onOpenAwards, onOpenSubscriptions, onOpenRegret)
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

/** 周报 / 月报 / 年报 / 自定义: 17sp labels, 2dp accent underline as wide as the selected text. */
@Composable
private fun RangeSegments(selected: StatsRange, onSelect: (StatsRange) -> Unit) {
    val density = LocalDensity.current
    Row(Modifier.fillMaxWidth().padding(vertical = XpSpace.s)) {
        StatsRange.entries.forEach { range ->
            val on = range == selected
            var textWidth by remember { mutableStateOf(24.dp) }
            val underline by animateDpAsState(if (on) textWidth else 0.dp, tween(180, easing = XpEase.out), label = "underline")
            Column(
                Modifier.weight(1f).pressableClick(haptic = false) { onSelect(range) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    range.label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (on) XpColors.Ink else XpColors.InkTertiary,
                    maxLines = 1,
                    modifier = Modifier.onSizeChanged { textWidth = with(density) { it.width.toDp() } },
                )
                Spacer(Modifier.height(6.dp))
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
    Row(Modifier.fillMaxWidth().padding(vertical = XpSpace.xs), verticalAlignment = Alignment.CenterVertically) {
        if (!custom) CircleArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上一期") { onShift(-1) }
        Row(
            Modifier.clip(RoundedCornerShape(XpRadius.pill))
                .pressableClick(haptic = false, onClick = onOpenPicker)
                .padding(horizontal = if (custom) 2.dp else XpSpace.s, vertical = XpSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (custom) "${StatsAggregator.md(snapshot.from)}-${StatsAggregator.md(snapshot.to - DAY)}"
                else snapshot.periodTitle,
                style = MaterialTheme.typography.titleMedium,
                color = XpColors.Ink,
                maxLines = 1,
            )
            if (custom) {
                Spacer(Modifier.width(4.dp))
                Text("▾", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
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
                Modifier.height(32.dp).clip(RoundedCornerShape(XpRadius.pill))
                    .background(if (on) XpColors.AccentSoft else XpColors.Fill)
                    .pressableClick(haptic = false) { onDirection(value) }
                    .padding(horizontal = XpSpace.m),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (on) XpColors.Accent else XpColors.Ink,
                )
            }
        }
    }
}

@Composable
private fun CircleArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(XpColors.Fill).pressableClick(haptic = false, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = XpColors.Ink, modifier = Modifier.size(16.dp))
    }
}

// ── cards ─────────────────────────────────────────────────────────────────────────────────────

/** Weak-background AI card: 40dp avatar riding the top-left corner, 15sp/1.6 copy, inline link. */
@Composable
private fun AiSummaryCard(title: String, line: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .clip(ContentShape)
                .background(XpColors.Fill)
                .pressableClick(haptic = false, onClick = onOpen)
                .padding(start = 56.dp, top = XpSpace.m, end = XpSpace.l, bottom = XpSpace.m),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = XpColors.Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                buildAnnotatedString {
                    append(line)
                    withStyle(SpanStyle(color = XpColors.Accent, fontWeight = FontWeight.SemiBold)) {
                        append("　查看 AI 分析 ›")
                    }
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 24.sp),
                color = XpColors.InkSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.padding(start = 12.dp).size(40.dp).clip(CircleShape).background(XpColors.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = XpColors.OnAccent, modifier = Modifier.size(20.dp))
        }
    }
}

/** 2×2 white card with a hairline cross; 自定义 shows only the two cells it can honestly compute. */
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
    Column(modifier.fillMaxWidth().clip(ContentShape).background(XpColors.Card).padding(vertical = XpSpace.s)) {
        cells.chunked(2).forEachIndexed { index, row ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(0.5.dp).background(XpColors.Hairline))
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { cellIndex, cell ->
                    if (cellIndex > 0) Box(Modifier.width(0.5.dp).height(56.dp).background(XpColors.Hairline))
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
    Column(modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.m)) {
        Text(cell.label, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            cell.value,
            style = KpiValue,
            color = when (cell.tone) {
                Tone.Plain -> XpColors.Ink
                Tone.Positive -> XpColors.Positive
                Tone.Negative -> XpColors.Negative
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun TrendCard(snapshot: StatsSnapshot, modifier: Modifier = Modifier) {
    val noun = if (snapshot.direction == Direction.OUT) "支出" else "收入"
    val title = when (snapshot.range) {
        StatsRange.Week -> "近 5 周$noun"
        StatsRange.Month -> "${StatsAggregator.md(snapshot.from).substringBefore('/')}月${noun}趋势"
        StatsRange.Year -> "年度$noun 趋势"
        StatsRange.Custom -> "区间$noun 趋势"
    }
    Column(modifier.fillMaxWidth().clip(CardShape).background(XpColors.Card).padding(XpSpace.l)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
        Spacer(Modifier.height(XpSpace.m))
        when (snapshot.chart) {
            StatsChart.Bars -> BarTrend(snapshot.buckets)
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
    modifier: Modifier = Modifier,
) {
    var selected by remember(snapshot.entries, grouping) { mutableStateOf(0) }
    val noun = if (snapshot.direction == Direction.OUT) "支出" else "收入"
    Column(modifier.fillMaxWidth().clip(CardShape).background(XpColors.Card).padding(XpSpace.l)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${noun}构成", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.xs)) {
                StatsGrouping.entries.forEach { g ->
                    val on = g == grouping
                    Box(
                        Modifier.height(24.dp).clip(RoundedCornerShape(XpRadius.pill))
                            .background(if (on) XpColors.AccentSoft else XpColors.Fill)
                            .pressableClick(haptic = false) { onGrouping(g) }
                            .padding(horizontal = XpSpace.s),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            g.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (on) XpColors.Accent else XpColors.InkSecondary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(XpSpace.s))
        DonutChart(entries = snapshot.entries, selected = selected, onSelect = { selected = it })
    }
}

/** Ranking rows: faint rank, 36dp tile, name over a 3dp share bar, 「N 笔 · 商户」, amount right. */
@Composable
private fun RankingCard(snapshot: StatsSnapshot, modifier: Modifier = Modifier) {
    var expanded by remember(snapshot.hashCode()) { mutableStateOf(false) }
    val rows = if (expanded) snapshot.entries else snapshot.entries.take(8)
    val maxCents = snapshot.entries.firstOrNull()?.cents ?: 1L
    Column(modifier.fillMaxWidth().clip(CardShape).background(XpColors.Card)) {
        Text(
            "${snapshot.periodTitle}${if (snapshot.direction == Direction.OUT) "支出" else "收入"}排行",
            style = MaterialTheme.typography.titleMedium,
            color = XpColors.Ink,
            modifier = Modifier.padding(start = XpSpace.l, end = XpSpace.l, top = XpSpace.l, bottom = XpSpace.s),
        )
        rows.forEachIndexed { index, entry ->
            if (index > 0) Box(Modifier.fillMaxWidth().padding(start = 56.dp).height(0.5.dp).background(XpColors.Hairline))
            RankRow(index + 1, entry, maxCents)
        }
        if (snapshot.entries.size > 8) {
            Text(
                if (expanded) "收起" else "查看全部 ›",
                style = MaterialTheme.typography.bodyMedium,
                color = XpColors.Accent,
                modifier = Modifier.fillMaxWidth().pressableClick(haptic = false) { expanded = !expanded }
                    .padding(horizontal = XpSpace.l, vertical = XpSpace.m),
            )
        }
    }
}

@Composable
private fun RankRow(rank: Int, entry: StatsEntry, maxCents: Long) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkTertiary,
            modifier = Modifier.width(18.dp),
        )
        IconTile(entry.category.icon(), size = 36.dp)
        Spacer(Modifier.width(XpSpace.m))
        Column(Modifier.weight(1f)) {
            Text(entry.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = XpColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(XpColors.Fill)) {
                Box(
                    Modifier.fillMaxWidth((entry.cents.toFloat() / maxCents).coerceIn(0.02f, 1f))
                        .height(3.dp).clip(RoundedCornerShape(2.dp)).background(XpColors.Accent.copy(alpha = 0.55f)),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (entry.detail.isNullOrBlank()) "${entry.count} 笔" else "${entry.count} 笔 · ${entry.detail}",
                style = MaterialTheme.typography.bodySmall,
                color = XpColors.InkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(XpSpace.m))
        Text(
            "¥ ${StatsAggregator.grouped(entry.cents)}",
            style = Money17,
            color = XpColors.Ink,
            maxLines = 1,
        )
    }
}

/** The old pages survive as three rows instead of a segmented tab (§3.3 keeps them reachable). */
@Composable
private fun MoreSection(onAwards: () -> Unit, onSubscriptions: () -> Unit, onRegret: () -> Unit) {
    GroupedSection(header = "更多", dividerInset = 20.dp, margin = 0.dp) {
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

/** Cards fade and rise 8dp whenever the data behind them changes, staggered by [index]. */
@Composable
private fun Modifier.enterOnChange(key: Any, index: Int, reduceMotion: Boolean): Modifier {
    val anim = remember(key) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(key) {
        if (!reduceMotion) {
            delay((index * 35).toLong())
            anim.animateTo(1f, tween(220, easing = XpEase.out))
        }
    }
    return this.graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * 8.dp.toPx()
    }
}
