package com.xiaopiao.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.pow

/** One colour of a theme, with its light and dark value. */
data class ThemeTone(val light: Long, val dark: Long) {
    fun color(dark: Boolean): Color = Color(if (dark) this.dark else light)
}

/**
 * 主题配色 (设置 → 外观): five hand-paired themes, each three colours — [accent] (the one interactive colour),
 * [highlight] (warm emphasis surfaces such as the ledger's summary card) and [tint] (icon blocks and secondary charts).
 * tt: 「每种应该是 2–3 个色彩然后你搭配 5 种用户选择」; the default is blue with a little yellow (tt#72).
 */
enum class ColorTheme(val id: String, val label: String, val accent: ThemeTone, val highlight: ThemeTone, val tint: ThemeTone) {
    Sky("sky", "晴空", ThemeTone(0xFF0071E3, 0xFF2997FF), ThemeTone(0xFFFFC93C, 0xFFFFD60A), ThemeTone(0xFF5AC8FA, 0xFF64D2FF)),
    Sunset("sunset", "暖阳", ThemeTone(0xFFF27A1A, 0xFFFF9F0A), ThemeTone(0xFFFFD166, 0xFFFFD60A), ThemeTone(0xFFFF6B5B, 0xFFFF7A6B)),
    Mint("mint", "薄荷", ThemeTone(0xFF12A07A, 0xFF30D19A), ThemeTone(0xFFF2D45C, 0xFFFFE066), ThemeTone(0xFF3FB8C4, 0xFF5AD1DB)),
    Berry("berry", "莓果", ThemeTone(0xFFE0457B, 0xFFFF5C8D), ThemeTone(0xFFFFB86B, 0xFFFFC27A), ThemeTone(0xFFA78BFA, 0xFFB9A3FF)),
    Ink("ink", "夜墨", ThemeTone(0xFF1D1D1F, 0xFFF5F5F7), ThemeTone(0xFFD9A441, 0xFFE8B85A), ThemeTone(0xFF6B7A90, 0xFF8E9BB0));

    /** The three swatches, in the order the picker shows them. */
    fun swatches(dark: Boolean): List<Color> = listOf(accent.color(dark), highlight.color(dark), tint.color(dark))

    companion object {
        val Default = Sky
        fun fromId(id: String?): ColorTheme = entries.firstOrNull { it.id == id } ?: Default
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

/** The fields a theme changes: accent, highlight and tint, each with its soft wash, plus the text that sits on the accent. */
fun XpPalette.withTheme(theme: ColorTheme): XpPalette {
    val base = if (dark) Color(0xFF1C1C1E) else Color.White
    val accent = theme.accent.color(dark)
    val highlight = theme.highlight.color(dark)
    val tint = theme.tint.color(dark)
    return copy(
        accent = accent,
        accentSoft = lerp(base, accent, if (dark) 0.24f else 0.14f),
        onAccent = Contrast.onAccent(accent),
        highlight = highlight,
        highlightSoft = lerp(base, highlight, 0.2f),
        info = tint,
        infoSoft = lerp(base, tint, if (dark) 0.2f else 0.1f),
    )
}

/** The palette the app wears: the light or dark base plus the user's theme. */
fun resolvePalette(dark: Boolean, theme: ColorTheme): XpPalette = paletteFor(dark).withTheme(theme)
