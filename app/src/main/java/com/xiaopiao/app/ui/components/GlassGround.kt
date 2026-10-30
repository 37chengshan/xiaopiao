package com.xiaopiao.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xiaopiao.app.ui.theme.XpColors

/** The ground that glass cards (e.g. [GroupedSection]) sample; null = draw opaque cards. */
val LocalGlass = staticCompositionLocalOf<Backdrop?> { null }

/**
 * A soft, theme-coloured ground for liquid-glass pages (tt: 设置整体换成液态玻璃): three large blurred blobs of the
 * colour theme's accent, highlight and tint over the grouped canvas. It stays put while the cards scroll over it,
 * so they refract real colour instead of flat grey. Only the ground is recorded, never the cards (no feedback loop).
 */
@Composable
fun GlassGround(modifier: Modifier = Modifier, strength: Float = 1f, content: @Composable () -> Unit) {
    val ground = rememberLayerBackdrop()
    val dark = XpColors.isDark
    val base = XpColors.CanvasGrouped
    val accent = XpColors.Accent
    val highlight = XpColors.Highlight
    val tint = XpColors.Info
    Box(modifier) {
        Box(
            Modifier.matchParentSize().layerBackdrop(ground).drawBehind {
                drawRect(base)
                fun blob(color: Color, alpha: Float, x: Float, y: Float, r: Float) {
                    val c = Offset(size.width * x, size.height * y)
                    val radius = size.width * r
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = c, radius = radius), radius, c)
                }
                val k = (if (dark) 0.6f else 1f) * strength
                blob(accent, 0.30f * k, 0.12f, 0.10f, 0.95f)
                blob(highlight, 0.34f * k, 0.95f, 0.42f, 0.80f)
                blob(tint, 0.26f * k, 0.18f, 0.86f, 0.90f)
            },
        )
        CompositionLocalProvider(LocalGlass provides ground) { content() }
    }
}
