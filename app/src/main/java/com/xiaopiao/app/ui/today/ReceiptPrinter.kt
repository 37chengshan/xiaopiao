package com.xiaopiao.app.ui.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors

val PrinterHeight = 128.dp
/** Where the slot's centre line sits from the top of the printer; the paper below tucks under it. */
val PrinterSlotCentre = 112.dp
private val SlotH = 14.dp

/**
 * 拟物打印机 (docs/APP-DESIGN.md §3.1): plastic body with a lit top edge, a recessed paper slot with an inner
 * shadow and a bright lip, an LED that blinks while printing, a small LCD with the chosen day, and a raised
 * print key that sinks when pressed. Everything is drawn procedurally; no images. The caller places the receipt
 * preview so that it starts under the slot ([PrinterSlotCentre]).
 */
@Composable
fun ReceiptPrinter(
    lcd: String,
    printing: Boolean,
    onPrint: (from: Offset?) -> Unit,
    modifier: Modifier = Modifier,
    /** True when the paper rises out of a slot on the printer's top edge (the 小票 tab sits it in the lower half). */
    slotAtTop: Boolean = false,
) {
    val dark = XpColors.isDark
    val haptics = rememberXpHaptics()
    // LED blink phase, advanced by the frame clock (dt), not by a fixed timer.
    var blink by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(printing) {
        if (!printing) { blink = 0f; return@LaunchedEffect }
        var last = 0L
        var t = 0f
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) t += (now - last) / 1_000_000_000f
                last = now
            }
            blink = if ((t * 3.2f) % 1f < 0.5f) 1f else 0.25f
        }
    }
    var keyCentre by remember { mutableStateOf<Offset?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val sink by animateFloatAsState(if (pressed) 1f else 0f, spring(0.6f, 700f), label = "keySink")

    Box(
        modifier.fillMaxWidth().height(PrinterHeight).drawBehind { drawBody(dark, printing, blink, slotAtTop) },
    ) {
        // LCD: day label in green-on-black mono.
        Box(
            Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = if (slotAtTop) 48.dp else 28.dp).size(104.dp, 28.dp)
                .drawBehind {
                    drawRoundRect(Color(0xFF0E1A12), cornerRadius = CornerRadius(6.dp.toPx()))
                    drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(1.dp.toPx(), 1.dp.toPx()), Size(size.width - 2.dp.toPx(), size.height * 0.45f), CornerRadius(5.dp.toPx()))
                    drawRoundRect(Color.Black.copy(alpha = 0.5f), cornerRadius = CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                lcd, maxLines = 1, softWrap = false,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7CFFA0), letterSpacing = 0.5.sp),
            )
        }
        // Print key.
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 28.dp, top = if (slotAtTop) 42.dp else 22.dp).size(52.dp)
                .onGloballyPositioned { keyCentre = it.boundsInWindow().center }
                .semantics {
                    contentDescription = "打印键：按当前设置打印所选日期的小票"
                    onClick("打印") { onPrint(keyCentre); true }
                }
                .pointerInput(printing) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        val up = waitForUpOrCancellation()
                        pressed = false
                        if (up != null && !printing) { haptics.thud(); onPrint(keyCentre) }
                    }
                }
                .drawBehind { drawKey(sink, dark) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Print, contentDescription = null, tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(22.dp).graphicsLayer { translationY = sink * 1.5.dp.toPx() },
            )
        }
    }
}

