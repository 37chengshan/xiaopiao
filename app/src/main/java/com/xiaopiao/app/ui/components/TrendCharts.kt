package com.xiaopiao.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.insights.StatsBucket
import com.xiaopiao.app.insights.StatsEntry
import com.xiaopiao.app.insights.StatsAggregator
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpFonts
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 周报柱状图 (docs/APP-DESIGN.md §3.7): 20dp bars, 4dp top corners only, value above the bar, two-line
 * period label below, current period in accent and the rest at 30%. No grid, no Y axis, no library.
 */
@Composable
fun BarTrend(
    buckets: List<StatsBucket>,
    modifier: Modifier = Modifier,
    barWidth: Dp = 20.dp,
    emptyText: String = "这段时间还没有账单",
) {
    if (buckets.isEmpty()) return
    val reduceMotion = rememberReducedMotion()
    val progress = remember(buckets) { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(buckets) {
        if (reduceMotion) progress.snapTo(1f) else {
            progress.snapTo(0f)
            // Staggered growth: the last bar finishes ~180ms after the first one.
            progress.animateTo(1f, tween(240 + buckets.size * 45, easing = XpEase.out))
        }
    }
    val measurer = rememberTextMeasurer()
    val valueStyle = remember {
        TextStyle(fontFamily = XpFonts.sans, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
    }
    val labelStyle = remember { TextStyle(fontFamily = XpFonts.sans, fontSize = 11.sp) }

    Canvas(modifier.fillMaxWidth().height(190.dp)) {
        val w = size.width
        val topPad = 22.dp.toPx()
        // Room for the two-line period label at whatever font scale is active (docs/DESIGN.md §10).
        val labelLine = measurer.measure(AnnotatedString("0"), labelStyle).size.height.toFloat()
        val labelBlock = 8.dp.toPx() + labelLine * 2 + 2.dp.toPx()
        val baseline = size.height - labelBlock
        val plotH = baseline - topPad
        val maxCents = buckets.maxOf { it.cents }

        if (maxCents <= 0L) {
            // Empty state: one dashed baseline and a line of explanation, nothing else.
            drawLine(
                XpColors.Hairline, Offset(0f, baseline), Offset(w, baseline),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
            )
            val text = measurer.measure(AnnotatedString(emptyText), labelStyle.copy(color = XpColors.InkTertiary))
            drawText(text, topLeft = Offset((w - text.size.width) / 2f, baseline - 26.dp.toPx()))
            return@Canvas
        }

        val slot = w / buckets.size
        val fitW = slot - 4.dp.toPx()
        val bw = min(barWidth.toPx(), slot * 0.55f)
        val radius = 4.dp.toPx()

        buckets.forEachIndexed { i, bucket ->
            val cx = slot * i + slot / 2f
            val local = ((progress.value * (buckets.size + 2) - i) / 1.8f).coerceIn(0f, 1f)
            val h = (bucket.cents.toFloat() / maxCents * plotH) * local
            val left = cx - bw / 2f
            val top = baseline - h
            if (h < 0.5f) return@forEachIndexed

            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, top, left + bw, baseline),
                        topLeft = CornerRadius(radius),
                        topRight = CornerRadius(radius),
                        bottomRight = CornerRadius.Zero,
                        bottomLeft = CornerRadius.Zero,
                    ),
                )
            }
            val color = if (bucket.isCurrent) XpColors.Accent else XpColors.Accent.copy(alpha = 0.3f)
            drawPath(path, color)

            if (local > 0.6f) {
                val value = measurer.fit(
                    StatsAggregator.grouped(bucket.cents),
                    valueStyle.copy(color = if (bucket.isCurrent) XpColors.Accent else XpColors.InkSecondary),
                    fitW,
                )
                drawText(value, topLeft = Offset(cx - value.size.width / 2f, top - value.size.height - 4.dp.toPx()))
            }

            val topLabel = measurer.fit(
                bucket.top,
                labelStyle.copy(color = if (bucket.isCurrent) XpColors.Accent else XpColors.InkTertiary),
                fitW,
            )
            drawText(topLabel, topLeft = Offset(cx - topLabel.size.width / 2f, baseline + 8.dp.toPx()))
            if (bucket.bottom.isNotEmpty()) {
                val bottomLabel = measurer.fit(
                    bucket.bottom,
                    labelStyle.copy(color = if (bucket.isCurrent) XpColors.Accent else XpColors.InkTertiary),
                    fitW,
                )
                drawText(bottomLabel, topLeft = Offset(cx - bottomLabel.size.width / 2f, baseline + 8.dp.toPx() + labelLine + 2.dp.toPx()))
            }
        }
    }
}

