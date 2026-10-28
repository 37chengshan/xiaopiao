package com.xiaopiao.app.ui.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.merchant.Experiment
import com.xiaopiao.app.ui.merchant.DemoPill
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * 二选一大作战 — consumer-facing swipe game. Only the experiment round is reported through
 * [onExperimentAnswer] (as a 「游戏表态」); filler answers just shape the persona result.
 */
@Composable
fun DuelGameScreen(
    experiment: Experiment?,
    seed: Long,
    onExperimentAnswer: (optionId: String) -> Unit,
    onFinished: (badgeCode: String) -> Unit,
    onExit: () -> Unit,
) {
    var playKey by remember { mutableIntStateOf(0) }
    val rounds = remember(seed, playKey) { GameLogic.buildRounds(experiment, seed + playKey) }
    val answers = remember(seed, playKey) { mutableStateListOf<GameChoice>() }
    val finished = answers.size >= rounds.size

    Column(Modifier.fillMaxSize().background(XpColors.Canvas)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.s),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
        ) {
            Text("✕", Modifier.clickable(onClick = onExit).padding(end = 8.dp, top = 6.dp, bottom = 6.dp), style = MaterialTheme.typography.titleMedium, color = XpColors.InkSecondary)
            ProgressDots(rounds.size, answers.size, Modifier.weight(1f))
            DemoPill()
        }
        AnimatedContent(
            targetState = finished,
            transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessLow)) togetherWith fadeOut() },
            label = "game-phase",
            modifier = Modifier.weight(1f),
        ) { done ->
            if (done) {
                val outcome = remember(rounds, answers.toList()) { GameLogic.outcome(answers.toList()) }
                LaunchedEffect(playKey) { onFinished(outcome.badge.code) }
                GameResultScreen(
                    outcome = outcome,
                    recordedExperiment = experiment != null,
                    onAgain = { playKey += 1 },
                    onExit = onExit,
                )
            } else {
                val idx = answers.size.coerceAtMost(rounds.lastIndex)
                SwipeDeck(
                    round = rounds[idx],
                    next = rounds.getOrNull(idx + 1),
                    onPick = { side ->
                        val round = rounds[idx]
                        val choice = if (side == Side.Left) round.left else round.right
                        if (round.isExperiment) onExperimentAnswer(choice.id)
                        answers.add(choice)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProgressDots(total: Int, done: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val w by animateDpAsState(if (i == done) 22.dp else 8.dp, spring(0.6f, Spring.StiffnessMedium), label = "dotw")
            val c by animateColorAsState(if (i <= done) XpColors.Accent else XpColors.InkTertiary.copy(alpha = 0.5f), label = "dotc")
            Box(Modifier.width(w).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c))
        }
    }
}
