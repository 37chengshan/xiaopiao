package com.xiaopiao.app.ui.today

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
        CardGradient("陶土", 0xFFDE9A7B, 0xFFB8603F),
        CardGradient("杏", 0xFFF5D9B0, 0xFFE8A97A),
        CardGradient("沙", 0xFFEADFC8, 0xFFC9B58F),
        CardGradient("苔绿", 0xFFB3C7A9, 0xFF6F8F6B),
        CardGradient("雾蓝", 0xFFBCCDDB, 0xFF7D9BB5),
        CardGradient("暮紫", 0xFFC2AFD0, 0xFF7E6A99),
        CardGradient("墨夜", 0xFF3E3E49, 0xFF16161C),
        CardGradient("奶油", 0xFFFBF1DC, 0xFFEFDDB8),
        CardGradient("桃", 0xFFF6CBC3, 0xFFE8938F),
        CardGradient("青瓷", 0xFFBBD8CE, 0xFF6FA89A),
        CardGradient("灰岩", 0xFFC8C6C0, 0xFF8E8C86),
        CardGradient("夜樱", 0xFF5E3D52, 0xFF2B1B2A),
    )

    fun byIndex(i: Int): CardGradient = all[i.coerceIn(0, all.lastIndex)]
}
