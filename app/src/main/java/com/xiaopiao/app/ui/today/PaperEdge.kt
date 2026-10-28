package com.xiaopiao.app.ui.today

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.xiaopiao.app.ui.theme.XpColors
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.Random

/** Receipt paper geometry (px): zigzag bottom, optional zigzag top for a freshly torn sheet. */
fun paperPath(w: Float, h: Float, tooth: Float, depth: Float, tornTop: Boolean): Path {
    val n = max(1, (w / tooth).roundToInt())
    val step = w / n
    return Path().apply {
        moveTo(0f, if (tornTop) depth else 0f)
        if (tornTop) {
            for (i in 0 until n) {
                lineTo(step * i + step / 2f, 0f)
                lineTo(step * (i + 1), depth)
            }
        } else lineTo(w, 0f)
        val base = max(depth, h - depth)
        lineTo(w, base)
        for (i in n downTo 1) {
            lineTo(step * i - step / 2f, h.coerceAtLeast(depth))
            lineTo(step * (i - 1), base)
        }
        close()
    }
}

private class Fibre(val x: Float, val y: Float, val len: Float, val tilt: Float)

private val Fibres: List<Fibre> = Random(11).let { r ->
    List(420) { Fibre(r.nextFloat(), r.nextFloat() * 1600f, 5f + r.nextFloat() * 18f, (r.nextFloat() - 0.5f) * 3f) }
}

/** Paper fill: warm gradient, faint fibre texture, soft shadow under the printer slot and at the side edges. */
fun DrawScope.drawPaperBackground(density: Float) {
    drawRect(Brush.verticalGradient(listOf(XpColors.Paper, XpColors.Paper, XpColors.PaperShade.copy(alpha = 0.65f))))
    val fibre = XpColors.PaperShade.copy(alpha = 0.55f)
    val stroke = 0.7f * density
    for (f in Fibres) {
        val y = f.y * density
        if (y > size.height) continue
        val x = f.x * size.width
        drawLine(fibre, Offset(x, y), Offset(x + f.len * density, y + f.tilt * density), stroke)
    }
    drawRect(
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent), endY = 18f * density),
    )
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.05f), 0.04f to Color.Transparent,
            0.96f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.05f),
        ),
    )
}
