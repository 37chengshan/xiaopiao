package com.xiaopiao.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.pow

/**
 * The user-pickable accent (设置 → 外观 → 强调色, modelled on ChatGPT's 个性化 → 强调色). Apple skin only:
 * Claude keeps its terracotta. Every preset has a light and a dark value; [Blue] (Apple's action blue) is the default; [Orange] stays on the palette.
 */
enum class AccentChoice(val id: String, val label: String, val light: Long, val dark: Long) {
    Orange("orange", "橙", 0xFFFF8A00, 0xFFFF9F0A),
    Yellow("yellow", "黄", 0xFFFFC400, 0xFFFFD60A),
    Green("green", "绿", 0xFF34C759, 0xFF30D158),
    Blue("blue", "蓝", 0xFF0071E3, 0xFF2997FF),
    Pink("pink", "粉", 0xFFFF375F, 0xFFFF375F),
    Purple("purple", "紫", 0xFFAF52DE, 0xFFBF5AF2),
    Ink("ink", "墨黑", 0xFF1D1D1F, 0xFFF5F5F7);

    fun color(dark: Boolean): Color = Color(if (dark) this.dark else light)

    companion object {
        val Default = Blue
        fun fromId(id: String?): AccentChoice = entries.firstOrNull { it.id == id } ?: Default
    }
}

/** WCAG relative luminance / contrast, used to pick legible text for whatever accent the user chose. */
object Contrast {
    private fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    const val MIN_TEXT = 4.5

    private val Candidates = listOf(Color.White, Color(0xFF1D1D1F), Color.Black)

    /** White when it reads (≥ 4.5:1), otherwise the first dark that does; if nothing passes, the best of the three. */
    fun onAccent(accent: Color): Color =
        Candidates.firstOrNull { ratio(it, accent) >= MIN_TEXT } ?: Candidates.maxBy { ratio(it, accent) }
}

/** Accent, its soft tint and the text colour that sits on it — the only fields an accent choice changes. */
fun XpPalette.withAccent(choice: AccentChoice): XpPalette {
    val accent = choice.color(dark)
    val base = if (dark) Color(0xFF1C1C1E) else Color.White
    return copy(
        accent = accent,
        accentSoft = lerp(base, accent, if (dark) 0.24f else 0.14f),
        onAccent = Contrast.onAccent(accent),
    )
}

/** The palette the app wears: the skin's own, plus the user's accent when the skin is Apple. */
fun resolvePalette(style: DesignStyle, dark: Boolean, accent: AccentChoice): XpPalette {
    val base = paletteFor(style, dark)
    return if (style == DesignStyle.Apple) base.withAccent(accent) else base
}
