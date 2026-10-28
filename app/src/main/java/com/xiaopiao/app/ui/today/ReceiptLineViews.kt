package com.xiaopiao.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.components.NumberTicker
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.ReceiptHandwriting
import com.xiaopiao.app.ui.theme.ReceiptRow
import com.xiaopiao.app.ui.theme.XpColors
import java.util.Random

/** Clock the line views read; the lambda is read inside derived state / draw blocks to avoid per-frame recomposition. */
class PrintClock(val timeline: PrintTimeline, val instant: Boolean, val elapsed: () -> Long) {
    fun now(): Long = if (instant) Long.MAX_VALUE / 4 else elapsed()
}

private fun takeCp(s: String, n: Int): String {
    val count = s.codePointCount(0, s.length)
    return if (n >= count) s else s.substring(0, s.offsetByCodePoints(0, n.coerceAtLeast(0)))
}

/** Renders one receipt line at its print progress: typed text, wiping dashes/barcode, rolling total. */
@Composable
fun ReceiptLineView(line: ReceiptLine, step: PrintStep, clock: PrintClock, onToggleMore: () -> Unit) {
    val box = Modifier.fillMaxWidth().height(line.kind.heightDp.dp)
    when (line.kind) {
        LineKind.BLANK, LineKind.STAMP_SPACE -> Box(box)
        LineKind.DASH -> DashLine(step, clock, box)
        LineKind.BARCODE -> Barcode(line.extra, step, clock, box)
        LineKind.TOTAL -> TotalRow(line, step, clock, box)
        LineKind.ITEM, LineKind.INCOME -> ItemRow(line, step, clock, box)
        else -> TypedText(line, step, clock, box, onToggleMore)
    }
}

@Composable
private fun typedCount(step: PrintStep, clock: PrintClock): Int {
    val n by remember(step, clock) {
        derivedStateOf { PrintTimeline.typedChars(step.textLength, clock.timeline.textProgress(step, clock.now())) }
    }
    return n
}

@Composable
private fun TypedText(line: ReceiptLine, step: PrintStep, clock: PrintClock, modifier: Modifier, onToggleMore: () -> Unit) {
    val n = typedCount(step, clock)
    val (style, align) = when (line.kind) {
        LineKind.TITLE -> ReceiptRow.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp) to TextAlign.Center
        LineKind.SUBTITLE -> ReceiptRow.copy(fontSize = 12.sp, color = XpColors.InkSecondary) to TextAlign.Center
        LineKind.DEMO -> ReceiptRow.copy(fontSize = 11.sp, color = XpColors.Accent, fontWeight = FontWeight.Bold) to TextAlign.Center
        LineKind.BIG -> ReceiptRow.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold) to TextAlign.Center
        LineKind.FOOTER -> ReceiptRow.copy(fontSize = 11.sp, color = XpColors.InkSecondary) to TextAlign.Center
        LineKind.COMMENT -> ReceiptHandwriting to TextAlign.Start
        LineKind.MORE -> ReceiptRow.copy(color = XpColors.Accent, fontWeight = FontWeight.Bold) to TextAlign.Center
        else -> ReceiptRow to TextAlign.Start
    }
    val click = if (line.kind == LineKind.MORE) modifier.pressableClick(onClick = onToggleMore) else modifier
    Box(click, contentAlignment = Alignment.Center) {
        Text(takeCp(line.left, n), style = style, textAlign = align, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ItemRow(line: ReceiptLine, step: PrintStep, clock: PrintClock, modifier: Modifier) {
    val n = typedCount(step, clock)
    val rightShown by remember(step, clock) {
        derivedStateOf { clock.timeline.textProgress(step, clock.now()) >= 0.6f }
    }
    val color = if (line.positive) XpColors.Positive else XpColors.PaperInk
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(takeCp(line.left, n), style = ReceiptRow, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (rightShown) Text(line.right, style = ReceiptRow.copy(color = color, fontWeight = FontWeight.Bold), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun TotalRow(line: ReceiptLine, step: PrintStep, clock: PrintClock, modifier: Modifier) {
    val started by remember(step, clock) { derivedStateOf { clock.now() >= step.startMs + 200 } }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(line.left, style = ReceiptRow.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
        if (started) {
            NumberTicker(
                text = line.right,
                style = TextStyle(fontFamily = ReceiptRow.fontFamily, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                color = XpColors.PaperInk,
                rollFromZero = !clock.instant,
            )
        }
    }
}

@Composable
private fun DashLine(step: PrintStep, clock: PrintClock, modifier: Modifier) {
    Canvas(modifier) {
        val p = clock.timeline.textProgress(step, clock.now())
        val y = size.height / 2f
        drawLine(
            XpColors.InkTertiary, Offset(0f, y), Offset(size.width * p, y), strokeWidth = 1.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
    }
}

@Composable
private fun Barcode(seed: Int, step: PrintStep, clock: PrintClock, modifier: Modifier) {
    val bars = remember(seed) {
        val r = Random(seed.toLong() * 7919L + 13L)
        List(46) { 1 + r.nextInt(3) }
    }
    Canvas(modifier.fillMaxSize()) {
        val p = clock.timeline.textProgress(step, clock.now())
        val unit = size.width * 0.78f / bars.sumOf { it * 2 }
        var x = size.width * 0.11f
        val limit = size.width * (0.11f + 0.78f * p)
        bars.forEach { b ->
            val w = b * unit
            if (x + w <= limit) drawRect(XpColors.PaperInk, Offset(x, size.height * 0.1f), androidx.compose.ui.geometry.Size(w, size.height * 0.8f))
            x += w + b * unit
        }
    }
}
