package com.xiaopiao.app.ui.today

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import com.xiaopiao.app.ui.theme.XpColors

/** Which parts of the day get printed (docs/APP-DESIGN.md §3.1: 打印内容开关). */
@Immutable
data class ReceiptContent(
    val items: Boolean = true,
    val total: Boolean = true,
    val bars: Boolean = false,
    val remark: Boolean = true,
    val stamp: Boolean = true,
    val weekCompare: Boolean = true,
    val barcode: Boolean = false,
) {
    /** Bit mask for DataStore; the order of [FLAGS] is the stored format. */
    fun toBits(): Int = FLAGS.foldIndexed(0) { i, acc, f -> if (f.get(this)) acc or (1 shl i) else acc }

    /** Which named preset this is, or null when the switches were mixed by hand. */
    val preset: ReceiptPreset? get() = ReceiptPreset.entries.firstOrNull { it.content == this }

    fun withFlag(flag: Flag, on: Boolean): ReceiptContent = when (flag) {
        Flag.Items -> copy(items = on)
        Flag.Total -> copy(total = on)
        Flag.Bars -> copy(bars = on)
        Flag.Remark -> copy(remark = on)
        Flag.Stamp -> copy(stamp = on)
        Flag.WeekCompare -> copy(weekCompare = on)
        Flag.Barcode -> copy(barcode = on)
    }

    enum class Flag(val label: String, val get: (ReceiptContent) -> Boolean) {
        Items("逐笔明细", { it.items }),
        Total("合计", { it.total }),
        Bars("分类占比条", { it.bars }),
        Remark("AI 点评", { it.remark }),
        Stamp("头衔印章", { it.stamp }),
        WeekCompare("本周对比", { it.weekCompare }),
        Barcode("条码流水号", { it.barcode }),
    }

    companion object {
        private val FLAGS = Flag.entries.map { it }

        /** What a bare [ReceiptLayout.build] prints: everything except the optional bars and barcode. */
        val PLAIN = ReceiptContent()

        fun fromBits(bits: Int): ReceiptContent = FLAGS.foldIndexed(PLAIN) { i, acc, f -> acc.withFlag(f, bits and (1 shl i) != 0) }
    }
}

/** 完整 / 简版: the two one-tap presets. 完整 = every detail; 简版 = hero total, top categories and the stamp. */
enum class ReceiptPreset(val label: String, val content: ReceiptContent) {
    Full("完整", ReceiptContent(items = true, total = true, bars = false, remark = true, stamp = true, weekCompare = true, barcode = true)),
    Brief("简版", ReceiptContent(items = false, total = true, bars = true, remark = false, stamp = true, weekCompare = false, barcode = false)),
}

/** Paper colour presets. [paper] null = follow the app's own paper tokens (the classic thermal look). */
enum class PaperTheme(val id: String, val label: String, val paper: Long?, val shade: Long?, val ink: Long?, val stamp: Long?) {
    Classic("classic", "经典热敏", null, null, null, null),
    Kraft("kraft", "牛皮纸", 0xFFD9B98A, 0xFFC4A06C, 0xFF3A2A18, 0xFF9E2B1F),
    Mint("mint", "薄荷", 0xFFE6F4EC, 0xFFCFE6D8, 0xFF1F3D30, 0xFFC0392B),
    Sakura("sakura", "樱花", 0xFFFCE9EE, 0xFFF3CFD9, 0xFF4A2630, 0xFFB5173F),
    Night("night", "夜间", 0xFF1C1C1E, 0xFF2C2C2E, 0xFFF2F2F2, 0xFFFF6B5E);

    companion object {
        fun fromId(id: String?): PaperTheme = entries.firstOrNull { it.id == id } ?: Classic
    }
}

/** The typeface the paper is set in: thermal-printer mono (default), system sans, or serif. */
enum class ReceiptFont(val id: String, val label: String) {
    Thermal("thermal", "等宽热敏"),
    Sans("sans", "无衬线"),
    Serif("serif", "衬线");

    val family: FontFamily
        get() = when (this) {
            Thermal -> FontFamily.Monospace
            Sans -> FontFamily.SansSerif
            Serif -> FontFamily.Serif
        }

    companion object {
        fun fromId(id: String?): ReceiptFont = entries.firstOrNull { it.id == id } ?: Thermal
    }
}

/** Everything the 小票页 lets the user change; persisted by [ReceiptPrefs]. */
@Immutable
data class ReceiptSettings(
    val theme: PaperTheme = PaperTheme.Classic,
    /** ARGB of a custom ink colour, or null for the theme's own ink. */
    val customInk: Int? = null,
    val content: ReceiptContent = ReceiptPreset.Full.content,
    val font: ReceiptFont = ReceiptFont.Thermal,
    /** Ledger category ids ([com.xiaopiao.app.domain.CategoryDef.id]) left off the paper. Empty = everything is printed (the default); new categories start included. */
    val excluded: Set<String> = emptySet(),
) {
    fun colors(): PaperColors = PaperColors.resolve(theme, customInk)

    fun includes(categoryId: String): Boolean = categoryId !in excluded
}

/** Resolved colours the paper is drawn with. Read through [LocalPaperColors]. */
@Immutable
data class PaperColors(val paper: Color, val shade: Color, val ink: Color, val stamp: Color, val dark: Boolean) {
    val inkSoft: Color get() = ink.copy(alpha = 0.66f)
    val inkFaint: Color get() = ink.copy(alpha = 0.40f)

    companion object {
        fun resolve(theme: PaperTheme, customInk: Int?): PaperColors {
            val base = PaperColors(
                paper = theme.paper?.let { Color(it) } ?: XpColors.Paper,
                shade = theme.shade?.let { Color(it) } ?: XpColors.PaperShade,
                ink = theme.ink?.let { Color(it) } ?: XpColors.PaperInk,
                stamp = theme.stamp?.let { Color(it) } ?: XpColors.Stamp,
                dark = theme == PaperTheme.Night,
            )
            return if (customInk != null) base.copy(ink = Color(customInk)) else base
        }

        /** Hue slider → ink: deep, slightly saturated so it still reads like thermal print on paper. */
        fun inkFromHue(hue: Float): Int = Color.hsv(hue.coerceIn(0f, 360f), 0.62f, 0.42f).toArgb()
    }
}

/** The paper's colours for everything drawn on it (lines, stamp, background). Defaults to the app's classic tokens. */
val LocalPaperColors = compositionLocalOf { PaperColors.resolve(PaperTheme.Classic, null) }

/** The typeface for everything printed on the paper (lines, share image). */
val LocalReceiptFont = compositionLocalOf { ReceiptFont.Thermal }