/**
 * 月报 / 年报 / 自定义 折线面积图 (§3.7): 88dp plot, 2dp accent line, accent 18% → 0 area, vertical grid,
 * a permanent tooltip on the current point with a dashed drop line.
 */
@Composable
fun LineTrend(
    buckets: List<StatsBucket>,
    tickEvery: Int = 1,
    modifier: Modifier = Modifier,
    highlightIndex: Int = -1,
    emptyText: String = "这段时间还没有账单",
) {
    if (buckets.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val axisStyle = remember { TextStyle(fontFamily = XpFonts.sans, fontSize = 11.sp) }
    val bubbleStyle = remember {
        TextStyle(fontFamily = XpFonts.sans, fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum", color = Color.White)
    }
    val highlight = when {
        highlightIndex in buckets.indices -> highlightIndex
        else -> buckets.indexOfLast { it.cents > 0 }.coerceAtLeast(0)
    }

    // 46dp tooltip band + 88dp plot + 22dp labels (§3.7).
    Canvas(modifier.fillMaxWidth().height(156.dp)) {
        val w = size.width
        val bubbleBlock = 46.dp.toPx()
        val labelBlock = 22.dp.toPx()
        val top = bubbleBlock
        val baseline = size.height - labelBlock
        val plotH = baseline - top
        val inset = 8.dp.toPx()
        val step = if (buckets.size > 1) (w - inset * 2) / (buckets.size - 1) else 0f
        val maxCents = buckets.maxOf { it.cents }

        fun xAt(i: Int) = inset + step * i
        fun yAt(cents: Long) = if (maxCents <= 0) baseline else baseline - cents.toFloat() / maxCents * plotH

        if (maxCents <= 0L) {
            drawLine(
                XpColors.Hairline, Offset(0f, baseline), Offset(w, baseline),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
            )
            val text = measurer.measure(AnnotatedString(emptyText), axisStyle.copy(color = XpColors.InkTertiary))
            drawText(text, topLeft = Offset((w - text.size.width) / 2f, baseline - 26.dp.toPx()))
            return@Canvas
        }

        // Vertical grid only — the numbers live in the tooltip, so no Y axis is drawn.
        buckets.forEachIndexed { i, _ ->
            if (i % tickEvery == 0) {
                drawLine(
                    XpColors.Hairline.copy(alpha = 0.6f), Offset(xAt(i), top), Offset(xAt(i), baseline),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        val line = Path()
        buckets.forEachIndexed { i, b ->
            val x = xAt(i)
            val y = yAt(b.cents)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(xAt(buckets.lastIndex), baseline)
            lineTo(xAt(0), baseline)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(
                colors = listOf(XpColors.Accent.copy(alpha = 0.18f), XpColors.Accent.copy(alpha = 0f)),
                startY = top,
                endY = baseline,
            ),
        )
        drawPath(line, XpColors.Accent, style = Stroke(width = 2.dp.toPx()))

        // X labels every n-th bucket.
        buckets.forEachIndexed { i, b ->
            if (i % tickEvery == 0) {
                val layout = measurer.measure(AnnotatedString(b.top), axisStyle.copy(color = XpColors.InkTertiary))
                val x = (xAt(i) - layout.size.width / 2f).coerceIn(0f, w - layout.size.width)
                drawText(layout, topLeft = Offset(x, baseline + 6.dp.toPx()))
            }
        }

        // Current point + tooltip, dashed drop line included.
        val hx = xAt(highlight)
        val hy = yAt(buckets[highlight].cents)
        drawLine(
            XpColors.Accent.copy(alpha = 0.4f), Offset(hx, hy + 6.dp.toPx()), Offset(hx, baseline),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
        drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(hx, hy))
        drawCircle(XpColors.Accent, radius = 5.dp.toPx(), center = Offset(hx, hy), style = Stroke(width = 2.dp.toPx()))

        val amount = measurer.measure(AnnotatedString("¥ ${StatsAggregator.grouped(buckets[highlight].cents)}"), bubbleStyle)
        val date = measurer.measure(AnnotatedString(buckets[highlight].top), bubbleStyle.copy(color = Color.White.copy(alpha = 0.85f)))
        val padH = 8.dp.toPx()
        val padV = 6.dp.toPx()
        val bubbleW = maxOf(amount.size.width, date.size.width) + padH * 2
        val bubbleH = amount.size.height + date.size.height + padV * 2 + 2.dp.toPx()
        val bubbleX = (hx - bubbleW / 2f).coerceIn(0f, w - bubbleW)
        val bubbleY = (hy - bubbleH - 10.dp.toPx()).coerceAtLeast(0f)
        drawRoundRect(
            XpColors.Accent,
            topLeft = Offset(bubbleX, bubbleY),
            size = Size(bubbleW, bubbleH),
            cornerRadius = CornerRadius(10.dp.toPx()),
        )
        drawText(date, topLeft = Offset(bubbleX + padH, bubbleY + padV))
        drawText(amount, topLeft = Offset(bubbleX + padH, bubbleY + padV + date.size.height + 2.dp.toPx()))
    }
}

/**
 * 分类构成环形图: 50% inner radius, 2dp gaps, monochrome accent ramp (no rainbow — §3.7 否决彩色图标),
 * center shows the selected slice's amount, leader lines carry colour while the label text stays gray.
 */
@Composable
fun DonutChart(
    entries: List<StatsEntry>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(fontFamily = XpFonts.sans, fontSize = 12.sp) }
    val centerStyle = remember {
        TextStyle(fontFamily = XpFonts.sans, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    }
    val unitStyle = remember { TextStyle(fontFamily = XpFonts.sans, fontSize = 11.sp) }
    val slices = entries.take(5)
    val total = slices.sumOf { it.cents }

    Canvas(
        modifier.fillMaxWidth().height(196.dp).pointerInput(slices) {
            detectTapGestures { offset ->
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = donutOuter(size.width.toFloat(), size.height.toFloat())
                val dx = offset.x - cx
                val dy = offset.y - cy
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist < r * 0.45f || dist > r * 1.45f || total <= 0) return@detectTapGestures
                var angle = Math.toDegrees(atan2(dy, dx).toDouble()) + 90.0   // 0° = 12 o'clock
                if (angle < 0) angle += 360
                var acc = 0f
                slices.forEachIndexed { i, e ->
                    val sweep = (e.cents.toFloat() / total) * 360f
                    if (angle >= acc && angle < acc + sweep) { onSelect(i); return@detectTapGestures }
                    acc += sweep
                }
            }
        },
    ) {
        // Centred ring sized to the card, so both label columns keep real width at 360dp × 1.3.
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = donutOuter(size.width, size.height)
        val inner = outer * 0.6f
        val stroke = outer - inner
        val r = (outer + inner) / 2f

        if (total <= 0L) {
            drawCircle(XpColors.Fill, radius = r, center = Offset(cx, cy), style = Stroke(width = stroke))
            val layout = measurer.measure(AnnotatedString("¥ 0"), centerStyle.copy(color = XpColors.InkTertiary))
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
            return@Canvas
        }

        val gap = 2.dp.toPx()
        var startAngle = -90f
        val labelAnchors = mutableListOf<Triple<Float, Float, Int>>()   // x, y, index

        slices.forEachIndexed { i, e ->
            val sweep = (e.cents.toFloat() / total) * 360f
            val color = XpColors.Accent.copy(alpha = 1f - i * 0.18f)
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = (sweep - (gap / (2 * Math.PI * r).toFloat() * 360f)).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = stroke),
            )
            val mid = Math.toRadians((startAngle + sweep / 2).toDouble())
            labelAnchors += Triple(
                cx + (outer + 6.dp.toPx()) * cos(mid).toFloat(),
                cy + (outer + 6.dp.toPx()) * sin(mid).toFloat(),
                i,
            )
            startAngle += sweep
        }

        // Leader lines + two-line labels (name over share), spread vertically so neighbours never collide,
        // and shrunk to the column width instead of running into the ring.
        val sideW = cx - outer - 26.dp.toPx()
        val nameStyle = labelStyle.copy(color = XpColors.InkSecondary)
        val shareStyle = labelStyle.copy(color = XpColors.InkTertiary, fontFeatureSettings = "tnum")
        fun labelOf(e: StatsEntry): Pair<TextLayoutResult, TextLayoutResult> {
            // Percent is relative to the five slices actually drawn, so the labels add up to the ring.
            val share = (e.cents.toFloat() / total * 100).toInt()
            return measurer.fit(e.label, nameStyle, sideW) to measurer.fit("$share%", shareStyle, sideW)
        }
        val sample = labelOf(slices.first())
        val minGap = sample.first.size.height + sample.second.size.height + 4.dp.toPx()
        val left = labelAnchors.filter { it.first < cx }.sortedBy { it.second }
        val right = labelAnchors.filter { it.first >= cx }.sortedBy { it.second }
        fun spread(list: List<Triple<Float, Float, Int>>): List<Pair<Triple<Float, Float, Int>, Float>> {
            val out = mutableListOf<Pair<Triple<Float, Float, Int>, Float>>()
            var lastY = -Float.MAX_VALUE
            list.forEach { a ->
                val y = maxOf(a.second, lastY + minGap, minGap / 2f)
                out += a to y
                lastY = y
            }
            // If the column ran off the bottom, slide it back up as one block.
            val overflow = (out.lastOrNull()?.second ?: 0f) - (size.height - minGap / 2f)
            return if (overflow > 0f) out.map { (a, y) -> a to y - overflow } else out
        }
        fun drawLabel(e: StatsEntry, y: Float, alignEnd: Boolean, x: Float) {
            val (name, share) = labelOf(e)
            val top = y - (name.size.height + share.size.height) / 2f
            drawText(name, topLeft = Offset(if (alignEnd) x - name.size.width else x, top))
            drawText(share, topLeft = Offset(if (alignEnd) x - share.size.width else x, top + name.size.height))
        }

        spread(left).forEach { (anchor, y) ->
            val e = slices[anchor.third]
            val color = XpColors.Accent.copy(alpha = 1f - anchor.third * 0.18f)
            val lineEnd = cx - outer - 12.dp.toPx()
            val path = Path().apply {
                moveTo(anchor.first, anchor.second)
                lineTo(lineEnd, y)
                lineTo(lineEnd - 8.dp.toPx(), y)
            }
            drawPath(path, color, style = Stroke(width = 1.dp.toPx()))
            drawLabel(e, y, alignEnd = true, x = lineEnd - 12.dp.toPx())
        }
        spread(right).forEach { (anchor, y) ->
            val e = slices[anchor.third]
            val color = XpColors.Accent.copy(alpha = 1f - anchor.third * 0.18f)
            val lineEnd = cx + outer + 12.dp.toPx()
            val path = Path().apply {
                moveTo(anchor.first, anchor.second)
                lineTo(lineEnd, y)
                lineTo(lineEnd + 8.dp.toPx(), y)
            }
            drawPath(path, color, style = Stroke(width = 1.dp.toPx()))
            drawLabel(e, y, alignEnd = false, x = lineEnd + 12.dp.toPx())
        }

        // Center: the selected slice's amount (reference behaviour), label under it.
        val current = slices[selected.coerceIn(0, slices.lastIndex)]
        // The centre stays inside the hole: shrink rather than spill onto the ring.
        val hole = inner * 2f - 10.dp.toPx()
        val amount = measurer.fit(StatsAggregator.grouped(current.cents), centerStyle.copy(color = XpColors.Ink), hole, minSp = 12f)
        val unit = measurer.fit(current.label, unitStyle.copy(color = XpColors.InkSecondary), hole)
        drawText(amount, topLeft = Offset(cx - amount.size.width / 2f, cy - amount.size.height / 2f - 6.dp.toPx()))
        drawText(unit, topLeft = Offset(cx - unit.size.width / 2f, cy + amount.size.height / 2f - 4.dp.toPx()))
    }
}

/** Ring radius: the old 34% of the height, capped so each label column keeps ~30% of the width. */
private fun donutOuter(width: Float, height: Float): Float = min(height * 0.34f, width * 0.2f)

/**
 * Canvas text that steps its size down (to [minSp]) until it fits [maxWidth], then ellipsizes — the
 * chart's equivalent of `TextAutoSize` (docs/DESIGN.md §10), so labels never run into each other.
 */
private fun TextMeasurer.fit(text: String, style: TextStyle, maxWidth: Float, minSp: Float = 8f): TextLayoutResult {
    var sp = style.fontSize.value
    var layout = measure(AnnotatedString(text), style, maxLines = 1, softWrap = false)
    while (layout.size.width > maxWidth && sp > minSp) {
        sp = (sp - 0.5f).coerceAtLeast(minSp)
        layout = measure(AnnotatedString(text), style.copy(fontSize = sp.sp), maxLines = 1, softWrap = false)
    }
    if (layout.size.width <= maxWidth || maxWidth < 1f) return layout
    return measure(
        AnnotatedString(text), style.copy(fontSize = sp.sp), overflow = TextOverflow.Ellipsis, maxLines = 1,
        constraints = Constraints(maxWidth = maxWidth.toInt()),
    )
}
