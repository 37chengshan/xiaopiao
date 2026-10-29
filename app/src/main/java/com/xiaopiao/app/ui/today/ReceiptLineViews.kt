package com.xiaopiao.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
import com.xiaopiao.app.ui.theme.ReceiptRow as ThermalRow
import com.xiaopiao.app.ui.theme.XpColors

/** Clock the paper reads; the lambda is only called inside layout/draw/derived state so frames never recompose the screen. */
class PrintClock(val timeline: PrintTimeline, val instant: Boolean, val elapsed: () -> Long) {
    fun now(): Long = if (instant) Long.MAX_VALUE / 4 else elapsed()
}

/** The paper's row style in the typeface chosen in 小票页 (thermal mono by default). */
private val ReceiptRow: TextStyle
    @Composable @ReadOnlyComposable get() = ThermalRow.copy(fontFamily = LocalReceiptFont.current.family)

/** Thermal print is never perfectly even: each line gets a stable, slightly different ink density (0.86..1). */
internal fun inkDensity(index: Int): Float = 0.86f + 0.14f * (((index * 2654435761L) ushr 8) % 100L) / 100f

/** Renders one receipt line. The paper itself reveals it (clip) as it feeds out, so the content is static. */
@Composable
fun ReceiptLineView(line: ReceiptLine, step: PrintStep, clock: PrintClock) {
    val c = LocalPaperColors.current
    val box = Modifier.fillMaxWidth().height(line.kind.heightDp.dp)
    val tint = if (line.kind == LineKind.BLANK || line.kind == LineKind.STAMP_SPACE) Modifier else Modifier.graphicsLayer { alpha = inkDensity(step.index) }
    Box(tint) {
        when (line.kind) {
            LineKind.BLANK, LineKind.STAMP_SPACE -> Box(box)
            LineKind.DASH -> DashLine(box, c)
            LineKind.TOTAL -> TotalBlock(line, step, clock, box, c)
            LineKind.STATS -> StatsRow(line, box, c)
            LineKind.META -> TwoEnds(line, box, c.inkSoft, ReceiptRow.copy(fontSize = 12.sp, color = c.ink))
            LineKind.HEADER -> TwoEnds(line, box, c.inkFaint, ReceiptRow.copy(fontSize = 11.sp, color = c.inkFaint, letterSpacing = 1.sp))
            LineKind.CATEGORY -> TwoEnds(
                line, box, c.ink,
                ReceiptRow.copy(fontWeight = FontWeight.Bold, color = c.ink),
                leftStyle = ReceiptRow.copy(fontWeight = FontWeight.Bold, color = c.ink),
            )
            LineKind.ITEM, LineKind.INCOME -> ItemRow(line, box, c)
            LineKind.SHARE_BAR -> ShareBar(line, box, c)
            LineKind.BARCODE -> Barcode(line, box, c)
            else -> Centered(line, box, c)
        }
    }
}

