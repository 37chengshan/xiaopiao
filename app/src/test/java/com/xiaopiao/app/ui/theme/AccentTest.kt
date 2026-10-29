package com.xiaopiao.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentTest {
    @Test fun sevenPresetsWithBlueAsTheDefault() {
        assertEquals(7, AccentChoice.entries.size)
        assertEquals(AccentChoice.Blue, AccentChoice.Default)
        assertEquals(Color(0xFFFF8A00), AccentChoice.Orange.color(dark = false))
        assertEquals(Color(0xFFFF9F0A), AccentChoice.Orange.color(dark = true))
        assertEquals(Color(0xFFFFC400), AccentChoice.Yellow.color(false))
        assertEquals(Color(0xFF34C759), AccentChoice.Green.color(false))
        assertEquals(Color(0xFF0071E3), AccentChoice.Blue.color(false))
        assertEquals(Color(0xFF2997FF), AccentChoice.Blue.color(true))
        assertEquals(Color(0xFFFF375F), AccentChoice.Pink.color(false))
        assertEquals(Color(0xFFAF52DE), AccentChoice.Purple.color(false))
        assertEquals(AccentChoice.Ink, AccentChoice.fromId("ink"))
        assertEquals(AccentChoice.Default, AccentChoice.fromId("nope"))
        assertEquals(AccentChoice.Default, AccentChoice.fromId(null))
    }

    @Test fun textOnAccentReadsAtFourPointFiveOrBetterForEveryPresetLightAndDark() {
        for (dark in listOf(false, true)) for (c in AccentChoice.entries) {
            val p = paletteFor(DesignStyle.Apple, dark).withAccent(c)
            val r = Contrast.ratio(p.onAccent, p.accent)
            assertTrue("$c dark=$dark contrast $r", r >= Contrast.MIN_TEXT)
        }
    }

    @Test fun whiteIsKeptWhereItReadsAndSwappedForDarkWhereItDoesNot() {
        // Orange / green / yellow are light: white would fail, so dark text is chosen.
        assertNotEquals(Color.White, paletteFor(DesignStyle.Apple, false).withAccent(AccentChoice.Orange).onAccent)
        assertNotEquals(Color.White, paletteFor(DesignStyle.Apple, false).withAccent(AccentChoice.Green).onAccent)
        // Blue (light) and ink are deep enough that white reads.
        assertEquals(Color.White, paletteFor(DesignStyle.Apple, false).withAccent(AccentChoice.Blue).onAccent)
        assertEquals(Color.White, paletteFor(DesignStyle.Apple, false).withAccent(AccentChoice.Ink).onAccent)
    }

    @Test fun accentChangesOnlyAccentFieldsAndSoftIsAPaleTint() {
        val base = paletteFor(DesignStyle.Apple, false)
        val blue = base.withAccent(AccentChoice.Blue)
        assertEquals(Color(0xFF0071E3), blue.accent)
        assertEquals(base.ink, blue.ink)
        assertEquals(base.canvas, blue.canvas)
        assertEquals(base.primary, blue.primary)
        assertTrue(Contrast.luminance(blue.accentSoft) > Contrast.luminance(blue.accent))
        val dark = paletteFor(DesignStyle.Apple, true).withAccent(AccentChoice.Blue)
        assertTrue(Contrast.luminance(dark.accentSoft) < Contrast.luminance(dark.accent))
    }

    @Test fun claudeSkinIgnoresTheChosenAccent() {
        for (dark in listOf(false, true)) {
            assertEquals(paletteFor(DesignStyle.Claude, dark), resolvePalette(DesignStyle.Claude, dark, AccentChoice.Blue))
            assertEquals(paletteFor(DesignStyle.Claude, dark).accent, resolvePalette(DesignStyle.Claude, dark, AccentChoice.Green).accent)
        }
        assertEquals(AccentChoice.Green.color(false), resolvePalette(DesignStyle.Apple, false, AccentChoice.Green).accent)
    }

    @Test fun appleDefaultPaletteAlreadyWearsTheDefaultAccent() {
        for (dark in listOf(false, true)) {
            val raw = paletteFor(DesignStyle.Apple, dark)
            assertEquals(AccentChoice.Default.color(dark), raw.accent)
            assertEquals(raw.onAccent, raw.withAccent(AccentChoice.Default).onAccent)
        }
    }

    @Test fun highlightTokensHaveBothThemes() {
        assertEquals(Color(0xFFFFC93C), paletteFor(DesignStyle.Apple, false).highlight)
        assertEquals(Color(0xFFFFF6D6), paletteFor(DesignStyle.Apple, false).highlightSoft)
        assertEquals(Color(0xFFFFD60A), paletteFor(DesignStyle.Apple, true).highlight)
        assertEquals(Color(0xFF3A3110), paletteFor(DesignStyle.Apple, true).highlightSoft)
    }

    @Test fun merchantGreenKeepsLegibleText() {
        for (dark in listOf(false, true)) {
            val m = merchantPalette(paletteFor(DesignStyle.Apple, dark))
            assertTrue(Contrast.ratio(m.onAccent, m.accent) >= Contrast.MIN_TEXT)
        }
    }
}
