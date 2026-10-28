package com.xiaopiao.app.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptShareTest {
    private val full = listOf(
        ReceiptLine(LineKind.BLANK),
        ReceiptLine(LineKind.TITLE, "今日小票"),
        ReceiptLine(LineKind.SUBTITLE, "认真生活，留下凭证。"),
        ReceiptLine(LineKind.DASH),
        ReceiptLine(LineKind.META, "日期", "2026-10-28"),
        ReceiptLine(LineKind.DASH),
        ReceiptLine(LineKind.TOTAL, "今日支出", "161.50", 16150),
        ReceiptLine(LineKind.NOTICE, ReceiptLayout.NOTICE_TEXT),
        ReceiptLine(LineKind.DASH),
        ReceiptLine(LineKind.HEADER, "分类 / 明细", "金额"),
        ReceiptLine(LineKind.CATEGORY, "🍚 吃饭", "72.00", 7200),
        ReceiptLine(LineKind.ITEM, "麻辣烫", "36.00", 3600),
        ReceiptLine(LineKind.DASH),
        ReceiptLine(LineKind.STATS, "笔数 10", "较昨日 +2%", extra = 3),
        ReceiptLine(LineKind.DASH),
        ReceiptLine(LineKind.COMMENT, "「大手笔的一天」"),
        ReceiptLine(LineKind.STAMP_SPACE, "奶茶战神"),
        ReceiptLine(LineKind.FOOTER, ReceiptLayout.FOOTER_TEXT),
    )
    private val content = ShareContent(full, "奶茶战神", "2026-10-28")

    @Test fun briefKeepsHeroStampAndRemarkOnly() {
        val kinds = content.brief.map { it.kind }.toSet()
        assertTrue(LineKind.TOTAL in kinds && LineKind.STAMP_SPACE in kinds && LineKind.COMMENT in kinds)
        assertFalse(LineKind.ITEM in kinds || LineKind.CATEGORY in kinds || LineKind.META in kinds || LineKind.STATS in kinds)
    }

    @Test fun briefIsShorterAndHasNoDoubleDashes() {
        assertTrue(ReceiptLayout.heightDp(content.brief) < ReceiptLayout.heightDp(content.full))
        content.brief.zipWithNext().forEach { (a, b) -> assertFalse(a.kind == LineKind.DASH && b.kind == LineKind.DASH) }
    }

    @Test fun textOutputsDifferByLayout() {
        val f = ReceiptShare.textFor(content, briefMode = false)
        val b = ReceiptShare.textFor(content, briefMode = true)
        assertNotEquals(f, b)
        assertTrue(f.contains("麻辣烫") && !b.contains("麻辣烫"))
        assertTrue(b.contains("161.50") && b.contains("奶茶战神"))
        assertEquals(content.full, content.linesFor(false))
        assertEquals(content.brief, content.linesFor(true))
    }
}
