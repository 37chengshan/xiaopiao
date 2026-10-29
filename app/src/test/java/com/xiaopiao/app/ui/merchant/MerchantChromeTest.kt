package com.xiaopiao.app.ui.merchant

import com.xiaopiao.app.merchant.Change
import com.xiaopiao.app.merchant.InsightKind
import com.xiaopiao.app.merchant.InsightTone
import com.xiaopiao.app.merchant.MerchantAi
import com.xiaopiao.app.merchant.RevenueBook
import com.xiaopiao.app.merchant.RevenueEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MerchantChromeTest {
    @Test fun changeTextCoversTheFourCases() {
        assertEquals("没有可比数据", changeText(Change(nowCents = 500, beforeCents = 0)))
        assertEquals("持平", changeText(Change(nowCents = 500, beforeCents = 500)))
        assertEquals("多 10%", changeText(Change(nowCents = 1_100, beforeCents = 1_000)))
        assertEquals("少 20%", changeText(Change(nowCents = 800, beforeCents = 1_000)))
    }

    private fun day(d: Int): Long = Calendar.getInstance().apply { clear(); set(2026, 9, d, 0, 0, 0) }.timeInMillis

    @Test fun everyInsightCarriesAKindInsteadOfAnEmoji() {
        val book = (20..28).map { RevenueEntry(day(it), (3_000L - (it - 20) * 100) * 100, 90) }
        val out = MerchantAi.draft(book, day(28))
        assertTrue(out.isNotEmpty())
        // The week, weekday, ticket and best-day sentences are all computable from nine days of rows.
        assertTrue(out.map { it.kind }.containsAll(listOf(InsightKind.Week, InsightKind.Weekday, InsightKind.Best)))
        // A nine-day slide down must warn, with the decline tone — the owner decides what to do about it.
        assertTrue(out.any { it.kind == InsightKind.Decline && it.tone == InsightTone.Warn })
        assertEquals(out.size, out.map { it.kind }.distinct().size)
    }

    @Test fun shopMoneyStaysInCentsAndGroupsThousands() {
        assertEquals("¥2,611", RevenueBook.money(261_100))
    }
}
