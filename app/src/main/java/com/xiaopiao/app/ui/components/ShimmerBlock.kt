package com.xiaopiao.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius

/** Loading placeholder: surface-2 with a soft fill-coloured sweep (static under reduced motion). */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(XpRadius.s),
) {
    val reduced = rememberReducedMotion()
    val phase = rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerPhase",
    )
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                onDrawBehind {
                    val w = size.width
                    val x = phase.value * w
                    drawRect(XpColors.Shimmer)
                    if (!reduced) drawRect(
                        Brush.linearGradient(
                            listOf(XpColors.Shimmer, XpColors.ShimmerHighlight, XpColors.Shimmer),
                            start = Offset(x - w * 0.4f, 0f),
                            end = Offset(x + w * 0.4f, size.height),
                        ),
                    )
                }
            },
    )
}

/** Convenience full-width line placeholder. */
@Composable
fun ShimmerLine(height: Dp = 14.dp, modifier: Modifier = Modifier) {
    ShimmerBlock(modifier.fillMaxWidth().height(height))
}
