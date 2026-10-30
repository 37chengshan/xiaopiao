package com.xiaopiao.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import kotlin.math.abs

/**
 * Segmented control with a liquid-glass thumb (tt 2026-10-30: 切换器带玻璃透镜). The labels are recorded once
 * and the thumb refracts them as it slides; it stretches with its speed and settles with a small overshoot.
 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    /** Capsule track and thumb instead of the 10dp-cornered ones. */
    pill: Boolean = false,
) {
    val haptics = rememberXpHaptics()
    val reduced = rememberReducedMotion()
    val inset = 2.dp
    val labels = rememberLayerBackdrop()
    val pos = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected, reduced) {
        if (reduced) pos.snapTo(selected.toFloat())
        else pos.animateTo(selected.toFloat(), spring(dampingRatio = 0.62f, stiffness = 420f))
    }
    // 0 at rest → 1 in flight; eases back so the thumb turns solid only once it has landed.
    val lift by animateFloatAsState(
        if (abs(pos.targetValue - pos.value) > 0.02f) 1f else 0f, tween(if (reduced) 0 else 160), label = "segLift",
    )
    BoxWithConstraints(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(if (pill) 50 else 28)).background(XpColors.Fill),
    ) {
        val count = options.size.coerceAtLeast(1)
        val segment = (maxWidth - inset * 2) / count
        // What the lens bends: the unselected labels on the bare track.
        Row(Modifier.fillMaxWidth().fillMaxHeight().layerBackdrop(labels).padding(inset)) {
            options.forEach { label ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = XpColors.InkSecondary, maxLines = 1)
                }
            }
        }
        Box(
            Modifier
                .padding(inset)
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = pos.value * segment.toPx()
                    // In flight the thumb lifts and swells into a lens; it squashes along its travel.
                    val stretch = (abs(pos.velocity) * 0.035f).coerceAtMost(0.18f)
                    scaleX = 1f + lift * 0.10f + stretch
                    scaleY = 1f + lift * 0.10f - stretch * 0.35f
                }
                .liquidGlass(
                    labels,
                    if (pill) RoundedCornerShape(50) else RoundedCornerShape(8.dp),
                    // At rest a solid thumb keeps the label crisp; moving, it clears so the labels bend through it.
                    // Raised, not Card: in dark the card tone is *below* the track and the thumb looked like a hole.
                    film = XpColors.Raised.copy(alpha = 0.85f - lift * 0.65f),
                    rim = Color.Black.copy(alpha = if (XpColors.isDark) 0.30f else 0.10f),
                    blur = 0.dp,
                    lensHeight = 8.dp,
                    lensAmount = 16.dp,
                    shadow = 6.dp,
                    fallback = XpColors.Raised,
                ),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(inset)) {
            options.forEachIndexed { i, label ->
                val color by animateColorAsState(
                    if (i == selected) XpColors.Ink else Color.Transparent, tween(180, easing = XpEase.out), label = "segText",
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, null) {
                            if (i != selected) { haptics.select(); onSelect(i) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
                }
            }
        }
    }
}
