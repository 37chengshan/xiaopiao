package com.xiaopiao.app.ui.today

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors

val PrinterHeight: Dp = 78.dp

/** Stylised printer: dark rounded body, status LED and a glowing slit the paper feeds out of. */
@Composable
fun ReceiptPrinter(printing: Boolean, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "led").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (printing) 260 else 1500), RepeatMode.Reverse),
        label = "ledPulse",
    )
    Canvas(modifier.width(PaperWidth + 44.dp).height(PrinterHeight).fillMaxWidth()) {
        val w = size.width
        val h = size.height
        val body = CornerRadius(22.dp.toPx())
        drawRoundRect(Brush.verticalGradient(listOf(XpColors.PrinterHighlight, XpColors.PrinterBody)), Offset.Zero, size, body)
        drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(1.dp.toPx(), 1.dp.toPx()), Size(w - 2.dp.toPx(), h * 0.45f), body)
        // slit
        val slitW = PaperWidth.toPx() + 14.dp.toPx()
        val slitH = 9.dp.toPx()
        val slitTop = h - 20.dp.toPx()
        val slitLeft = (w - slitW) / 2f
        drawRoundRect(XpColors.PrinterSlit, Offset(slitLeft, slitTop), Size(slitW, slitH), CornerRadius(slitH / 2f))
        if (printing) {
            drawRect(
                Brush.verticalGradient(listOf(XpColors.Accent.copy(alpha = 0.55f), Color.Transparent), startY = slitTop + slitH, endY = slitTop + slitH + 26.dp.toPx()),
                Offset(slitLeft + 6.dp.toPx(), slitTop + slitH), Size(slitW - 12.dp.toPx(), 26.dp.toPx()),
            )
        }
        drawRoundRect(
            XpColors.Accent.copy(alpha = if (printing) 0.9f else 0.35f),
            Offset(slitLeft + 4.dp.toPx(), slitTop + slitH / 2f - 1.dp.toPx()), Size(slitW - 8.dp.toPx(), 2.dp.toPx()), CornerRadius(1.dp.toPx()),
        )
        // LED + vents
        val ledC = Offset(w - 34.dp.toPx(), 24.dp.toPx())
        drawCircle(XpColors.Accent.copy(alpha = pulse * 0.35f), 9.dp.toPx(), ledC)
        drawCircle(if (printing) XpColors.Accent else XpColors.Positive.copy(alpha = 0.5f + pulse * 0.5f), 4.dp.toPx(), ledC)
        repeat(4) {
            drawRoundRect(
                Color.Black.copy(alpha = 0.35f), Offset(30.dp.toPx() + it * 10.dp.toPx(), 20.dp.toPx()),
                Size(6.dp.toPx(), 3.dp.toPx()), CornerRadius(1.5.dp.toPx()),
            )
        }
    }
}
