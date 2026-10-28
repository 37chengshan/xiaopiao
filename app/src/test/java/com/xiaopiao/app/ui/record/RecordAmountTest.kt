package com.xiaopiao.app.ui.record

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordAmountTest {

    @Test
    fun `digits append`() {
        var text = ""
        listOf("3", "2").forEach { text = RecordAmount.press(text, it) }
        assertEquals("32", text)
        assertEquals(3_200L, RecordAmount.toCents(text))
    }

    @Test
    fun `leading zero is replaced`() {
        assertEquals("5", RecordAmount.press("0", "5"))
    }

    @Test
    fun `one dot only`() {
        assertEquals("1.", RecordAmount.press("1", "."))
        assertEquals("1.5", RecordAmount.press("1.", "5"))
        assertEquals("1.5", RecordAmount.press("1.5", "."))
    }

    @Test
    fun `dot on empty starts at zero`() {
        assertEquals("0.", RecordAmount.press("", "."))
    }

    @Test
    fun `at most two decimals`() {
        assertEquals("1.25", RecordAmount.press("1.25", "9"))
    }

    @Test
    fun `backspace removes the last character`() {
        assertEquals("1.2", RecordAmount.press("1.25", RecordAmount.BACKSPACE))
        assertEquals("", RecordAmount.press("1", RecordAmount.BACKSPACE))
    }

    @Test
    fun `fraction parses exactly into cents`() {
        assertEquals(1_850L, RecordAmount.toCents("18.5"))
        assertEquals(1_855L, RecordAmount.toCents("18.55"))
        assertEquals(5L, RecordAmount.toCents("0.05"))
        assertEquals(0L, RecordAmount.toCents("0."))
        assertEquals(0L, RecordAmount.toCents(""))
        assertEquals(1_800L, RecordAmount.toCents("18"))
    }

    @Test
    fun `large amount stays exact`() {
        assertEquals(999_999_999L, RecordAmount.toCents("9999999.99"))
    }

    @Test
    fun `display substitutes zero`() {
        assertEquals("0", RecordAmount.display(""))
        assertEquals("12.5", RecordAmount.display("12.5"))
    }
}
