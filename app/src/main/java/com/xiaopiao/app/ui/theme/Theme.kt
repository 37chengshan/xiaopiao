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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun colorScheme(p: XpPalette) = if (p.dark) {
    darkColorScheme(
        primary = p.primary, onPrimary = p.onPrimary, background = p.canvas, onBackground = p.ink,
        surface = p.card, onSurface = p.ink, onSurfaceVariant = p.ink2, outline = p.hairline,
        surfaceVariant = p.surface2, error = p.negative,
    )
} else {
    lightColorScheme(
        primary = p.primary, onPrimary = p.onPrimary, background = p.canvas, onBackground = p.ink,
        surface = p.card, onSurface = p.ink, onSurfaceVariant = p.ink2, outline = p.hairline,
        surfaceVariant = p.surface2, error = p.negative,
    )
}

/** Apple: SF-like Inter, tight tracking, no serif. Claude: serif display over a humanist body. */
private fun typographyFor(style: DesignStyle): Typography = when (style) {
    DesignStyle.Apple -> Typography(
        displayLarge = TextStyle(fontFamily = XpFonts.sans, fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.4).sp, fontFeatureSettings = "tnum"),
        displayMedium = TextStyle(fontFamily = XpFonts.sans, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.1).sp, fontFeatureSettings = "tnum"),
        headlineLarge = TextStyle(fontFamily = XpFonts.sans, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = XpFonts.sans, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        titleLarge = TextStyle(fontFamily = XpFonts.sans, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleMedium = TextStyle(fontFamily = XpFonts.sans, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleSmall = TextStyle(fontFamily = XpFonts.sans, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontFamily = XpFonts.sans, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
        bodyMedium = TextStyle(fontFamily = XpFonts.sans, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.1).sp),
        bodySmall = TextStyle(fontFamily = XpFonts.sans, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = XpFonts.sans, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontFamily = XpFonts.sans, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = XpFonts.sans, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    )
    DesignStyle.Claude -> Typography(
        displayLarge = TextStyle(fontFamily = XpFonts.sans, fontSize = 48.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.4).sp, fontFeatureSettings = "tnum"),
        displayMedium = TextStyle(fontFamily = XpFonts.sans, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.1).sp, fontFeatureSettings = "tnum"),
        headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.3).sp),
        titleLarge = TextStyle(fontFamily = XpFonts.sans, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.Medium),
        titleMedium = TextStyle(fontFamily = XpFonts.sans, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
        titleSmall = TextStyle(fontFamily = XpFonts.sans, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontFamily = XpFonts.sans, fontSize = 16.sp, lineHeight = 25.sp),
        bodyMedium = TextStyle(fontFamily = XpFonts.sans, fontSize = 15.sp, lineHeight = 23.sp),
        bodySmall = TextStyle(fontFamily = XpFonts.sans, fontSize = 13.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontFamily = XpFonts.sans, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontFamily = XpFonts.sans, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = XpFonts.sans, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
}

private fun shapesFor(style: DesignStyle) = if (style == DesignStyle.Apple) {
    Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(11.dp), large = RoundedCornerShape(18.dp))
} else {
    Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp))
}

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
    val style by prefs.observeStyle().collectAsState(initial = DesignStyle.Apple)
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val target = paletteFor(style, dark)
    // Ease the two backgrounds so a theme switch never flashes (docs/DESIGN.md §6).
    val canvas by animateColorAsState(target.canvas, tween(200), label = "canvas")
    val ink by animateColorAsState(target.ink, tween(200), label = "ink")
    val live = target.copy(canvas = canvas, ink = ink)
    // Written before `content` composes, so every screen reads the new palette in the same pass.
    if (XpPaletteState.current != live) XpPaletteState.current = live
    if (XpPaletteState.style != style) XpPaletteState.style = style

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
    MaterialTheme(colorScheme = colorScheme(target), typography = remember(style) { typographyFor(style) }, shapes = remember(style) { shapesFor(style) }, content = content)
}
