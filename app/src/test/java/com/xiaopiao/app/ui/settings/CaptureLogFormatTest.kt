package com.xiaopiao.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureLogFormatTest {
    @Test fun parsedIsGood() {
        val l = CaptureLogFormat.describe("PARSED:alipay-expense")
        assertEquals(LogTone.GOOD, l.tone)
        assertTrue(l.text.contains("alipay-expense"))
    }

    @Test fun duplicateAndIgnoredAreNeutral() {
        assertEquals(LogTone.NEUTRAL, CaptureLogFormat.describe("DUPLICATE:x").tone)
        val ignored = CaptureLogFormat.describe("IGNORED:营销通知（红包）")
        assertEquals(LogTone.NEUTRAL, ignored.tone)
        assertTrue(ignored.text.contains("营销通知（红包）"))
    }

    @Test fun unparsedWarns() = assertEquals(LogTone.WARN, CaptureLogFormat.describe("UNPARSED").tone)

    @Test fun unknownTagPassesThrough() = assertEquals("???", CaptureLogFormat.describe("???").text)
}
