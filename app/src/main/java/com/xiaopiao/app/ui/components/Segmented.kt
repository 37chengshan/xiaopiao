package com.xiaopiao.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpMotion

/**
 * iOS-style segmented control: fill track with a surface thumb that slides to the selection on a
 * critically damped spring (retargets from its current position if tapped mid-flight).
 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberXpHaptics()
    val reduced = rememberReducedMotion()
    val inset = 2.dp
    BoxWithConstraints(
        modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(XpColors.Fill),
    ) {
        val count = options.size.coerceAtLeast(1)
        val segment = (maxWidth - inset * 2) / count
        val pos by animateFloatAsState(
            targetValue = selected.toFloat(),
            animationSpec = if (reduced) androidx.compose.animation.core.snap() else XpMotion.snappy(),
            label = "segThumb",
        )
        Box(
            Modifier
                .padding(inset)
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer { translationX = pos * segment.toPx() }
                .clip(RoundedCornerShape(8.dp))
                .background(XpColors.Card),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(inset)) {
            options.forEachIndexed { i, label ->
                val color by animateColorAsState(
                    if (i == selected) XpColors.Ink else XpColors.InkSecondary, tween(180, easing = XpEase.out), label = "segText",
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
