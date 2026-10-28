package com.xiaopiao.app.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun colorScheme(p: XpPalette) = if (p.dark) {
    darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, background = p.canvas, onBackground = p.ink,
        surface = p.card, onSurface = p.ink, onSurfaceVariant = p.ink2, outline = p.hairline,
        surfaceVariant = p.surface2, error = p.negative,
    )
} else {
    lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, background = p.canvas, onBackground = p.ink,
        surface = p.card, onSurface = p.ink, onSurfaceVariant = p.ink2, outline = p.hairline,
        surfaceVariant = p.surface2, error = p.negative,
    )
}

private val Shapes = Shapes(
    small = RoundedCornerShape(XpRadius.s),
    medium = RoundedCornerShape(XpRadius.m),
    large = RoundedCornerShape(XpRadius.l),
)

private val Type = Typography(
    // Big numbers: tabular figures, tight tracking.
    displayLarge = TextStyle(fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.4).sp, fontFeatureSettings = "tnum"),
    displayMedium = TextStyle(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.1).sp, fontFeatureSettings = "tnum"),
    // Large Title: serif, Claude-style.
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

/** Monospace style for anything printed on a receipt. */
val ReceiptText get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = XpColors.PaperInk)

/** Compact monospace used for dense receipt rows (30 columns across the paper). */
val ReceiptRow get() = ReceiptText.copy(fontSize = 13.sp, color = XpColors.PaperInk)

/** Handwritten-ish italic for the AI's remark at the bottom of a receipt. */
val ReceiptHandwriting get() = TextStyle(
    fontFamily = FontFamily.Cursive,
    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
    fontSize = 15.sp,
    color = XpColors.PaperInk,
)

@Composable
fun XiaopiaoTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { ThemePrefs(context.applicationContext) }
    val mode by prefs.observeMode().collectAsState(initial = ThemeMode.System)
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val target = if (dark) DarkPalette else LightPalette
    // Ease the two backgrounds so a theme switch never flashes (docs/DESIGN.md §6).
    val canvas by animateColorAsState(target.canvas, tween(200), label = "canvas")
    val ink by animateColorAsState(target.ink, tween(200), label = "ink")
    val live = target.copy(canvas = canvas, ink = ink)
    // Written before `content` composes, so every screen reads the new palette in the same pass.
    if (XpPaletteState.current != live) XpPaletteState.current = live

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colorScheme(target), typography = Type, shapes = Shapes, content = content)
}
