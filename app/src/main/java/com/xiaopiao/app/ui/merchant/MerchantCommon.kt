package com.xiaopiao.app.ui.merchant

import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.IconTile
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.motion.xpRevealLayer
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay

val SpringSoft = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)
val SpringSnappy = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)

/** 0→1 progress that springs in once when first composed (and again when [key] changes). */
@Composable
fun rememberEnter(key: Any? = Unit, delayMs: Long = 0): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(delayMs)
        a.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow))
    }
    return a.value
}

/**
 * Staggered fade + rise on first appearance. Uses the shared [xpRevealLayer] (graphicsLayer only), so a
 * hidden block never leaves the layout — the page cannot reflow while it animates (that was the old
 * AnimatedVisibility version's flaw) and it respects reduced motion.
 */
@Composable
fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.xpRevealLayer(index = index, riseDp = 10.dp)) { content() }
}

/** Ink primary / fill secondary button (the shared XpButton), full width. */
@Composable
fun MerchantButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    clay: Boolean = false,
) {
    XpButton(
        text, onClick, modifier.fillMaxWidth(),
        style = if (clay) XpButtonStyle.Clay else if (primary) XpButtonStyle.Primary else XpButtonStyle.Secondary,
        enabled = enabled,
    )
}

/** Designed empty state: big emoji, friendly line, optional action. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    val t = rememberEnter()
    Column(
        Modifier.fillMaxWidth().padding(XpSpace.xl).scale(0.9f + 0.1f * t),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XpSpace.s),
    ) {
        IconTile(icon, size = 56.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Box(Modifier.padding(top = XpSpace.m)) { MerchantButton(action, onAction) }
        }
    }
}

@Composable
fun SelectableCard(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, SpringSnappy, label = "sel")
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(XpRadius.m))
            .background(if (selected) XpColors.AccentSoft else XpColors.Card)
            .clickable(interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(XpSpace.l),
    ) { content() }
}

