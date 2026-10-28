package com.xiaopiao.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors

/**
 * Tactile surface (docs/DESIGN.md §4b): a faint vertical gradient on [base], a 1dp inner highlight on the top
 * edge, and a soft drop shadow that shrinks while [pressed]. In dark mode the shadow is replaced by a hairline
 * glow edge, since black shadows vanish on dark canvases.
 */
fun Modifier.xpRaised(
    shape: Shape,
    base: Color,
    pressed: Boolean = false,
    elevation: Dp = 6.dp,
): Modifier {
    val dark = XpColors.isDark
    val top = lerp(base, Color.White, 0.07f)
    val bottom = lerp(base, Color.Black, 0.07f)
    val highlight = Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.35f), Color.Transparent))
    val lift = if (pressed) elevation / 4 else elevation
    return this
        .then(
            if (dark) Modifier.border(BorderStroke(0.5.dp, base.copy(alpha = 0.35f).let { c -> if (base.luminance() < 0.2f) Color.White.copy(alpha = if (pressed) 0.10f else 0.18f) else c }), shape)
            else Modifier.shadow(
                lift, shape, clip = false,
                ambientColor = base.copy(alpha = 0.28f), spotColor = base.copy(alpha = 0.28f),
            ),
        )
        .background(Brush.verticalGradient(listOf(top, bottom)), shape)
        .border(BorderStroke(1.dp, highlight), shape)
}

/** Light outline variant for secondary controls: fill base, white top highlight, hairline edge. */
fun Modifier.xpSoft(shape: Shape, base: Color): Modifier =
    this.background(base, shape)
        .border(BorderStroke(0.5.dp, XpColors.Hairline), shape)
