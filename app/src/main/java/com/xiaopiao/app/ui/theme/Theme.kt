package com.xiaopiao.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Colors = lightColorScheme(
    primary = XpColors.Accent,
    onPrimary = XpColors.Card,
    background = XpColors.Canvas,
    onBackground = XpColors.Ink,
    surface = XpColors.Card,
    onSurface = XpColors.Ink,
    onSurfaceVariant = XpColors.InkSecondary,
    outline = XpColors.Hairline,
)

private val Shapes = Shapes(
    small = RoundedCornerShape(XpRadius.s),
    medium = RoundedCornerShape(XpRadius.m),
    large = RoundedCornerShape(XpRadius.l),
)

private val Type = Typography(
    displayLarge = TextStyle(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    displayMedium = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

/** Monospace style for anything printed on a receipt. */
val ReceiptText = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = XpColors.Ink)

/** Compact monospace used for dense receipt rows (30 columns across the paper). */
val ReceiptRow = ReceiptText.copy(fontSize = 13.sp, color = XpColors.PaperInk)

/** Handwritten-ish italic for the AI's remark at the bottom of a receipt. */
val ReceiptHandwriting = TextStyle(
    fontFamily = FontFamily.Cursive,
    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
    fontSize = 15.sp,
    color = XpColors.PaperInk,
)

@Composable
fun XiaopiaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, shapes = Shapes, content = content)
}
