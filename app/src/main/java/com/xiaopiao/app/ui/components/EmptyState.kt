package com.xiaopiao.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/** Friendly empty/error placeholder: bobbing emoji, title, body and an optional action button. */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val bob = rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)), RepeatMode.Reverse),
        label = "bobY",
    )
    Column(
        modifier.fillMaxWidth().padding(horizontal = XpSpace.xl, vertical = XpSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XpSpace.s),
    ) {
        Text(
            emoji,
            fontSize = 52.sp,
            modifier = Modifier.graphicsLayer {
                translationY = -8.dp.toPx() * bob.value
                rotationZ = (bob.value - 0.5f) * 6f
            },
        )
        Text(title, style = MaterialTheme.typography.titleLarge, color = XpColors.Ink, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            XpButton(actionLabel, onAction, style = XpButtonStyle.Secondary, modifier = Modifier.padding(top = XpSpace.s))
        }
    }
}
