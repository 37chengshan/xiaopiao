package com.xiaopiao.app.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import com.xiaopiao.app.ui.motion.XpTiming
import com.xiaopiao.app.ui.theme.XpColors

/** Press-down depth for every tappable surface (docs/APP-DESIGN.md §1: 0.96 + darken). */
private const val DefaultPressScale = 0.96f

/** How much ink is laid over a pressed surface. Small enough to read as "pushed", not as a state. */
private const val PressDimAlpha = 0.07f

/**
 * Presses down to [scaleDown] (0.96) and lays a 7% ink veil over itself while [interactionSource] is pressed.
 * The ramp is a 100ms tween so the first frame after `DOWN` already shows movement; releasing uses a
 * critically damped spring so the surface settles without a bounce. Under reduced motion the scale is
 * skipped and only the veil remains, because the veil is feedback rather than decoration.
 */
fun Modifier.pressableScale(
    interactionSource: MutableInteractionSource,
    scaleDown: Float = DefaultPressScale,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) tween(XpTiming.Press, easing = LinearOutSlowInEasing) else spring(dampingRatio = 1f, stiffness = 900f),
        label = "press",
    )
    val scale = if (reduced) 1f else 1f - (1f - scaleDown) * press
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .drawWithContent {
            drawContent()
            if (press > 0f) {
                drawRect(XpColors.Ink.copy(alpha = PressDimAlpha * press))
            }
        }
}

/**
 * Indication-free click with the press feedback above. Haptics are **off by default** (docs/APP-DESIGN.md
 * §7.5: a buzz only for a meaningful confirmation such as sending, answering or saving); pass
 * `haptic = true` on those, and leave the rest silent so the phone does not rattle while you browse.
 */
fun Modifier.pressableClick(
    enabled: Boolean = true,
    scaleDown: Float = DefaultPressScale,
    haptic: Boolean = false,
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
