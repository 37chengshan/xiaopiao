package com.xiaopiao.app.ui.merchant

import com.xiaopiao.app.ui.components.IconTile
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.Icons
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.merchant.Experiment
import com.xiaopiao.app.merchant.ExperimentStatus
import com.xiaopiao.app.merchant.ExperimentTemplate
import com.xiaopiao.app.merchant.ExperimentTemplates
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.tr
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Step ②: pick a template, preview what consumers see, publish with a launch animation. */
@Composable
fun SetupStep(current: Experiment?, onPublish: (ExperimentTemplate) -> Unit) {
    var selected by remember { mutableStateOf(current?.template ?: ExperimentTemplates.HALF_VS_TOPPING) }
    var launching by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(launching) {
        if (launching) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(1500)
            onPublish(selected)
        }
    }

    Box(Modifier.fillMaxSize()) {
        FlowPage {
            if (current != null && current.status != ExperimentStatus.ENDED) RunningBanner(current)
            Column(Modifier.padding(horizontal = XpSpace.l), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                Text(tr("选一个二选一模板", "Pick a this-or-that template"), style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                ExperimentTemplates.all.forEachIndexed { i, t ->
                    Reveal(i) {
                        SelectableCard(selected == t, { selected = t }) {
                            Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.m), verticalAlignment = Alignment.CenterVertically) {
                                IconTile(Icons.Outlined.CompareArrows, size = 36.dp, selected = selected == t)
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                                    Text(t.question.removePrefix("二选一："), style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
                                }
                            }
                        }
                    }
                }
            }
            Text(tr("顾客会看到", "What customers see"), Modifier.padding(horizontal = XpSpace.l), style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
            Box(Modifier.padding(horizontal = XpSpace.l)) {
                AnimatedContent(selected, label = "preview") { Preview(it) }
            }
            Column(Modifier.padding(horizontal = XpSpace.l), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                Text(
                    tr(
                        "只会推给已授权的邻居，以「游戏表态」收集，和真实消费分开存放。",
                        "Only sent to neighbours who opted in, collected as game answers and stored apart from real spending.",
                    ),
                    style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                )
                MerchantButton(tr("发布实验", "Publish experiment"), { launching = true }, enabled = !launching, clay = true)
            }
        }
        if (launching) LaunchOverlay(selected)
    }
}

@Composable
private fun RunningBanner(exp: Experiment) {
    Box(Modifier.padding(horizontal = XpSpace.l)) {
        XpCard {
            Text(tr("已有一个${exp.status.label}的实验", "An experiment is already ${exp.status.label}"), style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
            Text(
                tr("「${exp.template.title}」。发布新的会替换它，之前的游戏表态不会带过去。", "“${exp.template.title}”. Publishing a new one replaces it — earlier game answers do not carry over."),
                style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        }
    }
}

/** A tiny phone-card mock of what a consumer sees in the game. */
@Composable
private fun Preview(t: ExperimentTemplate) {
    XpCard {
        Text(tr("楼下的芋泥奶茶铺 想听听你的想法", "The taro milk tea shop downstairs wants your take"), style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        Text(t.question, Modifier.padding(vertical = XpSpace.s), style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            t.options.forEach { o ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(XpColors.Canvas).padding(XpSpace.m),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(o.emoji, style = MaterialTheme.typography.titleLarge)
                    Text(o.title, style = MaterialTheme.typography.labelLarge, color = XpColors.Ink, textAlign = TextAlign.Center)
                    Text(o.blurb, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary, textAlign = TextAlign.Center)
                }
            }
        }
        Text(tr("← 左滑选左边 · 右滑选右边 →", "← Swipe left for left · swipe right for right →"), Modifier.fillMaxWidth().padding(top = XpSpace.s), style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary, textAlign = TextAlign.Center)
    }
}

/** Rocket lifts off, rings ripple out, confirmation text pops in. */
@Composable
private fun LaunchOverlay(t: ExperimentTemplate) {
    val lift = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(Unit) { ring.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    LaunchedEffect(Unit) { delay(350); lift.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxSize().background(XpColors.Canvas.copy(alpha = 0.94f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            listOf(0f, 0.25f, 0.5f).forEach { d ->
                val p = ((ring.value - d) / (1f - d)).coerceIn(0f, 1f)
                drawCircle(XpColors.Accent.copy(alpha = (1f - p) * 0.5f), 40.dp.toPx() + p * 180.dp.toPx(), c, style = Stroke(3.dp.toPx()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            IconTile(
                Icons.Outlined.RocketLaunch, size = 72.dp, selected = true, modifier = Modifier.offset { IntOffset(0, (-lift.value * 220.dp.toPx()).roundToInt()) }
                    .alpha(1f - lift.value * 0.85f)
                    .graphicsLayer { rotationZ = lift.value * 8f }
                    .scale(0.9f + 0.4f * pop.value),
            )
            Text(tr("实验已发布！", "Experiment published!"), Modifier.scale(pop.value), style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
            Text(tr("「${t.title}」正在送往邻居的手机", "“${t.title}” is on its way to neighbours' phones"), style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        }
    }
}
