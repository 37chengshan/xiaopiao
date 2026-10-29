package com.xiaopiao.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Tab-bar glyphs drawn by hand so each can act out its own state change (tt: 「图标能动起来有状态变化…不是这种假动」).
 * Resting: outline in the tab colour. Selected: the shape fills, its inner marks turn white, and a one-shot motion
 * plays — lines are written, paper prints, bars grow, people pop up, dots type, the pad wobbles.
 */
enum class NavGlyph { Ledger, Receipt, Chart, People, Chat, Gamepad, Calendar, Coin, Store, Person, Flask }

/**
 * [selected] drives the fill; every false→true change (and every [replay] bump while selected) plays the motion once.
 * With [reduced] motion the glyph only fills.
 */
@Composable
fun AnimatedNavGlyph(glyph: NavGlyph, selected: Boolean, tint: Color, reduced: Boolean, replay: Int, modifier: Modifier = Modifier) {
    val fill by animateFloatAsState(if (selected) 1f else 0f, spring(0.85f, 500f), label = "glyphFill")
    val play = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(selected, replay) {
        // The first composition shows the settled state; motion is for changes the user caused.
        if (!seen) { seen = true; return@LaunchedEffect }
        if (!selected) return@LaunchedEffect
        if (reduced) { play.snapTo(1f); return@LaunchedEffect }
        play.snapTo(0f)
        play.animateTo(1f, tween(680, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        val g = GlyphScope(this, tint, fill, if (selected) play.value else 1f)
        when (glyph) {
            NavGlyph.Ledger -> g.ledger()
            NavGlyph.Receipt -> g.receipt()
            NavGlyph.Chart -> g.chart()
            NavGlyph.People -> g.people()
            NavGlyph.Chat -> g.chat()
            NavGlyph.Gamepad -> g.gamepad()
            NavGlyph.Calendar -> g.calendar()
            NavGlyph.Coin -> g.coin()
            NavGlyph.Store -> g.store()
            NavGlyph.Person -> g.person()
            NavGlyph.Flask -> g.flask()
        }
    }
}

/** 0..1 slice of [t] between [from] and [to]. */
private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** Ease-out with a small overshoot, for things that pop into place. */
private fun back(t: Float): Float {
    val c = 1.9f
    val x = t - 1f
    return 1f + (c + 1f) * x * x * x + c * x * x
}

/** A damped swing that starts and ends at 0 — wobble and nods. */
private fun swing(t: Float, cycles: Float = 2f): Float = (sin(t * cycles * 2 * PI) * exp(-3.2 * t)).toFloat()

/** Draws in a 24-unit grid scaled to the canvas, like a 24dp icon. */
private class GlyphScope(val d: DrawScope, val tint: Color, val fill: Float, val play: Float) {
    val u = d.size.minDimension / 24f
    val stroke = Stroke(width = 1.8.dp.value * d.density, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val thin = Stroke(width = 1.6.dp.value * d.density, cap = StrokeCap.Round)
    /** Marks inside a filled shape turn white as it fills. */
    val mark get() = lerp(tint, Color.White, fill)

    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    fun sz(w: Float, h: Float) = Size(w * u, h * u)
    fun r(v: Float) = CornerRadius(v * u)

    /** Outline always, plus the fill fading in. */
    fun shape(draw: (androidx.compose.ui.graphics.drawscope.DrawStyle, Color) -> Unit) {
        if (fill > 0.01f) draw(Fill, tint.copy(alpha = tint.alpha * fill))
        draw(stroke, tint)
    }

    fun line(x0: Float, y0: Float, x1: Float, y1: Float, color: Color = mark, progress: Float = 1f) {
        if (progress <= 0f) return
        d.drawLine(color, o(x0, y0), o(x0 + (x1 - x0) * progress, y0 + (y1 - y0) * progress), thin.width, StrokeCap.Round)
    }

    // 记账 / 账单: a card whose three entry lines are written one after another.
    fun ledger() {
        shape { s, c -> d.drawRoundRect(c, o(3.5f, 4f), sz(17f, 16f), r(3.5f), style = s) }
        listOf(9f to 16.5f, 12f to 16.5f, 15f to 13f).forEachIndexed { i, (y, end) ->
            line(7.5f, y, end, y, progress = seg(play, 0.1f + i * 0.18f, 0.45f + i * 0.18f))
        }
    }

    // 小票: paper feeds out of the printer slot, saw-tooth edge last.
    fun receipt() {
        val feed = 0.45f + 0.55f * back(seg(play, 0f, 0.8f))
        val bottom = 4f + 16f * feed
        val paper = Path().apply {
            moveTo(6f * u, 4f * u); lineTo(18f * u, 4f * u); lineTo(18f * u, bottom * u)
            var x = 18f
            var down = false
            while (x > 6f) { x -= 2f; lineTo(x * u, (bottom + if (down) 0f else -1.6f) * u); down = !down }
            lineTo(6f * u, bottom * u); close()
        }
        shape { s, c -> d.drawPath(paper, c, style = s) }
        line(3.5f, 4f, 20.5f, 4f, tint)
        listOf(8.5f, 11.5f, 14.5f).forEachIndexed { i, y ->
            if (y < bottom - 2.5f) line(9f, y, if (i == 2) 12.5f else 15f, y, progress = seg(play, 0.35f + i * 0.12f, 0.7f + i * 0.12f))
        }
    }

    // 统计: three bars grow in turn, then a spark.
    fun chart() {
        listOf(Triple(4.5f, 7f, 0f), Triple(10.25f, 11f, 1f), Triple(16f, 15f, 2f)).forEach { (x, h, i) ->
            val k = back(seg(play, 0.08f + i * 0.14f, 0.5f + i * 0.14f)).coerceAtLeast(0.15f)
            val top = 20f - h * k
            shape { s, c -> d.drawRoundRect(c, o(x, top), sz(3.5f, 20f - top), r(1.2f), style = s) }
        }
        val spark = sin(seg(play, 0.6f, 1f) * PI).toFloat() * fill
        if (spark > 0.02f) d.scale(spark, pivot = o(20.5f, 4.5f)) {
            val p = Path().apply {
                moveTo(20.5f * u, 2f * u); lineTo(21.2f * u, 3.8f * u); lineTo(23f * u, 4.5f * u); lineTo(21.2f * u, 5.2f * u)
                lineTo(20.5f * u, 7f * u); lineTo(19.8f * u, 5.2f * u); lineTo(18f * u, 4.5f * u); lineTo(19.8f * u, 3.8f * u); close()
            }
            d.drawPath(p, tint)
        }
    }

    private fun personAt(cx: Float, base: Float, k: Float, head: Float) {
        d.scale(k, pivot = o(cx, base)) {
            shape { s, c -> d.drawCircle(c, head * u, o(cx, base - 11f), style = s) }
            shape { s, c ->
                d.drawArc(c, 180f, 180f, true, o(cx - head * 1.9f, base - 6.5f), sz(head * 3.8f, 13f), style = s)
            }
        }
    }

    // 圈子: two people pop up, the one behind first.
    fun people() {
        personAt(15.5f, 20f, back(seg(play, 0f, 0.55f)).coerceAtLeast(0.2f), 2.6f)
        personAt(8.5f, 20.5f, back(seg(play, 0.2f, 0.75f)).coerceAtLeast(0.2f), 3f)
    }

    // AI: the bubble fills and three dots type in a wave.
    fun chat() {
        val bubble = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(3f * u, 4f * u, 21f * u, 17f * u, r(5f)))
            moveTo(6.5f * u, 16f * u); lineTo(6f * u, 21f * u); lineTo(11f * u, 16.5f * u); close()
        }
        shape { s, c -> d.drawPath(bubble, c, style = s) }
        listOf(8f, 12f, 16f).forEachIndexed { i, x ->
            val hop = sin(seg(play, i * 0.12f, 0.45f + i * 0.12f) * PI).toFloat() + sin(seg(play, 0.45f + i * 0.12f, 0.9f + i * 0.12f) * PI).toFloat() * 0.6f
            d.drawCircle(mark, 1.35f * u, o(x, 10.5f - 1.8f * hop))
        }
    }

    // 游戏: the pad wobbles and its buttons light.
    fun gamepad() {
        d.rotate(16f * swing(play), pivot = o(12f, 13f)) {
            shape { s, c -> d.drawRoundRect(c, o(2.5f, 7.5f), sz(19f, 11f), r(5.5f), style = s) }
            line(6f, 13f, 10f, 13f); line(8f, 11f, 8f, 15f)
            val glow = 1f + 0.5f * sin(seg(play, 0.3f, 0.8f) * PI).toFloat()
            d.drawCircle(mark, 1.1f * u * glow, o(15.5f, 11.8f))
            d.drawCircle(mark, 1.1f * u * glow, o(17.8f, 14.2f))
        }
    }

    // 小店 今日: the page flips down and today's dot lands.
    fun calendar() {
        shape { s, c -> d.drawRoundRect(c, o(3.5f, 5f), sz(17f, 15.5f), r(3.5f), style = s) }
        line(3.5f, 9.5f, 20.5f, 9.5f, tint)
        line(8f, 3f, 8f, 6.5f, tint); line(16f, 3f, 16f, 6.5f, tint)
        val k = back(seg(play, 0.25f, 0.8f))
        d.drawCircle(mark, 2f * u * k.coerceAtLeast(0.6f), o(12f, 14.8f - 3f * (1f - seg(play, 0.25f, 0.6f))))
    }

    // 小店 营收: a coin flips and lands.
    fun coin() {
        val flip = cos(seg(play, 0f, 0.75f) * 2 * PI).toFloat()
        d.scale(scaleX = flip.coerceIn(-1f, 1f).let { if (kotlin.math.abs(it) < 0.12f) 0.12f else kotlin.math.abs(it) }, scaleY = 1f, pivot = o(12f, 12f)) {
            shape { s, c -> d.drawCircle(c, 8.5f * u, o(12f, 12f), style = s) }
            line(9.5f, 8.5f, 12f, 11.5f); line(14.5f, 8.5f, 12f, 11.5f)
            line(12f, 11.5f, 12f, 16f); line(9.5f, 12.5f, 14.5f, 12.5f)
        }
    }

    // 小店 经营: the awning drops into place over the door.
    fun store() {
        shape { s, c -> d.drawRoundRect(c, o(5f, 11f), sz(14f, 9.5f), r(1.5f), style = s) }
        line(10.5f, 20.5f, 10.5f, 15.5f, mark); line(13.5f, 20.5f, 13.5f, 15.5f, mark); line(10.5f, 15.5f, 13.5f, 15.5f, mark)
        d.translate(top = -3f * u * (1f - back(seg(play, 0f, 0.6f)))) {
            val awning = Path().apply {
                moveTo(4f * u, 4.5f * u); lineTo(20f * u, 4.5f * u); lineTo(21.5f * u, 9.5f * u)
                var x = 21.5f
                while (x > 3f) { val nx = x - 3.7f; quadraticTo((x + nx) / 2f * u, 12f * u, nx * u, 9.5f * u); x = nx }
                close()
            }
            shape { s, c -> d.drawPath(awning, c, style = s) }
        }
    }

    // 小店 我的: a small nod.
    fun person() {
        d.translate(top = 1.6f * u * swing(play, 1.5f)) {
            shape { s, c -> d.drawCircle(c, 4f * u, o(12f, 8f), style = s) }
        }
        shape { s, c -> d.drawArc(c, 180f, 180f, true, o(4.5f, 14f), sz(15f, 13f), style = s) }
    }

    // 小店 实验: liquid rises in the flask and two bubbles float up.
    fun flask() {
        val body = Path().apply {
            moveTo(9.5f * u, 3.5f * u); lineTo(9.5f * u, 9.5f * u); lineTo(4.5f * u, 18.5f * u)
            quadraticTo(4f * u, 20.5f * u, 6.5f * u, 20.5f * u); lineTo(17.5f * u, 20.5f * u)
            quadraticTo(20f * u, 20.5f * u, 19.5f * u, 18.5f * u); lineTo(14.5f * u, 9.5f * u); lineTo(14.5f * u, 3.5f * u)
        }
        val level = 20.5f - 7f * (0.35f + 0.65f * seg(play, 0f, 0.55f))
        if (fill > 0.01f) d.drawPath(Path().apply {
            moveTo(4.6f * u, 18.5f * u); lineTo(((level - 20.5f) / -11f * 5f + 4.5f).coerceAtMost(9.5f) * u, level * u)
            lineTo((19.5f - ((level - 20.5f) / -11f * 5f)).coerceAtLeast(14.5f) * u, level * u); lineTo(19.4f * u, 18.5f * u)
            quadraticTo(20f * u, 20.5f * u, 17.5f * u, 20.5f * u); lineTo(6.5f * u, 20.5f * u); quadraticTo(4f * u, 20.5f * u, 4.6f * u, 18.5f * u); close()
        }, tint.copy(alpha = tint.alpha * fill))
        d.drawPath(body, tint, style = stroke)
        line(8f, 3.5f, 16f, 3.5f, tint)
        listOf(10.5f to 0.35f, 13.5f to 0.5f).forEach { (x, start) ->
            val t = seg(play, start, start + 0.45f)
            if (t in 0.01f..0.99f) d.drawCircle(mark, 0.9f * u, o(x, level + 1.5f - 6f * t))
        }
    }
}
