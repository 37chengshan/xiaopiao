package com.xiaopiao.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import com.xiaopiao.app.ui.theme.XpColors

/**
 * The one liquid-glass recipe for things that float over content (tt 2026-10-29: 克制地把内容里的一些卡片/UI 变成液态
 * 玻璃). Glass only earns its cost when something moves behind it, so use it for floating layers — buttons, bars,
 * toasts, popovers — never for a card lying flat on a plain background (it would read as a grey card).
 *
 * [backdrop] must record a *sibling* of this element (a page's list, never a parent that contains this element);
 * null draws the opaque fallback, so callers do not need two code paths.
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop?,
    shape: Shape,
    film: Color = XpColors.Card.copy(alpha = if (XpColors.isDark) 0.40f else 0.55f),
    rim: Color = Color.Black.copy(alpha = if (XpColors.isDark) 0.24f else 0.06f),
    blur: Dp = 10.dp,
    lensHeight: Dp = 8.dp,
    lensAmount: Dp = 12.dp,
    shadow: Dp = 16.dp,
    fallback: Color = XpColors.Card,
): Modifier =
    if (backdrop == null) {
        this.clip(shape).background(fallback)
    } else {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blur.toPx())
                lens(lensHeight.toPx(), lensAmount.toPx())
            },
            shadow = { Shadow(radius = shadow, color = rim) },
            onDrawSurface = { drawRect(film) },
        )
    }
