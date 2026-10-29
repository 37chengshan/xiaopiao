package com.xiaopiao.app.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors

private val StripW = 28.dp
private val StripH = 36.dp
private val LipH = 4.dp
private val ToothDp = 4.dp
/** How far the paper retracts into the slot before it is spat out again when today's number changes. */
private val Retract = 3.dp

/**
 * The resident receipt: a 28×36dp scrap of paper hanging out of a tiny printer lip, today's spend printed on it.
 * Tap = print today (the ceremony), long-press = the 小票页. When today's number changes the paper slips back
 * into the lip and is spat out again. Lives in the top-right of every root page, left of the gear
 * (docs/APP-DESIGN.md §3.1); pages only place it, all behaviour comes from [LocalReceiptHost].
 */
@Composable
fun ReceiptStrip(modifier: Modifier = Modifier) {
    val host = LocalReceiptHost.current
    val haptics = rememberXpHaptics()
    val reduced = rememberReducedMotion()
    val colors = host?.settings?.colors() ?: PaperColors.resolve(PaperTheme.Classic, null)
    val cents = host?.todayCents ?: 0L
    val text = remember(cents) { StripText.format(cents) }

    // Spit-out: retract then spring back, only when the number actually changes (not on first composition).
    val slide = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    val retractPx = with(androidx.compose.ui.platform.LocalDensity.current) { Retract.toPx() }
    LaunchedEffect(cents) {
        if (first) { first = false; return@LaunchedEffect }
        if (reduced) return@LaunchedEffect
        slide.snapTo(-retractPx)
        slide.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 420f))
    }

    var bounds by remember { mutableStateOf<Rect?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val press by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.92f else 1f, spring(0.55f, 450f), label = "stripPress")
    val centre = { bounds?.center }

    Box(
        modifier
            .size(40.dp, 44.dp)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .semantics {
                contentDescription = "今日小票 $text，点按打印，长按打开小票页"
                onClick("打印今日小票") { host?.print(from = centre()); true }
                onLongClick("打开小票页") { host?.openReceiptPage(); true }
            }
            .pointerInput(host) {
                if (host == null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val long = awaitLongPressOrCancellation(down.id)
                    if (long == null) {
                        pressed = false
                        haptics.select()
                        host.print(from = centre())
                    } else {
                        haptics.thud()
                        host.openReceiptPage()
                        waitForUpOrCancellation()
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(StripW, StripH + LipH).graphicsLayer { scaleX = press; scaleY = press }.clipToBounds(),
        ) {
            Paper(text, colors, Modifier.offset(y = LipH - 1.dp).graphicsLayer { translationY = slide.value })
            // The printer lip sits over the top of the paper, so the paper reads as coming out of it.
            Canvas(Modifier.size(StripW, LipH + 2.dp)) {
                drawRoundRect(XpColors.PrinterBody, Offset.Zero, Size(size.width, LipH.toPx()), CornerRadius(2.dp.toPx()))
                drawRect(XpColors.PrinterSlit, Offset(2.dp.toPx(), LipH.toPx() - 1.4.dp.toPx()), Size(size.width - 4.dp.toPx(), 1.4.dp.toPx()))
                drawRect(Color.White.copy(alpha = 0.18f), Offset(2.dp.toPx(), 0.6.dp.toPx()), Size(size.width - 4.dp.toPx(), 0.6.dp.toPx()))
            }
        }
    }
}

/** The paper scrap itself: warm sheet, saw-tooth bottom, three faint lines and the amount in mono. */
@Composable
private fun Paper(text: String, c: PaperColors, modifier: Modifier) {
    Box(modifier.size(StripW, StripH)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val tooth = ToothDp.toPx()
            val n = (w / tooth).toInt().coerceAtLeast(1)
            val step = w / n
            val path = Path().apply {
                moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h - tooth)
                for (i in n downTo 1) { lineTo(step * i - step / 2f, h); lineTo(step * (i - 1), h - tooth) }
                close()
            }
            translate(top = 1.2.dp.toPx()) { drawPath(path, Color.Black.copy(alpha = 0.10f)) }
            drawPath(path, Brush.verticalGradient(listOf(c.paper, androidx.compose.ui.graphics.lerp(c.paper, c.shade, 0.7f))))
            val line = c.ink.copy(alpha = 0.16f)
            drawRect(line, Offset(w * 0.22f, 7.dp.toPx()), Size(w * 0.56f, 1.dp.toPx()))
            drawRect(line, Offset(w * 0.22f, 10.dp.toPx()), Size(w * 0.36f, 1.dp.toPx()))
            drawRect(c.ink.copy(alpha = 0.10f), Offset(w * 0.12f, 14.5.dp.toPx()), Size(w * 0.76f, 0.6.dp.toPx()))
        }
        // The scrap is a fixed 28dp drawing, so its print does not grow with the system font scale.
        val density = androidx.compose.ui.platform.LocalDensity.current
        Text(
            text,
            modifier = Modifier.align(Alignment.Center).padding(top = 8.dp),
            style = TextStyle(
                fontFamily = FontFamily.Monospace, fontSize = with(density) { 7.dp.toSp() }, lineHeight = with(density) { 8.dp.toSp() },
                fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center,
                letterSpacing = with(density) { (-0.3).dp.toSp() }, fontFeatureSettings = "tnum",
            ),
            maxLines = 1, softWrap = false,
        )
    }
}