private fun DrawScope.drawBody(dark: Boolean, printing: Boolean, blink: Float, slotAtTop: Boolean) {
    val d = density
    val bodyH = size.height - 10 * d
    val r = CornerRadius(26 * d)
    val top = if (dark) Color(0xFF48484A) else Color(0xFFF6F5F1)
    val bottom = if (dark) Color(0xFF2A2A2C) else Color(0xFFD9D6CD)
    // Grounding shadow: soft copies of the body, growing and fading downward.
    for (i in 1..7) drawRoundRect(Color.Black.copy(alpha = 0.045f), Offset(i * 0.6f * d, i * 1.3f * d), Size(size.width - i * 1.2f * d, bodyH), r)
    drawRoundRect(Brush.verticalGradient(listOf(top, bottom), startY = 0f, endY = bodyH), Offset.Zero, Size(size.width, bodyH), r)
    // Lit top edge and a dark bottom rim give the plastic its thickness.
    drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.9f), Color.Transparent), endY = 36 * d), Offset(1.5f * d, 1.5f * d), Size(size.width - 3 * d, bodyH - 3 * d), r, style = Stroke(1.6f * d))
    drawRoundRect(Color.Black.copy(alpha = 0.10f), Offset.Zero, Size(size.width, bodyH), r, style = Stroke(1f * d))
    // Vents.
    val vent = Color.Black.copy(alpha = if (dark) 0.5f else 0.14f)
    val ventY = if (slotAtTop) 86f else 66f
    for (i in 0 until 4) drawRoundRect(vent, Offset(22 * d, (ventY + i * 6) * d), Size(52 * d, 2.2f * d), CornerRadius(1.1f * d))
    // LED: green when ready, amber blinking while printing; with a soft glow.
    val led = if (printing) Color(0xFFFFB020) else Color(0xFF34C759)
    val a = if (printing) blink else 1f
    val c = Offset(size.width - (if (slotAtTop) 112 else 60) * d, (if (slotAtTop) 38 else 18) * d)
    drawCircle(Brush.radialGradient(listOf(led.copy(alpha = 0.45f * a), Color.Transparent), c, 12 * d), 12 * d, c)
    drawCircle(Color.Black.copy(alpha = 0.35f), 4.6f * d, c)
    drawCircle(led.copy(alpha = a), 3.6f * d, c)
    drawCircle(Color.White.copy(alpha = 0.6f * a), 1.1f * d, c + Offset(-1.1f * d, -1.1f * d))
    // Paper slot: recessed, dark, with an inner shadow under the top edge and a bright lip below.
    val slotW = size.width - 32 * d
    val slotY = (if (slotAtTop) 8 else 105) * d
    val sx = (size.width - slotW) / 2f
    val sr = CornerRadius(7 * d)
    drawRoundRect(Color(0xFF111113), Offset(sx, slotY), Size(slotW, 14 * d), sr)
    drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent), startY = slotY, endY = slotY + 9 * d), Offset(sx, slotY), Size(slotW, 14 * d), sr)
    val lipY = if (slotAtTop) slotY + 15 * d else slotY + 14 * d
    drawRoundRect(Color.White.copy(alpha = if (dark) 0.16f else 0.7f), Offset(sx + 6 * d, lipY), Size(slotW - 12 * d, 1.4f * d), CornerRadius(0.7f * d))
}

private fun DrawScope.drawKey(sink: Float, dark: Boolean) {
    val d = density
    val c = center
    val r = size.minDimension / 2f
    // Well the key sits in.
    drawCircle(Color.Black.copy(alpha = if (dark) 0.5f else 0.16f), r, c)
    drawCircle(Color.White.copy(alpha = if (dark) 0.10f else 0.8f), r, c + Offset(0f, 1f * d), style = Stroke(1f * d))
    // The cap: rises 2.5dp when idle, flat when pressed.
    val lift = (1f - sink) * 2.5f * d
    val capR = r - 4 * d
    val cc = c + Offset(0f, -lift + sink * 1f * d)
    drawCircle(Color.Black.copy(alpha = 0.30f * (1f - sink)), capR, cc + Offset(0f, lift))
    drawCircle(Brush.verticalGradient(listOf(Color(0xFF3A3A3E), Color(0xFF151517)), cc.y - capR, cc.y + capR), capR, cc)
    drawCircle(Color.White.copy(alpha = 0.28f), capR, cc, style = Stroke(1f * d))
    drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), cc.y - capR, cc.y), capR - 2 * d, cc)
}
