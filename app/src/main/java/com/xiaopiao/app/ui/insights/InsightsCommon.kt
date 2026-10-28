package com.xiaopiao.app.ui.insights

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.LargeTitle
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.components.rememberNavScroll
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/** Everything an insights page needs: the (real or demo) ledger and a fixed "now". */
class InsightsInput(
    val history: List<com.xiaopiao.app.domain.Txn>,
    val now: Long,
    val demo: Boolean,
)

/** Springy press-scale + haptic tick. Used for every tappable card / chip on these screens. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounce",
    )
    val haptic = LocalHapticFeedback.current
    return this.scale(scale).clickable(source, null, enabled = enabled) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
}

/** Sub-page frame: back chip, large title, scrollable body. */
@Composable
fun InsightsPage(
    title: String,
    subtitle: String?,
    demo: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scroll = rememberScrollState()
    NavScaffold(title, rememberNavScroll(scroll), onBack = onBack, backLabel = "洞察", demo = demo) { top ->
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = XpSpace.xxl)) {
            LargeTitle(title, subtitle)
            Column(
                Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.s),
                verticalArrangement = Arrangement.spacedBy(XpSpace.m),
            ) { content() }
        }
    }
}

@Composable
fun DemoBanner(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        Pill("演示数据")
        Text("下面的内容来自示例账本，不是你的真实花销", style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
    }
}

/** Designed empty state: big emoji, a playful line, optional action. */
@Composable
fun InsightsEmpty(emoji: String, title: String, hint: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    XpCard {
        Column(Modifier.fillMaxWidth().padding(vertical = XpSpace.l), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.displayLarge)
            Text(title, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink, modifier = Modifier.padding(top = XpSpace.s))
            Text(
                hint, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = XpSpace.xs),
            )
            if (actionLabel != null && onAction != null) {
                PrimaryChip(actionLabel, Modifier.padding(top = XpSpace.m), onAction)
            }
        }
    }
}

@Composable
fun PrimaryChip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.bounceClick(onClick = onClick).clip(RoundedCornerShape(50)).background(XpColors.Accent)
            .padding(horizontal = XpSpace.l, vertical = XpSpace.s),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = XpColors.OnAccent) }
}

/** Selectable chip (filter / segmented option). */
@Composable
fun SelectChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) XpColors.AccentSoft else XpColors.Fill
    val fg = if (selected) XpColors.Accent else XpColors.Ink
    Box(
        modifier.bounceClick(onClick = onClick).clip(RoundedCornerShape(50)).background(bg)
            .padding(horizontal = XpSpace.m, vertical = XpSpace.s),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = fg) }
}

/** Two-or-three option segmented control on a soft track. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.s)).background(XpColors.Fill).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(XpRadius.s - 2.dp))
                    .background(if (on) XpColors.Card else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(value) }.padding(vertical = XpSpace.s),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) XpColors.Ink else XpColors.InkSecondary)
            }
        }
    }
}

/** Counts up to [target] with an ease-out tween. */
@Composable
fun animatedCents(target: Cents): Cents {
    val v by animateFloatAsState(target.toFloat(), tween(900), label = "cents")
    return v.toLong()
}

@Composable
fun BigMoney(cents: Cents, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = XpColors.Ink) {
    Text("¥${animatedCents(cents).yuan()}", modifier, style = MaterialTheme.typography.displayLarge, color = color)
}
