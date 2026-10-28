package com.xiaopiao.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors

/**
 * Tiny single-colour bar chart for hero numbers (docs/DESIGN.md §2b). No axes, no labels: it only shows shape.
 * The last bar is drawn in [highlight], the rest in [color] at low alpha.
 */
@Composable
fun MiniBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    color: Color = XpColors.InkTertiary,
    highlight: Color = XpColors.Accent,
) {
    val reduced = rememberReducedMotion()
    val grow = remember { androidx.compose.animation.core.Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(values, reduced) {
        if (reduced) grow.snapTo(1f) else { grow.snapTo(0f); grow.animateTo(1f, spring(0.85f, 420f)) }
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val progress = grow.value
        val max = values.max().coerceAtLeast(1f)
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            // Stagger left → right so the column reads as one gesture, not 14 little ones.
            val local = ((progress * (values.size + 2) - i) / 3f).coerceIn(0f, 1f)
            val h = (v / max * size.height * local).coerceAtLeast(3.dp.toPx() * local)
            drawRoundRect(
                if (i == values.lastIndex) highlight else color.copy(alpha = 0.35f),
                Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(w.coerceAtMost(4.dp.toPx())),
            )
        }
    }
}
