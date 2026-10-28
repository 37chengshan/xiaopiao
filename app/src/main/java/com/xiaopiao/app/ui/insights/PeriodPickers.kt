package com.xiaopiao.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.insights.StatsAggregator
import com.xiaopiao.app.insights.StatsRange
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import java.util.Calendar

/**
 * 「选择周」 sheet: three weeks per row, tap applies immediately (no confirm button — §3.7).
 * Months are paged with the same ‹ › circle buttons as the page itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekPickerSheet(
    anchorWeekStart: Long,
    currentWeekStart: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var month by remember { mutableStateOf(StatsAggregator.startOfMonth(anchorWeekStart)) }
    XpSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page).padding(bottom = XpSpace.l)) {
            Text(
                "选择周",
                style = MaterialTheme.typography.titleMedium,
                color = XpColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = XpSpace.s),
            )
            MonthHeader(month, onShift = { month = StatsAggregator.startOfMonth(addMonths(month, it)) })

            val weeks = weeksOfMonth(month)
            Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                weeks.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(top = XpSpace.s), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                        row.forEach { start ->
                            WeekCell(
                                weekStart = start,
                                selected = start == anchorWeekStart,
                                current = start == currentWeekStart,
                                modifier = Modifier.weight(1f),
                            ) { onPick(start) }
                        }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCell(weekStart: Long, selected: Boolean, current: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fg = when {
        selected -> XpColors.Accent
        current -> XpColors.Accent
        else -> XpColors.Ink
    }
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) XpColors.AccentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = XpSpace.m, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val head = if (current) "本周" else "第 ${weekOfYear(weekStart)} 周"
        Text(head, style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1)
        Text(
            "${StatsAggregator.md(weekStart)}—${StatsAggregator.md(addDays(weekStart, 6))}",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected || current) XpColors.Accent else XpColors.InkSecondary,
            maxLines = 1,
        )
    }
}

/** 「日期范围 ▾」 sheet: pick the start day, then the end day, then confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangePickerSheet(
    from: Long,
    to: Long,
    onApply: (Long, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var start by remember { mutableStateOf(startOfDay(from)) }
    var end by remember { mutableStateOf(startOfDay(to)) }
    var month by remember { mutableStateOf(StatsAggregator.startOfMonth(startOfDay(from))) }
    // Which end the next tap edits: the sheet starts waiting for a new start.
    var pickingStart by remember { mutableStateOf(true) }

    XpSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page).padding(bottom = XpSpace.l)) {
            Text(
                "选择日期范围",
                style = MaterialTheme.typography.titleMedium,
                color = XpColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = XpSpace.s),
            )
            Row(Modifier.fillMaxWidth().padding(bottom = XpSpace.s), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                RangePill(
                    text = StatsAggregator.md(start),
                    active = pickingStart,
                    modifier = Modifier.weight(1f),
                ) { pickingStart = true; month = StatsAggregator.startOfMonth(start) }
                Text("—", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkTertiary)
                RangePill(
                    text = StatsAggregator.md(end),
                    active = !pickingStart,
                    modifier = Modifier.weight(1f),
                ) { pickingStart = false; month = StatsAggregator.startOfMonth(end) }
            }
            MonthHeader(month, onShift = { month = StatsAggregator.startOfMonth(addMonths(month, it)) })
            MonthGrid(
                anchor = month,
                from = start,
                to = end,
                onPick = { day ->
                    if (pickingStart) {
                        start = day
                        if (end < day) end = day
                        pickingStart = false
                    } else {
                        end = if (day < start) start else day
                        pickingStart = true
                    }
                },
            )
            XpButton(
                "确定",
                onClick = { onApply(minOf(start, end), maxOf(start, end)) },
                modifier = Modifier.fillMaxWidth().padding(top = XpSpace.l),
            )
        }
    }
}

@Composable
private fun RangePill(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(XpRadius.pill))
            .background(if (active) XpColors.AccentSoft else XpColors.Fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) XpColors.Accent else XpColors.Ink,
        )
    }
}

@Composable
private fun MonthHeader(month: Long, onShift: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上个月") { onShift(-1) }
        Text(
            StatsAggregator.periodTitle(StatsRange.Month, month, addMonths(month, 1)),
            style = MaterialTheme.typography.titleSmall,
            color = XpColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        CircleArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下个月") { onShift(1) }
    }
}

@Composable
private fun CircleArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(XpColors.Fill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = XpColors.Ink, modifier = Modifier.size(18.dp))
    }
}

private val weekdayHead = listOf("一", "二", "三", "四", "五", "六", "日")

/** Monday-first month grid; days outside [from, to] read as plain text, the range is accent-soft. */
@Composable
fun MonthGrid(
    anchor: Long,
    from: Long,
    to: Long,
    onPick: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = XpSpace.xs)) {
            weekdayHead.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = XpColors.InkTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val cells = monthCells(anchor)
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                        if (day != 0L) {
                            val inRange = day in startOfDay(from)..startOfDay(to)
                            val isEnd = day == startOfDay(from) || day == startOfDay(to)
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (inRange) XpColors.AccentSoft else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { onPick(day) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    dayOfMonth(day).toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (inRange) XpColors.Accent else XpColors.Ink,
                                )
                            }
                            if (isEnd) {
                                // The two endpoints keep a dot so the range reads even at a glance.
                                Box(
                                    Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                                        .size(2.dp).clip(CircleShape).background(XpColors.Accent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── pure calendar helpers (kept here so the sheets need no ledger imports) ────────────────────

internal fun addMonths(ms: Long, n: Int): Long =
    Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.MONTH, n) }.timeInMillis

internal fun addDays(ms: Long, n: Int): Long =
    Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.DAY_OF_YEAR, n) }.timeInMillis

internal fun weekOfYear(ms: Long): Int =
    Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.WEEK_OF_YEAR)

internal fun dayOfMonth(ms: Long): Int =
    Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_MONTH)

/** Week starts (Mondays) that any day of [month]'s month falls into. */
internal fun weeksOfMonth(monthAnchor: Long): List<Long> {
    val first = StatsAggregator.startOfMonth(monthAnchor)
    val last = addDays(StatsAggregator.startOfMonth(addMonths(first, 1)), -1)
    var start = com.xiaopiao.app.insights.startOfWeek(first)
    val out = mutableListOf<Long>()
    while (start <= last) {
        out += start
        start = addDays(start, 7)
    }
    return out
}

/** 7-column Monday-first grid for one month; `0` marks a blank cell. */
internal fun monthCells(anchor: Long): List<Long> {
    val first = StatsAggregator.startOfMonth(anchor)
    val cal = Calendar.getInstance().apply { timeInMillis = first; firstDayOfWeek = Calendar.MONDAY }
    val leading = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val total = ((leading + days + 6) / 7) * 7
    return List(total) { i ->
        val d = i - leading
        if (d < 0 || d >= days) 0L else startOfDay(first + d * DAY_MS)
    }
}
