package com.xiaopiao.app.ui.insights

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.insights.RegretScenario
import com.xiaopiao.app.insights.RegretSimulator
import com.xiaopiao.app.insights.RegretTarget
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import kotlin.math.roundToInt

/** 后悔模拟器: pure arithmetic "what if I had spent X% less" — no returns, no advice. */
@Composable
fun RegretPage(input: InsightsInput, onBack: () -> Unit) {
    var window by rememberSaveable { mutableIntStateOf(3) }
    var reduction by rememberSaveable { mutableFloatStateOf(50f) }
    var targetKey by rememberSaveable { mutableStateOf<String?>(null) }

    val (cats, merchants) = remember(input, window) { RegretSimulator.candidates(input.history, input.now, window) }
    val targets: List<RegretTarget> = cats + merchants
    val target = targets.firstOrNull { it.label == targetKey } ?: targets.firstOrNull()
    val hasData = input.history.any { it.direction == Direction.OUT }

    InsightsPage("后悔模拟器", "如果当初少花一点……", onBack) {
        if (!hasData || target == null) {
            InsightsEmpty("🔮", "还没有可以模拟的账目", "记下一些支出后，这里可以算算「少花一点」能攒下多少。")
            return@InsightsPage
        }
        Segmented(RegretSimulator.WINDOWS.map { it to "近 $it 个月" }, window) { window = it }
        Text("选一个想假设的对象", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            targets.forEach { t -> SelectChip(t.label, t.label == target.label) { targetKey = t.label } }
        }
        XpCard {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("少花", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                Text("${reduction.roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = XpColors.Accent)
            }
            Slider(
                value = reduction, onValueChange = { reduction = (it / 5).roundToInt() * 5f }, valueRange = 10f..100f,
                colors = SliderDefaults.colors(thumbColor = XpColors.Accent, activeTrackColor = XpColors.Accent, inactiveTrackColor = XpColors.Hairline),
            )
        }
        val result = RegretSimulator.simulate(input.history, RegretScenario(target, reduction.roundToInt(), window), input.now)
        XpCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    "近 $window 个月在「${target.label}」上花了",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoneyText(result.spentCents, style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
                Text(
                    "，共 ${result.txnCount} 笔", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                )
            }
            Text("如果少花 ${reduction.roundToInt()}%，大约能省下", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary, modifier = Modifier.padding(top = XpSpace.s))
            BigMoney(result.savedCents)
            AnimatedContent(
                result.equivalents,
                transitionSpec = {
                    (fadeIn(spring(stiffness = Spring.StiffnessLow)) + slideInVertically { it / 4 }) togetherWith fadeOut()
                },
                label = "equivalents",
            ) { eqs ->
                Column(Modifier.padding(top = XpSpace.m), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    if (eqs.isEmpty()) Text("这点钱还不够换一杯奶茶～🌱", color = XpColors.InkSecondary)
                    eqs.forEach { e ->
                        Text("${e.emoji} ≈ ${e.count} ${e.unitLabel}${e.noun}", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                    }
                }
            }
        }
        Text(result.disclaimer, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
    }
}
