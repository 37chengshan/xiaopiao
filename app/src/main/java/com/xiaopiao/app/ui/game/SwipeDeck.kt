package com.xiaopiao.app.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.merchant.MerchantButton
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Side { Left, Right }

/**
 * Tinder-style card stack. Drag left/right (card rotates, springs back if released early) or use the
 * two buttons. [round] is the top card; [next] is previewed behind it.
 */
@Composable
fun SwipeDeck(round: GameRound, next: GameRound?, onPick: (Side) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    var command by remember(round.id) { mutableStateOf<Side?>(null) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val threshold = widthPx * 0.26f
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = XpSpace.l)) {
                if (next != null) {
                    // Static preview card; the live card above springs from the same scale when it replaces this.
                    CardFace(next, 0f, Modifier.scale(BEHIND_SCALE).alpha(0.9f))
                }
                TopCard(round, widthPx, threshold, command, onPick, haptic)
            }
            ChoiceButtons(round) { command = it }
        }
    }
}

private const val BEHIND_SCALE = 0.94f

@Composable
private fun TopCard(
    round: GameRound,
    widthPx: Float,
    threshold: Float,
    command: Side?,
    onPick: (Side) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
) {
    val scope = rememberCoroutineScope()
    val x = remember(round.id) { Animatable(0f) }
    val appear = remember(round.id) { Animatable(BEHIND_SCALE) }
    var committed by remember(round.id) { mutableStateOf(false) }
    LaunchedEffect(round.id) { appear.animateTo(1f, spring(0.6f, Spring.StiffnessMedium)) }

    fun fling(side: Side) {
        if (committed) return
        committed = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            x.animateTo(if (side == Side.Left) -widthPx * 1.3f else widthPx * 1.3f, tween(220))
            onPick(side)
        }
    }
    LaunchedEffect(command) { command?.let(::fling) }

    val past = abs(x.value) > threshold
    LaunchedEffect(past) { if (past) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    CardFace(
        round, (x.value / threshold).coerceIn(-1.4f, 1.4f),
        Modifier
            .offset { IntOffset(x.value.roundToInt(), 0) }
            .graphicsLayer {
                rotationZ = x.value / widthPx * 22f
                scaleX = appear.value; scaleY = appear.value
            }
            .pointerInput(round.id) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            when {
                                x.value > threshold -> fling(Side.Right)
                                x.value < -threshold -> fling(Side.Left)
                                else -> x.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { x.animateTo(0f, spring(0.5f, Spring.StiffnessMedium)) } },
                ) { change, drag ->
                    change.consume()
                    if (!committed) scope.launch { x.snapTo(x.value + drag.x) }
                }
            },
    )
}

@Composable
private fun ChoiceButtons(round: GameRound, onChoose: (Side) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = XpSpace.l).padding(bottom = XpSpace.m), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        Box(Modifier.weight(1f)) { MerchantButton("← ${round.left.label}", { onChoose(Side.Left) }, primary = false) }
        Box(Modifier.weight(1f)) { MerchantButton("${round.right.label} →", { onChoose(Side.Right) }) }
    }
}

/** [lean] -1..1: negative highlights the left choice, positive the right. */
@Composable
private fun CardFace(round: GameRound, lean: Float, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().clip(RoundedCornerShape(XpRadius.xl)).background(XpColors.Card)
            .border(0.5.dp, XpColors.Hairline, RoundedCornerShape(XpRadius.xl)).padding(XpSpace.l),
        verticalArrangement = Arrangement.spacedBy(XpSpace.m),
    ) {
        Text(
            if (round.isExperiment) "楼下的芋泥奶茶铺 想听听你的想法" else "小热身",
            style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
        )
        Text(round.question, style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            ChoicePanel(round.left, (-lean).coerceIn(0f, 1f), Modifier.weight(1f))
            ChoicePanel(round.right, lean.coerceIn(0f, 1f), Modifier.weight(1f))
        }
        Text("← 左滑 · 右滑 →", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary)
    }
}

@Composable
private fun ChoicePanel(c: GameChoice, glow: Float, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().clip(RoundedCornerShape(XpRadius.m))
            .background(XpColors.Surface2)
            .background(XpColors.AccentSoft.copy(alpha = glow))
            .border(2.dp, XpColors.Accent.copy(alpha = glow), RoundedCornerShape(XpRadius.m)).padding(XpSpace.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(c.emoji, Modifier.scale(1f + 0.25f * glow), style = MaterialTheme.typography.displayLarge)
        Text(c.label, Modifier.padding(top = XpSpace.s), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
        Text("选它！", Modifier.alpha(glow).padding(top = 4.dp), style = MaterialTheme.typography.labelLarge, color = XpColors.Accent)
    }
}
