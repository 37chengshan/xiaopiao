package com.xiaopiao.app.ui.merchant

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.merchant.Aggregator
import com.xiaopiao.app.merchant.ExperimentEngine
import com.xiaopiao.app.merchant.ExperimentStatus
import com.xiaopiao.app.merchant.MerchantStore
import com.xiaopiao.app.merchant.SuggestionGenerator
import com.xiaopiao.app.ui.game.DuelGameScreen
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

enum class MerchantRoute(val order: Int, val title: String) {
    Landing(0, "小店"), Insights(1, "人群洞察"), Setup(2, "发起实验"),
    Results(3, "看结果"), Decide(4, "你来决定"), Game(5, "二选一大作战"),
}

@Composable
fun MerchantScreen(container: AppContainer) {
    val store = remember { MerchantStore.get(container.appContext) }
    val state by store.state.collectAsState()
    var route by rememberSaveable { mutableStateOf(MerchantRoute.Landing) }
    var gameReturn by rememberSaveable { mutableStateOf(MerchantRoute.Landing) }
    var gameSeed by rememberSaveable { mutableStateOf(0L) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }

    val experiment = state.experiment
    val result = remember(experiment, store.crowd) { experiment?.let { ExperimentEngine.result(it, store.crowd) } }

    fun go(to: MerchantRoute) { route = to }
    fun playGame(from: MerchantRoute) {
        gameSeed = System.currentTimeMillis()
        gameReturn = from
        route = MerchantRoute.Game
    }

    BackHandler(enabled = route != MerchantRoute.Landing) {
        route = if (route == MerchantRoute.Game) gameReturn else MerchantRoute.Landing
    }

    Box(Modifier.fillMaxSize().background(XpColors.Canvas)) {
        AnimatedContent(
            targetState = route,
            transitionSpec = { slideSpec(initialState, targetState) },
            label = "merchant-route",
        ) { r ->
            when (r) {
                MerchantRoute.Landing -> MerchantLanding(
                    state, result, onOpen = { if (it == MerchantRoute.Game) playGame(MerchantRoute.Landing) else go(it) },
                    onPrivacy = { showPrivacy = true },
                )
                MerchantRoute.Game -> DuelGameScreen(
                    experiment = experiment?.takeIf { it.status == ExperimentStatus.RUNNING },
                    seed = gameSeed,
                    onExperimentAnswer = { id -> store.update { it.addGameResponse(id, System.currentTimeMillis()) } },
                    onFinished = { code -> store.update { it.finishGame(code) } },
                    onExit = { route = gameReturn },
                )
                else -> FlowFrame(r, hasExperiment = experiment != null, onJump = ::go, onBack = { go(MerchantRoute.Landing) }, onPrivacy = { showPrivacy = true }) {
                    when (r) {
                        MerchantRoute.Insights -> InsightsStep(store.crowd, onNext = { go(MerchantRoute.Setup) }, onPrivacy = { showPrivacy = true })
                        MerchantRoute.Setup -> SetupStep(experiment) { template ->
                            store.update { it.publish(template, System.currentTimeMillis()) }
                            go(MerchantRoute.Results)
                        }
                        MerchantRoute.Results -> ResultsStep(
                            experiment, result, onPlay = { playGame(MerchantRoute.Results) },
                            onNext = { go(MerchantRoute.Decide) }, onSetup = { go(MerchantRoute.Setup) },
                        )
                        else -> DecideHost(store, state, result, onSetup = { go(MerchantRoute.Setup) })
                    }
                }
            }
        }
        if (showPrivacy) PrivacySheet { showPrivacy = false }
    }
}

@Composable
private fun DecideHost(
    store: MerchantStore,
    state: com.xiaopiao.app.merchant.MerchantState,
    result: com.xiaopiao.app.merchant.ExperimentResult?,
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

private fun slideSpec(from: MerchantRoute, to: MerchantRoute): ContentTransform {
    val forward = to.order > from.order
    val dir = if (forward) 1 else -1
    return (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
        slideInHorizontally(spring(0.85f, Spring.StiffnessMediumLow)) { dir * it / 4 }) togetherWith
        (fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
            slideOutHorizontally(spring(0.85f, Spring.StiffnessMediumLow)) { -dir * it / 4 })
}

/** Chrome for steps 1–4: back chevron, step title, 演示数据 pill, tappable 4-segment progress. */
@Composable
private fun FlowFrame(
    route: MerchantRoute,
    hasExperiment: Boolean,
    onJump: (MerchantRoute) -> Unit,
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    content: @Composable () -> Unit,
) {
    NavScaffold(
        route.title, NavScrollNone, onBack = onBack, backLabel = "小店", demo = true,
        trailing = {
            Text("数据怎么来的", Modifier.clickable(onClick = onPrivacy), style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        },
    ) { top ->
        Column(Modifier.fillMaxSize().padding(top = top)) {
            Column(Modifier.padding(horizontal = XpSpace.l)) {
                Text("第 ${route.order} 步 / 共 4 步", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                Text(route.title, style = MaterialTheme.typography.headlineLarge, color = XpColors.Ink)
                StepBar(route, hasExperiment, onJump)
            }
            Box(Modifier.weight(1f).padding(top = XpSpace.s)) { content() }
        }
    }
}

@Composable
private fun StepBar(current: MerchantRoute, hasExperiment: Boolean, onJump: (MerchantRoute) -> Unit) {
    val steps = listOf(MerchantRoute.Insights, MerchantRoute.Setup, MerchantRoute.Results, MerchantRoute.Decide)
    Row(Modifier.fillMaxWidth().padding(top = XpSpace.s), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEach { s ->
            val reachable = s.order <= 2 || hasExperiment
            val fill by androidx.compose.animation.animateColorAsState(
                if (s.order <= current.order) XpColors.Accent else XpColors.Fill, label = "step",
            )
            Box(
                Modifier.weight(1f).clickable(enabled = reachable && s != current) { onJump(s) }.padding(vertical = 8.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(fill))
            }
        }
    }
}
