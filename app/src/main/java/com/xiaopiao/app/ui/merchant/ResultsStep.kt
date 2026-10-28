package com.xiaopiao.app.ui.merchant

import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.Icons
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.merchant.Confidence
import com.xiaopiao.app.merchant.Experiment
import com.xiaopiao.app.merchant.ExperimentResult
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/** Step ③: split bar, CI whiskers, sample size, plain-language confidence. */
@Composable
fun ResultsStep(
    experiment: Experiment?,
    result: ExperimentResult?,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onSetup: () -> Unit,
) {
    if (experiment == null || result == null) {
        EmptyState(Icons.Outlined.Science, "还没有实验", "先发起一个二选一小实验，邻居玩过之后这里就会出现结果。", "去发起实验", onSetup)
        return
    }
    val a = result.stats[0]
    val b = result.stats[1]
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(bottom = XpSpace.xl),
        verticalArrangement = Arrangement.spacedBy(XpSpace.m),
    ) {
        Reveal(0) {
            Column(Modifier.padding(horizontal = XpSpace.l)) {
                XpCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(experiment.template.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s), verticalAlignment = Alignment.CenterVertically) {
                            Pill(experiment.status.label)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = XpSpace.m), horizontalArrangement = Arrangement.SpaceBetween) {
                        OptionLabel(a.option.emoji, a.option.title, a.share, XpColors.Accent)
                        OptionLabel(b.option.emoji, b.option.title, b.share, XpColors.Ink, end = true)
                    }
                    Column(Modifier.padding(top = XpSpace.s)) { SplitBar(a, b) }
                }
            }
        }
        Reveal(1) { ConfidenceCard(result) }
        Reveal(2) {
            Column(Modifier.padding(horizontal = XpSpace.l)) {
                XpCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("95% 置信区间", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink, modifier = Modifier.weight(1f))
                    }
                    Text("须线越短越确定；两条须线重叠，说明两边差距可能只是巧合。", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
                    Column(Modifier.padding(top = XpSpace.m), verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                        CiWhisker(a, XpColors.Accent)
                        CiWhisker(b, XpColors.Ink)
                    }
                }
            }
        }
        Reveal(3) {
            Column(Modifier.padding(horizontal = XpSpace.l), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                Text(
                    "样本构成：人群样本 ${result.simulatedCount} 人 + 游戏表态 ${result.gameCount} 人。游戏表态是你亲自玩出来的，和真实消费分开统计。",
                    style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                )
                MerchantButton("邀请顾客再玩一轮", onPlay, primary = false)
                MerchantButton("下一步：看看三个可选动作", onNext)
            }
        }
    }
}

@Composable
private fun OptionLabel(emoji: String, title: String, share: Double, color: androidx.compose.ui.graphics.Color, end: Boolean = false) {
    Column(horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Text("$emoji $title", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, color = color)
    }
}

@Composable
private fun ConfidenceCard(result: ExperimentResult) {
    val tint by animateColorAsState(
        when (result.note.level) {
            Confidence.STABLE -> XpColors.Positive
            Confidence.EARLY -> XpColors.Warning
            Confidence.THIN -> XpColors.InkSecondary
        },
        label = "conf",
    )
    Row(
        Modifier.padding(horizontal = XpSpace.l).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(XpColors.Card).padding(XpSpace.l),
        horizontalArrangement = Arrangement.spacedBy(XpSpace.m), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("${result.sampleSize}", style = MaterialTheme.typography.titleLarge, color = tint)
            Text("人", style = MaterialTheme.typography.labelSmall, color = tint)
        }
        Column(Modifier.weight(1f)) {
            Text(result.note.text, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
            Text("置信度只是参考，不是保证。结论由你来下。", style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary)
        }
    }
}
