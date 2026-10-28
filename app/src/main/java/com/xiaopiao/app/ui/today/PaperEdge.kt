package com.xiaopiao.app.ui.today

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.max
import kotlin.math.roundToInt
import java.util.Random

/**
 * Receipt paper geometry (px): zigzag bottom, optional zigzag top for a freshly torn sheet. A torn top is not a
 * perfect saw: every tooth is a little taller or shorter and off-centre, like paper ripped along a serrated bar.
 */
fun paperPath(w: Float, h: Float, tooth: Float, depth: Float, tornTop: Boolean): Path {
    val n = max(1, (w / tooth).roundToInt())
    val step = w / n
    val rnd = Random(3)
    return Path().apply {
        moveTo(0f, if (tornTop) depth else 0f)
        if (tornTop) {
            for (i in 0 until n) {
                val peak = depth * 0.45f * rnd.nextFloat()
                val skew = (rnd.nextFloat() - 0.5f) * step * 0.35f
                lineTo(step * i + step / 2f + skew, peak)
                lineTo(step * (i + 1), if (i == n - 1) depth else depth * (0.8f + 0.2f * rnd.nextFloat()))
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
private class Grain(val x: Float, val y: Float, val r: Float, val dark: Boolean)
private class Streak(val x: Float, val width: Float, val alpha: Float)

private val Fibres: List<Fibre> = Random(11).let { r ->
    List(420) { Fibre(r.nextFloat(), r.nextFloat() * 1600f, 5f + r.nextFloat() * 18f, (r.nextFloat() - 0.5f) * 3f) }
}

/** Procedural paper tooth: tiny pits and flecks (no image assets), a fixed pattern so the paper never shimmers. */
private val Grains: List<Grain> = Random(23).let { r ->
    List(1500) { Grain(r.nextFloat(), r.nextFloat() * 1600f, 0.35f + r.nextFloat() * 0.7f, r.nextFloat() < 0.8f) }
}

/** Print-head dropouts: a few columns of heating elements that run a touch cooler, so the ink is paler there. */
private val Streaks: List<Streak> = Random(41).let { r ->
    List(7) { Streak(r.nextFloat(), 0.8f + r.nextFloat() * 2.2f, 0.05f + r.nextFloat() * 0.08f) }
}

/**
 * Paper fill: soft vertical shading, long fibres, fine grain, a shadow under the printer slot and at the side edges,
 * and a faint curl along the free end (the paper's own weight lifting it off the table).
 */
fun DrawScope.drawPaperBackground(density: Float, c: PaperColors) {
    drawRect(Brush.verticalGradient(listOf(c.paper, c.paper, lerp(c.paper, c.shade, 0.65f))))
    val fibre = c.shade.copy(alpha = 0.55f)
    val stroke = 0.7f * density
    for (f in Fibres) {
        val y = f.y * density
        if (y > size.height) continue
        val x = f.x * size.width
        drawLine(fibre, Offset(x, y), Offset(x + f.len * density, y + f.tilt * density), stroke)
    }
    val fleck = if (c.dark) Color.White else Color.Black
    for (g in Grains) {
        val y = g.y * density
        if (y > size.height) continue
        val col = if (g.dark) fleck.copy(alpha = if (c.dark) 0.05f else 0.07f) else c.shade.copy(alpha = 0.7f)
        drawCircle(col, g.r * density, Offset(g.x * size.width, y))
    }
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent), endY = 18f * density))
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.05f), 0.04f to Color.Transparent,
            0.96f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.05f),
        ),
    )
    // Curl: shade rolling over just above the free end, a thin highlight on the lip.
    val curl = 26f * density
    if (size.height > curl * 2) {
        val top = size.height - curl
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.55f to Color.Black.copy(alpha = 0.07f), 0.85f to Color.White.copy(alpha = if (c.dark) 0.05f else 0.28f), 1f to Color.Black.copy(alpha = 0.05f),
                startY = top, endY = size.height,
            ),
            topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, curl),
        )
    }
}

/** Drawn over the printed content: head dropout streaks and slow density waves make the ink read as thermal, not vector. */
fun DrawScope.drawThermalUnevenness(density: Float, c: PaperColors) {
    for (s in Streaks) drawRect(c.paper.copy(alpha = s.alpha), Offset(s.x * size.width, 0f), androidx.compose.ui.geometry.Size(s.width * density, size.height))
    var y = 40f * density
    var i = 0
    while (y < size.height) {
        val band = (14f + 16f * ((i * 37) % 10) / 10f) * density
        drawRect(c.paper.copy(alpha = 0.035f + 0.03f * ((i * 53) % 10) / 10f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, band))
        y += 90f * density + 23f * density * ((i * 29) % 7) / 7f
        i++
    }
}