@Composable
private fun Centered(line: ReceiptLine, modifier: Modifier, c: PaperColors) {
    val style = when (line.kind) {
        LineKind.TITLE -> ReceiptRow.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp, color = c.ink)
        LineKind.SUBTITLE -> ReceiptRow.copy(fontSize = 12.sp, color = c.inkSoft)
        LineKind.DEMO -> ReceiptRow.copy(fontSize = 11.sp, color = c.inkSoft, fontWeight = FontWeight.Bold)
        LineKind.NOTICE -> ReceiptRow.copy(fontSize = 11.sp, color = c.inkFaint)
        LineKind.BIG -> ReceiptRow.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = c.ink)
        LineKind.FOOTER -> ReceiptRow.copy(fontSize = 11.sp, color = c.inkFaint, letterSpacing = 0.5.sp)
        LineKind.COMMENT -> if (LocalReceiptFont.current == ReceiptFont.Thermal) ReceiptHandwriting.copy(color = c.ink) else ReceiptRow.copy(fontSize = 13.sp, color = c.ink)
        LineKind.MORE -> ReceiptRow.copy(fontSize = 11.sp, color = c.inkSoft)
        else -> ReceiptRow.copy(color = c.ink)
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
private fun ItemRow(line: ReceiptLine, modifier: Modifier, c: PaperColors) {
    val income = line.kind == LineKind.INCOME
    val color = if (line.positive) XpColors.Positive else c.inkSoft
    Row(
        modifier.padding(start = if (line.kind == LineKind.ITEM) 18.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            line.left, style = ReceiptRow.copy(fontSize = 12.sp, color = if (income) color else c.inkSoft),
            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Text(line.right, style = ReceiptRow.copy(fontSize = 12.sp, color = if (line.positive) color else c.ink), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun StatsRow(line: ReceiptLine, modifier: Modifier, c: PaperColors) {
    val style = ReceiptRow.copy(fontSize = 11.sp, color = c.inkSoft, textAlign = TextAlign.Center)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(line.left, style = style, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false)
        Text(line.right, style = style, modifier = Modifier.weight(1.35f), maxLines = 1, softWrap = false)
        Text("本周第 ${line.extra} 天", style = style, modifier = Modifier.weight(1.1f), maxLines = 1, softWrap = false)
    }
}

/** 今日支出 caption + a big centred total whose digits roll once the line reaches the slot. */
@Composable
private fun TotalBlock(line: ReceiptLine, step: PrintStep, clock: PrintClock, modifier: Modifier, c: PaperColors) {
    val started by remember(step, clock) { derivedStateOf { clock.now() >= step.startMs } }
    val digits = line.right.removePrefix("¥")
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(line.left, style = ReceiptRow.copy(fontSize = 12.sp, color = c.inkSoft, letterSpacing = 3.sp))
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                Text("¥", style = ReceiptRow.copy(fontSize = 20.sp, color = c.inkSoft, fontWeight = FontWeight.Medium), modifier = Modifier.padding(end = 3.dp, bottom = 5.dp))
                // Reserve the final width so the number never shifts while rolling; the ticker appears in place.
                Box {
                    Text(digits, style = bigTotalStyle().copy(color = Color.Transparent))
                    if (started) NumberTicker(text = digits, style = bigTotalStyle(), color = c.ink, rollFromZero = !clock.instant)
                }
            }
        }
    }
}

@Composable
@ReadOnlyComposable
private fun bigTotalStyle() = TextStyle(
    fontFamily = ReceiptRow.fontFamily, fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp,
    fontFeatureSettings = "tnum",
)

@Composable
private fun DashLine(modifier: Modifier, c: PaperColors) {
    Canvas(modifier) {
        val y = size.height / 2f
        drawLine(
            c.inkFaint, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
    }
}

/** One of the top categories: label, a proportional bar in ink, and the percentage (简版 / 分类占比条). */
@Composable
private fun ShareBar(line: ReceiptLine, modifier: Modifier, c: PaperColors) {
    val label = ReceiptRow.copy(fontSize = 12.sp, color = c.inkSoft)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(line.left, style = label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.95f))
        Canvas(Modifier.weight(1.4f).height(7.dp)) {
            drawRect(c.ink.copy(alpha = 0.14f))
            drawRect(c.ink.copy(alpha = 0.82f), size = androidx.compose.ui.geometry.Size(size.width * (line.extra / 100f).coerceIn(0f, 1f), size.height))
        }
        Text(line.right, style = ReceiptRow.copy(fontSize = 12.sp, color = c.ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.End), maxLines = 1, softWrap = false, modifier = Modifier.width(38.dp))
    }
}

/** Code-39-ish stripes derived from the serial, so every receipt has its own. Not scannable; it is paper dressing. */
@Composable
private fun Barcode(line: ReceiptLine, modifier: Modifier, c: PaperColors) {
    androidx.compose.foundation.layout.Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.width(170.dp).height(26.dp)) {
            var x = 0f
            var i = 0
            val unit = 1.5.dp.toPx()
            val bits = line.left.flatMap { ch -> (0 until 7).map { b -> ((ch.code shr b) and 1) == 1 } }
            while (x < size.width) {
                val wide = bits[i % bits.size]
                val w = unit * (if (wide) 2.2f else 1f)
                if (i % 2 == 0) drawRect(c.ink.copy(alpha = 0.9f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(minOf(w, size.width - x), size.height))
                x += w + 0.4f * unit
                i++
            }
        }
        Text(line.left, style = ReceiptRow.copy(fontSize = 9.sp, color = c.inkFaint, letterSpacing = 2.sp), maxLines = 1, softWrap = false)
    }
}
