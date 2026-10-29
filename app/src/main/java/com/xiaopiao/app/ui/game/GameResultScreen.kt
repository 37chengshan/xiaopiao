package com.xiaopiao.app.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.merchant.MerchantButton
import com.xiaopiao.app.ui.merchant.Reveal
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun GameResultScreen(outcome: GameOutcome, recordedExperiment: Boolean, onAgain: () -> Unit, onExit: () -> Unit) {
    val pop = remember { Animatable(0f) }
    val confetti = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow)) }
    LaunchedEffect(Unit) { confetti.animateTo(1f, tween(1800, easing = LinearEasing)) }

    Box(Modifier.fillMaxSize()) {
        ConfettiLayer(confetti.value)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = XpSpace.l).padding(bottom = XpSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(XpSpace.m),
        ) {
            Text("本局结果", Modifier.padding(top = XpSpace.l), style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
            Text(outcome.persona.emoji, Modifier.scale(pop.value), style = MaterialTheme.typography.displayLarge.copy(fontSize = 60.sp))
            Text("你是『${outcome.persona.name}』！", Modifier.scale(0.8f + 0.2f * pop.value).alpha(pop.value.coerceIn(0f, 1f)), style = MaterialTheme.typography.headlineLarge, color = XpColors.Ink, textAlign = TextAlign.Center)
            Text(outcome.persona.blurb, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, textAlign = TextAlign.Center)
            Reveal(2) { BadgeTicket(outcome) }
            Reveal(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    outcome.tally.take(3).forEach { (name, n) ->
                        Text(
                            "${GameLogic.personaInfo(name).emoji} $name ×$n", style = MaterialTheme.typography.labelMedium, color = XpColors.Ink,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(XpColors.Card).padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            Reveal(4) {
                Text(
                    if (recordedExperiment) "你在商家问题上的选择已记为「游戏表态」，会加进实验结果；和你的真实消费分开存放，没有现金奖励。"
                    else "现在没有进行中的商家实验，这局只是好玩，不会记录任何数据。",
                    style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary, textAlign = TextAlign.Center,
                )
            }
            Reveal(5) {
                Column(verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    MerchantButton("再玩一局", onAgain)
                    MerchantButton("回到小店", onExit, primary = false)
                }
            }
        }
    }
}

@Composable
private fun BadgeTicket(outcome: GameOutcome) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(XpColors.Card).padding(XpSpace.l),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.m),
    ) {
        Box(Modifier.clip(RoundedCornerShape(50)).background(XpColors.AccentSoft).padding(12.dp)) {
            Text(outcome.badge.emoji, style = MaterialTheme.typography.titleLarge)
        }
        Column {
            Text("获得徽章", style = MaterialTheme.typography.labelMedium, color = XpColors.Accent)
            Text(outcome.badge.title, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
            Text(outcome.badge.reason, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        }
    }
}

private class Particle(val angle: Float, val speed: Float, val size: Float, val color: Color)

@Composable
private fun ConfettiLayer(t: Float) {
    val palette = listOf(XpColors.Accent, XpColors.Warning, XpColors.Positive, XpColors.Stamp)
    val particles = remember {
        val r = Random(11)
        List(36) { Particle(r.nextFloat() * 6.2832f, 0.35f + r.nextFloat() * 0.65f, 3f + r.nextFloat() * 4f, palette[r.nextInt(palette.size)]) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val origin = Offset(size.width / 2, size.height * 0.22f)
        val spread = size.minDimension * 0.9f
        particles.forEach { p ->
            val d = p.speed * spread * (1f - (1f - t) * (1f - t))
            val gravity = t * t * size.height * 0.25f
            drawCircle(
                p.color.copy(alpha = (1f - t).coerceIn(0f, 1f)), p.size.dp.toPx(),
                Offset(origin.x + cos(p.angle) * d, origin.y + sin(p.angle) * d + gravity),
            )
        }
    }
}
