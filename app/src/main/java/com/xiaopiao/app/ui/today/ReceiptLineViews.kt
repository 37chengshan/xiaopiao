package com.xiaopiao.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.components.NumberTicker
import com.xiaopiao.app.ui.theme.ReceiptHandwriting
import com.xiaopiao.app.ui.theme.ReceiptRow
import com.xiaopiao.app.ui.theme.XpColors

/** Clock the paper reads; the lambda is only called inside layout/draw/derived state so frames never recompose the screen. */
class PrintClock(val timeline: PrintTimeline, val instant: Boolean, val elapsed: () -> Long) {
    fun now(): Long = if (instant) Long.MAX_VALUE / 4 else elapsed()
}

/** Paper ink in three strengths. Paper is paper-coloured in both themes, so never use the app's Ink tokens here. */
internal object PaperInk {
    val strong get() = XpColors.PaperInk
    val soft get() = XpColors.PaperInk.copy(alpha = 0.66f)
    val faint get() = XpColors.PaperInk.copy(alpha = 0.40f)
    /** Income green that stays readable on paper in both themes. */
    val income: Color get() = XpColors.Positive
}

/** Renders one receipt line. The paper itself reveals it (clip) as it feeds out, so the content is static. */
@Composable
fun ReceiptLineView(line: ReceiptLine, step: PrintStep, clock: PrintClock) {
    val box = Modifier.fillMaxWidth().height(line.kind.heightDp.dp)
    when (line.kind) {
        LineKind.BLANK, LineKind.STAMP_SPACE -> Box(box)
        LineKind.DASH -> DashLine(box)
        LineKind.TOTAL -> TotalBlock(line, step, clock, box)
        LineKind.STATS -> StatsRow(line, box)
        LineKind.META -> TwoEnds(line, box, PaperInk.soft, ReceiptRow.copy(fontSize = 12.sp, color = PaperInk.strong))
        LineKind.HEADER -> TwoEnds(line, box, PaperInk.faint, ReceiptRow.copy(fontSize = 11.sp, color = PaperInk.faint, letterSpacing = 1.sp))
        LineKind.CATEGORY -> TwoEnds(
            line, box, PaperInk.strong,
            ReceiptRow.copy(fontWeight = FontWeight.Bold, color = PaperInk.strong),
            leftStyle = ReceiptRow.copy(fontWeight = FontWeight.Bold, color = PaperInk.strong),
        )
        LineKind.ITEM, LineKind.INCOME -> ItemRow(line, box)
        else -> Centered(line, box)
    }
}

@Composable
private fun Centered(line: ReceiptLine, modifier: Modifier) {
    val style = when (line.kind) {
        LineKind.TITLE -> ReceiptRow.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
        LineKind.SUBTITLE -> ReceiptRow.copy(fontSize = 12.sp, color = PaperInk.soft)
        LineKind.DEMO -> ReceiptRow.copy(fontSize = 11.sp, color = PaperInk.soft, fontWeight = FontWeight.Bold)
        LineKind.NOTICE -> ReceiptRow.copy(fontSize = 11.sp, color = PaperInk.faint)
        LineKind.BIG -> ReceiptRow.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        LineKind.FOOTER -> ReceiptRow.copy(fontSize = 11.sp, color = PaperInk.faint, letterSpacing = 0.5.sp)
        LineKind.COMMENT -> ReceiptHandwriting
        LineKind.MORE -> ReceiptRow.copy(fontSize = 11.sp, color = PaperInk.soft)
        else -> ReceiptRow
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(line.left, style = style, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TwoEnds(line: ReceiptLine, modifier: Modifier, leftColor: Color, rightStyle: TextStyle, leftStyle: TextStyle? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            line.left, style = leftStyle ?: ReceiptRow.copy(fontSize = 12.sp, color = leftColor), maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(line.right, style = rightStyle, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun ItemRow(line: ReceiptLine, modifier: Modifier) {
    val income = line.kind == LineKind.INCOME
    val color = if (line.positive) PaperInk.income else PaperInk.soft
    Row(
        modifier.padding(start = if (line.kind == LineKind.ITEM) 18.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            line.left, style = ReceiptRow.copy(fontSize = 12.sp, color = if (income) color else PaperInk.soft),
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(line.right, style = ReceiptRow.copy(fontSize = 12.sp, color = if (line.positive) color else PaperInk.strong), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun StatsRow(line: ReceiptLine, modifier: Modifier) {
    val style = ReceiptRow.copy(fontSize = 11.sp, color = PaperInk.soft, textAlign = TextAlign.Center)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(line.left, style = style, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false)
        Text(line.right, style = style, modifier = Modifier.weight(1.35f), maxLines = 1, softWrap = false)
        Text("本周第 ${line.extra} 天", style = style, modifier = Modifier.weight(1.1f), maxLines = 1, softWrap = false)
    }
}

/** 今日支出 caption + a big centred total whose digits roll once the line reaches the slot. */
@Composable
private fun TotalBlock(line: ReceiptLine, step: PrintStep, clock: PrintClock, modifier: Modifier) {
    val started by remember(step, clock) { derivedStateOf { clock.now() >= step.startMs } }
    val digits = line.right.removePrefix("¥")
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(line.left, style = ReceiptRow.copy(fontSize = 12.sp, color = PaperInk.soft, letterSpacing = 3.sp))
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                Text("¥", style = ReceiptRow.copy(fontSize = 20.sp, color = PaperInk.soft, fontWeight = FontWeight.Medium), modifier = Modifier.padding(end = 3.dp, bottom = 5.dp))
                // Reserve the final width so the number never shifts while rolling; the ticker appears in place.
                Box {
                    Text(digits, style = BigTotalStyle.copy(color = Color.Transparent))
                    if (started) NumberTicker(text = digits, style = BigTotalStyle, color = PaperInk.strong, rollFromZero = !clock.instant)
                }
            }
        }
    }
}

private val BigTotalStyle = TextStyle(
    fontFamily = ReceiptRow.fontFamily, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp,
    fontFeatureSettings = "tnum",
)

@Composable
private fun DashLine(modifier: Modifier) {
    Canvas(modifier) {
        val y = size.height / 2f
        drawLine(
            PaperInk.faint, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
    }
}
