package com.xiaopiao.app.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/** Presses down to [scaleDown] (0.97, critically damped, no bounce) while [interactionSource] is pressed. */
fun Modifier.pressableScale(interactionSource: MutableInteractionSource, scaleDown: Float = 0.97f): Modifier =
    composed {
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) scaleDown else 1f,
            animationSpec = spring(dampingRatio = 1f, stiffness = 900f),
            label = "pressScale",
        )
        graphicsLayer { scaleX = scale; scaleY = scale }
    }

/** Indication-free click with a press-scale and a light haptic tick. Use on any tappable surface. */
fun Modifier.pressableClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.97f,
    haptic: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val haptics = rememberXpHaptics()
    this
        .pressableScale(source, scaleDown)
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
            if (haptic) haptics.select()
            onClick()
        }
}
