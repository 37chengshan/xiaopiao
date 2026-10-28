package com.xiaopiao.app.ui.merchant

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.merchant.Aggregator
import com.xiaopiao.app.merchant.Cell
import com.xiaopiao.app.merchant.DayPart
import com.xiaopiao.app.merchant.OptionStat
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

const val SUPPRESSED_TEXT = "样本不足"

/** 7 × 6 weekday/day-part heatmap. Suppressed (k < 20) cells are drawn grey and say 样本不足 when tapped. */
@Composable
fun Heatmap(
    heat: List<List<Cell>>,
    max: Int,
    selected: Pair<Int, Int>?,
    onSelect: (Pair<Int, Int>) -> Unit,
) {
    val t = rememberEnter(heat)
    val parts = DayPart.entries
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(24.dp))
            parts.forEach {
                Text(
                    it.label.substringBefore(' '), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
                )
            }
        }
        Row {
            Column(Modifier.width(24.dp), verticalArrangement = Arrangement.spacedBy(GAP)) {
                Aggregator.weekdayLabels.forEach {
                    Box(Modifier.height(ROW_H), contentAlignment = Alignment.CenterStart) {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
                    }
                }
            }
            Canvas(
                Modifier.weight(1f).height(ROW_H * 7 + GAP * 6)
                    .pointerInput(heat) {
                        detectTapGestures { p ->
                            val cw = size.width / parts.size
                            val rh = size.height / 7f
                            val c = (p.x / cw).toInt().coerceIn(0, parts.size - 1)
                            val r = (p.y / rh).toInt().coerceIn(0, 6)
                            onSelect(r to c)
                        }
                    },
            ) {
                val gap = GAP.toPx()
                val cw = (size.width - gap * (parts.size - 1)) / parts.size
                val rh = (size.height - gap * 6) / 7f
                heat.forEachIndexed { r, row ->
                    row.forEachIndexed { c, cell ->
                        val local = (t * 1.6f - (r + c) / 14f).coerceIn(0f, 1f)
                        val x = c * (cw + gap)
                        val y = r * (rh + gap)
                        val color = if (cell.suppressed) XpColors.InkTertiary.copy(alpha = 0.28f)
                        else XpColors.Accent.copy(alpha = (0.12f + 0.88f * cell.people!! / max.coerceAtLeast(1)) * local)
                        val inset = (1f - local) * cw / 2
                        drawRoundRect(
                            color, Offset(x + inset, y + inset * rh / cw),
                            Size(cw - inset * 2, rh - inset * 2 * rh / cw), CornerRadius(6.dp.toPx()),
                        )
                        if (selected == r to c) {
                            drawRoundRect(
                                XpColors.Ink, Offset(x, y), Size(cw, rh), CornerRadius(6.dp.toPx()),
                                style = Stroke(2.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val ROW_H = 28.dp
private val GAP = 3.dp

/** Single-slice-per-entry donut. Suppressed entries are not drawn (they stay greyed in the legend). */
@Composable
fun Donut(values: List<Pair<Color, Int?>>, modifier: Modifier = Modifier, center: @Composable () -> Unit = {}) {
    val t = rememberEnter(values)
    val total = values.sumOf { it.second ?: 0 }.coerceAtLeast(1)
    Box(modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            var start = -90f
            values.forEach { (color, n) ->
                if (n == null) return@forEach
                val sweep = 360f * n / total * t
                drawArc(
                    color, start + 1.5f, (sweep - 3f).coerceAtLeast(0.1f), false,
                    Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        center()
    }
}

/** Horizontal bar with label and value. A suppressed cell shows a grey track and 样本不足. */
@Composable
fun CellBar(cell: Cell, maxShare: Float, color: Color, index: Int = 0) {
    val t = rememberEnter(cell, delayMs = index * 60L)
    val frac = if (cell.suppressed || maxShare <= 0f) 0f else (cell.share!! / maxShare) * t
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            cell.label, Modifier.width(78.dp), style = MaterialTheme.typography.bodyMedium,
            color = if (cell.suppressed) XpColors.InkTertiary else XpColors.Ink, maxLines = 1,
        )
        Box(
            Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp))
                .background(XpColors.InkTertiary.copy(alpha = if (cell.suppressed) 0.28f else 0.14f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(7.dp)).background(color))
        }
        Text(
            if (cell.suppressed) SUPPRESSED_TEXT else "${(cell.share!! * 100).toInt()}%",
            Modifier.width(60.dp), textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelMedium,
            color = if (cell.suppressed) XpColors.InkTertiary else XpColors.InkSecondary,
        )
    }
}

/** Two-segment split bar whose widths spring to the shares. */
@Composable
fun SplitBar(a: OptionStat, b: OptionStat) {
    val shareA by animateFloatAsState(a.share.toFloat(), SpringSoft, label = "splitA")
    Canvas(Modifier.fillMaxWidth().height(40.dp)) {
        val gap = 4.dp.toPx()
        val usable = size.width - gap
        val wa = (usable * shareA).coerceAtLeast(0f)
        val wb = (usable - wa).coerceAtLeast(0f)
        val r = CornerRadius(14.dp.toPx())
        if (wa > 0.5f) drawRoundRect(XpColors.Accent, Offset.Zero, Size(wa, size.height), r)
        if (wb > 0.5f) drawRoundRect(XpColors.Ink, Offset(wa + gap, 0f), Size(wb, size.height), r)
    }
}

/** 95% CI whisker on a 0–100% axis. */
@Composable
fun CiWhisker(stat: OptionStat, color: Color) {
    val t = rememberEnter(stat)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${stat.option.emoji} ${stat.option.title}", style = MaterialTheme.typography.labelMedium, color = XpColors.Ink)
            Text(
                "${(stat.share * 100).toInt()}%（${(stat.ciLow * 100).toInt()}–${(stat.ciHigh * 100).toInt()}%）",
                style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val cy = size.height / 2
            val w = size.width
            drawLine(XpColors.Hairline, Offset(0f, cy), Offset(w, cy), 2.dp.toPx())
            listOf(0f, .25f, .5f, .75f, 1f).forEach {
                drawLine(XpColors.Hairline, Offset(w * it, cy - 5.dp.toPx()), Offset(w * it, cy + 5.dp.toPx()), 1.dp.toPx())
            }
            val centre = stat.share.toFloat()
            val lo = centre + (stat.ciLow.toFloat() - centre) * t
            val hi = centre + (stat.ciHigh.toFloat() - centre) * t
            drawLine(color, Offset(w * lo, cy), Offset(w * hi, cy), 4.dp.toPx(), StrokeCap.Round)
            drawLine(color, Offset(w * lo, cy - 7.dp.toPx()), Offset(w * lo, cy + 7.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawLine(color, Offset(w * hi, cy - 7.dp.toPx()), Offset(w * hi, cy + 7.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(XpColors.Card, 7.dp.toPx(), Offset(w * centre, cy))
            drawCircle(color, 5.dp.toPx(), Offset(w * centre, cy))
        }
    }
}
