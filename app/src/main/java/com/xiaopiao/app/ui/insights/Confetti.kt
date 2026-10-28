package com.xiaopiao.app.ui.insights

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.xiaopiao.app.ui.theme.XpColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Particle(
    val angle: Float, val speed: Float, val spin: Float, val size: Float, val color: Color, val round: Boolean,
)

private val CONFETTI_COLORS = listOf(
    XpColors.Accent, XpColors.Warning, XpColors.Positive, XpColors.Stamp,
    Color(0xFF5AC8FA), Color(0xFFAF52DE), Color(0xFFFFD60A),
)

/**
 * Confetti burst drawn on a Canvas. Particles shoot upward from the bottom centre, then gravity pulls them down.
 * Bump [burstKey] to fire another burst.
 */
@Composable
fun ConfettiBurst(burstKey: Int, modifier: Modifier = Modifier, count: Int = 90) {
    val particles = remember(burstKey) {
        val rnd = Random(burstKey * 31 + 7)
        List(count) {
            Particle(
                angle = (-PI.toFloat() / 2) + (rnd.nextFloat() - 0.5f) * 1.7f,
                speed = 0.55f + rnd.nextFloat() * 0.75f,
                spin = (rnd.nextFloat() - 0.5f) * 900f,
                size = 0.012f + rnd.nextFloat() * 0.014f,
                color = CONFETTI_COLORS[rnd.nextInt(CONFETTI_COLORS.size)],
                round = rnd.nextInt(4) == 0,
            )
        }
    }
    val t = remember(burstKey) { Animatable(0f) }
    LaunchedEffect(burstKey) { t.snapTo(0f); t.animateTo(1f, tween(2600, easing = LinearEasing)) }

    Canvas(modifier.fillMaxSize()) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val w = size.width
        val h = size.height
        val seconds = p * 2.6f
        val gravity = 1.9f
        particles.forEach { q ->
            // Positions are in "screen widths"; speed scales with width so it looks the same on any phone.
            val x = w * 0.5f + cos(q.angle) * q.speed * seconds * w * 1.1f
            val y = h * 0.62f + sin(q.angle) * q.speed * seconds * w * 1.1f + 0.5f * gravity * seconds * seconds * w * 0.55f
            val alpha = if (p > 0.7f) (1f - p) / 0.3f else 1f
            val s = q.size * w
            val c = q.color.copy(alpha = alpha.coerceIn(0f, 1f))
            if (q.round) {
                drawCircle(c, s * 0.6f, Offset(x, y))
            } else {
                rotate(q.spin * seconds, Offset(x, y)) {
                    drawRect(c, Offset(x - s / 2, y - s / 3), Size(s, s * 0.66f))
                }
            }
        }
    }
}
