package com.xiaopiao.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 主题配色 (tt: 5 themes × 2–3 colours, default blue with a little yellow). */
class ColorThemeTest {

    @Test fun fiveThemesThreeColoursEachBlueDefault() {
        assertEquals(5, ColorTheme.entries.size)
        ColorTheme.entries.forEach { assertEquals(3, it.swatches(dark = false).size) }
        assertEquals(ColorTheme.Sky, ColorTheme.Default)
        assertEquals(Color(0xFF0071E3), ColorTheme.Sky.accent.color(dark = false))
        assertEquals(Color(0xFFFFC93C), ColorTheme.Sky.highlight.color(dark = false))
    }

    @Test fun unknownOrOldIdsFallBackToDefault() {
        assertEquals(ColorTheme.Mint, ColorTheme.fromId("mint"))
        assertEquals(ColorTheme.Default, ColorTheme.fromId("blue"))
        assertEquals(ColorTheme.Default, ColorTheme.fromId(null))
    }

    @Test fun textOnEveryAccentIsLegibleInBothModes() {
        for (dark in listOf(false, true)) for (theme in ColorTheme.entries) {
            val p = resolvePalette(dark, theme)
            assertTrue("${theme.label} dark=$dark", Contrast.ratio(p.onAccent, p.accent) >= Contrast.MIN_TEXT)
        }
    }

    @Test fun themeCarriesAllThreeColoursIntoThePalette() {
        val p = resolvePalette(dark = true, theme = ColorTheme.Berry)
        assertEquals(ColorTheme.Berry.accent.color(true), p.accent)
        assertEquals(ColorTheme.Berry.highlight.color(true), p.highlight)
        assertEquals(ColorTheme.Berry.tint.color(true), p.info)
    }

    @Test fun merchantKeepsItsGreenWhateverTheTheme() {
        for (dark in listOf(false, true)) {
            val m = merchantPalette(resolvePalette(dark, ColorTheme.Sunset))
            assertTrue(Contrast.ratio(m.onAccent, m.accent) >= Contrast.MIN_TEXT)
            assertEquals(m.accent, merchantPalette(resolvePalette(dark, ColorTheme.Ink)).accent)
        }
    }
}
