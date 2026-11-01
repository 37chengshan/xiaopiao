package com.xiaopiao.app.ui.today

import com.xiaopiao.app.ui.tr

/** One selectable background for the share card (docs/DESIGN.md §7.6). Colours are opaque ARGB. */
data class CardGradient(val name: String, val top: Long, val bottom: Long) {
    /** Perceived brightness 0..1 of the middle of the gradient; decides ink for the card's own captions. */
    val luminance: Float get() = (luma(top) + luma(bottom)) / 2f
    val isDark: Boolean get() = luminance < 0.42f

    private fun luma(argb: Long): Float {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}

/** Twelve muted, warm-leaning presets. Index 0 (陶土) is the default. */
object GradientPresets {
    val all: List<CardGradient> = listOf(
        CardGradient(tr("陶土", "Terracotta"), 0xFFDE9A7B, 0xFFB8603F),
        CardGradient(tr("杏", "Apricot"), 0xFFF5D9B0, 0xFFE8A97A),
        CardGradient(tr("沙", "Sand"), 0xFFEADFC8, 0xFFC9B58F),
        CardGradient(tr("苔绿", "Moss"), 0xFFB3C7A9, 0xFF6F8F6B),
        CardGradient(tr("雾蓝", "Mist"), 0xFFBCCDDB, 0xFF7D9BB5),
        CardGradient(tr("暮紫", "Dusk"), 0xFFC2AFD0, 0xFF7E6A99),
        CardGradient(tr("墨夜", "Night"), 0xFF3E3E49, 0xFF16161C),
        CardGradient(tr("奶油", "Cream"), 0xFFFBF1DC, 0xFFEFDDB8),
        CardGradient(tr("桃", "Peach"), 0xFFF6CBC3, 0xFFE8938F),
        CardGradient(tr("青瓷", "Celadon"), 0xFFBBD8CE, 0xFF6FA89A),
        CardGradient(tr("灰岩", "Slate"), 0xFFC8C6C0, 0xFF8E8C86),
        CardGradient(tr("夜樱", "Night blossom"), 0xFF5E3D52, 0xFF2B1B2A),
    )

    fun byIndex(i: Int): CardGradient = all[i.coerceIn(0, all.lastIndex)]
}
