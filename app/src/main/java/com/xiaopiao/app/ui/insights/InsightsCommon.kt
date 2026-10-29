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
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.Pill
import androidx.compose.foundation.layout.size
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
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

/** 子页滚动容器。标题与返回由外层 NavScaffold 负责，这里只画一行说明，避免两处标题打架。 */
@Composable
fun InsightsPage(
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        Column(
            Modifier.padding(horizontal = XpSpace.l, vertical = XpSpace.s),
            verticalArrangement = Arrangement.spacedBy(XpSpace.m),
        ) {
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
            }
            content()
        }
    }
}

/** Designed empty state: big emoji, a playful line, optional action. */
@Composable
fun InsightsEmpty(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    hint: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    XpCard {
        Column(Modifier.fillMaxWidth().padding(vertical = XpSpace.l), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = XpColors.InkTertiary, modifier = Modifier.size(40.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink, modifier = Modifier.padding(top = XpSpace.s))
            Text(
                hint, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = XpSpace.xs),
            )
            if (actionLabel != null && onAction != null) {
                XpButton(
                    actionLabel, onAction,
                    style = XpButtonStyle.Secondary, size = XpButtonSize.Small,
                    modifier = Modifier.padding(top = XpSpace.m),
                )
            }
        }
    }
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
    MoneyText(animatedCents(cents).yuan(), modifier, style = MaterialTheme.typography.displayLarge, color = color)
}
