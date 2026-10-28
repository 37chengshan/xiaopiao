package com.xiaopiao.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

private val weekdayHead = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * Month grid (Monday first) with a ‹ › month header. Used by the 「按天跳转」 sheet and by the
 * filter sheet's custom range, so both read like the same calendar.
 */
@Composable
fun MonthGrid(
    anchor: Long,
    selected: Long?,
    today: Long,
    onPick: (Long) -> Unit,
    onShift: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MonthArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上个月") { onShift(-1) }
            Text(
                LedgerCalendar.title(anchor),
                style = MaterialTheme.typography.titleSmall,
                color = XpColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            MonthArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下个月") { onShift(1) }
        }
        Row(Modifier.fillMaxWidth().padding(top = XpSpace.s)) {
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
        val cells = LedgerCalendar.cells(anchor)
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                week.forEach { dayStart ->
                    Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                        if (dayStart != 0L) {
                            val isSelected = selected != null && startOfDay(selected) == dayStart
                            val isToday = startOfDay(today) == dayStart
                            val textColor = when {
                                isSelected -> XpColors.OnAccent
                                isToday -> XpColors.Accent
                                else -> XpColors.Ink
                            }
                            Box(
                                Modifier.size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) XpColors.Accent else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { onPick(dayStart) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    LedgerCalendar.dayOfMonth(dayStart).toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Keep the grid's height stable across 5- and 6-week months.
        repeat((6 - cells.size / 7).coerceAtLeast(0)) {
            Box(Modifier.fillMaxWidth().height(40.dp))
        }
    }
}

@Composable
private fun MonthArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(XpColors.Fill).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = XpColors.Ink, modifier = Modifier.size(18.dp))
    }
}

/** 「按天跳转」: pick a day, the list scrolls to that day's card. Tapping a day applies immediately. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LedgerCalendarSheet(
    anchor: Long,
    selected: Long?,
    today: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val month = androidx.compose.runtime.remember(anchor) {
        androidx.compose.runtime.mutableStateOf(LedgerRanges.monthStart(anchor))
    }
    XpSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page).padding(bottom = XpSpace.l)) {
            Text(
                "选择日期",
                style = MaterialTheme.typography.titleMedium,
                color = XpColors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = XpSpace.s),
            )
            MonthGrid(
                anchor = month.value,
                selected = selected,
                today = today,
                onPick = { day -> onPick(startOfDay(day)) },
                onShift = { delta -> month.value = LedgerCalendar.shiftMonths(month.value, delta) },
            )
        }
    }
}
