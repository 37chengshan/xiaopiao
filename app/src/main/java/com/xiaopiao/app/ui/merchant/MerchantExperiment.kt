package com.xiaopiao.app.ui.merchant

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.merchant.Aggregator
import com.xiaopiao.app.merchant.ExperimentResult
import com.xiaopiao.app.merchant.ExperimentStatus
import com.xiaopiao.app.merchant.MerchantState
import com.xiaopiao.app.merchant.MerchantStore
import com.xiaopiao.app.merchant.SuggestionGenerator
import com.xiaopiao.app.ui.components.NavIconButton
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.game.DuelGameScreen
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/**
 * The customer-experiment flow kept from the earlier build: 人群洞察 → 发起实验 → 看结果 → 你来决定,
 * plus the two-choice game. It now lives under 我的 and keeps its own step chrome, so the four
 * money tabs stay about the shop's own numbers.
 */
@Composable
fun MerchantExperimentScreen(
    sub: MerchantSub,
    store: MerchantStore,
    state: MerchantState,
    result: ExperimentResult?,
    onNav: (MerchantSub) -> Unit,
    onClose: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val experiment = state.experiment

    if (sub == MerchantSub.Game) {
        DuelGameScreen(
            experiment = experiment?.takeIf { it.status == ExperimentStatus.RUNNING },
            seed = remember { System.currentTimeMillis() },
            onExperimentAnswer = { id -> store.update { it.addGameResponse(id, System.currentTimeMillis()) } },
            onFinished = { code -> store.update { it.finishGame(code) } },
            onExit = { onNav(MerchantSub.Results) },
        )
        return
    }

    FlowFrame(
        route = sub,
        hasExperiment = experiment != null,
        onBack = onClose,
        onPrivacy = onPrivacy,
        onJump = onNav,
    ) {
        when (sub) {
            MerchantSub.Crowd -> InsightsStep(
                crowd = store.crowd,
                onNext = { onNav(MerchantSub.Setup) },
                onPrivacy = onPrivacy,
            )
            MerchantSub.Setup -> SetupStep(experiment) { template ->
                store.update { it.publish(template, System.currentTimeMillis()) }
                onNav(MerchantSub.Results)
            }
            MerchantSub.Results -> ResultsStep(
                experiment = experiment,
                result = result,
                onPlay = { onNav(MerchantSub.Game) },
                onNext = { onNav(MerchantSub.Decide) },
                onSetup = { onNav(MerchantSub.Setup) },
            )
            else -> DecideHost(store, state, result) { onNav(MerchantSub.Setup) }
        }
    }
}

@Composable
private fun DecideHost(
    store: MerchantStore,
    state: MerchantState,
    result: ExperimentResult?,
    onSetup: () -> Unit,
) {
    val exp = state.experiment
    val suggestions = remember(exp, result) {
        if (exp == null || result == null) emptyList()
        else {
            val quiet = Aggregator.quietestVisibleSlot(Aggregator.compute(store.crowd))
            SuggestionGenerator.generate(exp.template, result, quiet)
        }
    }
    DecideStep(
        experiment = exp,
        suggestions = suggestions,
        decision = exp?.let { state.decisionFor(it.id) },
        onAdopt = { s -> store.update { it.adopt(s, System.currentTimeMillis()) } },
        onNewExperiment = { store.update { it.clearExperiment() }; onSetup() },
        onSetup = onSetup,
    )
}

/** Back chevron, step title, the demo pill, a tappable 4-segment progress and the provenance link. */
@Composable
private fun FlowFrame(
    route: MerchantSub,
    hasExperiment: Boolean,
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onJump: (MerchantSub) -> Unit,
    content: @Composable () -> Unit,
) {
    NavScaffold(
        title = route.title,
        scroll = NavScrollNone,
        onBack = onBack,
        // The flow closes back to 分析, its home tab (移动七条 §1).
        backLabel = bi("分析", "Insights"),
        demo = false,
        trailing = { NavIconButton(Icons.Outlined.Info, bi("数据怎么来的", "Where the data comes from"), onClick = onPrivacy) },
    ) { top ->
        // The step bar scrolls with the page so the whole flow can run under the glass bar (DESIGN §4.3).
        FlowChromeHost(FlowChrome(top = top + XpSpace.s) { StepBar(route, hasExperiment, onJump) }) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun StepBar(current: MerchantSub, hasExperiment: Boolean, onJump: (MerchantSub) -> Unit) {
    val steps = listOf(MerchantSub.Crowd, MerchantSub.Setup, MerchantSub.Results, MerchantSub.Decide)
    Row(Modifier.fillMaxWidth().padding(top = XpSpace.s), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEach { step ->
            val reachable = step.order <= 2 || hasExperiment
            val fill by animateColorAsState(
                if (step.order <= current.order) XpColors.Accent else XpColors.Fill, label = "step",
            )
            Box(
                Modifier.weight(1f).clickable(enabled = reachable && step != current) { onJump(step) }
                    .padding(vertical = 8.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(fill),
                )
            }
        }
    }
}
