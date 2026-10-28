package com.xiaopiao.app.ui.merchant

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.merchant.Decision
import com.xiaopiao.app.merchant.Experiment
import com.xiaopiao.app.merchant.Suggestion
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Step ④: three suggestion cards; the owner decides. After deciding, shows a confirmation + review note. */
@Composable
fun DecideStep(
    experiment: Experiment?,
    suggestions: List<Suggestion>,
    decision: Decision?,
    onAdopt: (Suggestion) -> Unit,
    onNewExperiment: () -> Unit,
    onSetup: () -> Unit,
) {
    if (experiment == null) {
        EmptyState("🤔", "先做个实验再决定", "有了实验结果，这里会给你 3 个可以试试的选择。", "去发起实验", onSetup)
        return
    }
    AnimatedContent(
        decision,
        transitionSpec = {
            (fadeIn(spring(stiffness = Spring.StiffnessLow)) + slideInVertically(spring(0.8f, Spring.StiffnessLow)) { it / 8 }) togetherWith
                (fadeOut() + slideOutVertically { -it / 10 })
        },
        label = "decide",
    ) { d ->
        if (d == null) Picker(suggestions, onAdopt) else Confirmed(d, onNewExperiment)
    }
}

@Composable
private fun Picker(suggestions: List<Suggestion>, onAdopt: (Suggestion) -> Unit) {
    var pickedId by remember { mutableStateOf<String?>(null) }
    val picked = suggestions.firstOrNull { it.id == pickedId }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = XpSpace.l).padding(bottom = XpSpace.xl),
        verticalArrangement = Arrangement.spacedBy(XpSpace.s),
    ) {
        Text("基于这轮结果，有 3 个可以试试的方向。选哪个、要不要选，都由你决定。", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
        suggestions.forEachIndexed { i, s ->
            Reveal(i) {
                SelectableCard(pickedId == s.id, { pickedId = s.id }) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                            Text(s.emoji, style = MaterialTheme.typography.titleLarge)
                            Text("可以试试…", style = MaterialTheme.typography.labelMedium, color = XpColors.Accent)
                        }
                        Text(s.headline, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                        Text(s.detail, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(XpColors.Canvas).padding(XpSpace.m)) {
                            Text("权衡", style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
                            Text(s.tradeoff, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            picked != null,
            enter = fadeIn() + slideInVertically(spring(0.7f, Spring.StiffnessMediumLow)) { it },
        ) {
            MerchantButton("就选这个 · 记下来", { picked?.let(onAdopt) }, Modifier.padding(top = XpSpace.s))
        }
        if (picked == null) {
            Text("点一张卡片选中它。想再等等也可以，什么都不选。", style = MaterialTheme.typography.labelMedium, color = XpColors.InkTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Confirmed(d: Decision, onNewExperiment: () -> Unit) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)) }
    val fmt = remember { SimpleDateFormat("M月d日", Locale.CHINA) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = XpSpace.l).padding(bottom = XpSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XpSpace.m),
    ) {
        Text("✅", Modifier.scale(pop.value).padding(top = XpSpace.l), style = MaterialTheme.typography.displayLarge)
        Text("你做了决定", style = MaterialTheme.typography.titleLarge, color = XpColors.Ink)
        XpCard {
            Text("你选了", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
            Text(d.headline, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
            Text("记录于 ${fmt.format(Date(d.chosenAt))}", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        }
        XpCard {
            Text("⏰ 两周后复盘 · ${fmt.format(Date(d.reviewAt))}", style = MaterialTheme.typography.titleMedium, color = XpColors.Accent)
            Text(d.reviewNote, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
            Text("只保存在这台手机上，不会上传。", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        }
        MerchantButton("再做一个新实验", onNewExperiment, primary = false)
    }
}
