package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StatsAggregatorTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long = Calendar.getInstance().apply {
        clear(); set(year, month - 1, day, hour, 0, 0)
    }.timeInMillis

    /** Wednesday 2026-10-28 15:00 — the current week is 10/26–11/1. */
    private val now = at(2026, 10, 28, 15)

    private fun txn(
        cents: Long,
        at: Long,
        dir: Direction = Direction.OUT,
        category: Category = Category.MEAL,
        merchant: String = "瑞幸",
    ) = Txn(
        amountCents = cents, direction = dir, merchant = merchant, category = category,
        occurredAt = at, source = Source.MANUAL,
    )

    private val history = listOf(
        txn(1000, at(2026, 10, 26), category = Category.MEAL, merchant = "瑞幸"),
        txn(2000, at(2026, 10, 27), category = Category.MEAL, merchant = "瑞幸"),
        txn(3000, at(2026, 10, 28), category = Category.TRANSPORT, merchant = "地铁"),
        txn(500, at(2026, 10, 25), category = Category.DRINK, merchant = "奶茶店"),   // previous week
        txn(9000, at(2026, 10, 2), category = Category.SHOPPING, merchant = "淘宝"),  // same month, earlier week
        txn(40000, at(2026, 10, 3), dir = Direction.IN, category = Category.SOCIAL, merchant = "红包"),
        txn(7000, at(2026, 8, 1), category = Category.FUN, merchant = "电影"),        // same year, other month
        txn(11000, at(2025, 12, 31), category = Category.FUN, merchant = "电影"),     // previous year
    )

    @Test fun windowsAreHalfOpenAndAnchored() {
        assertEquals(at(2026, 10, 26, 0), StatsAggregator.window(StatsRange.Week, now).first)
        assertEquals(at(2026, 11, 2, 0), StatsAggregator.window(StatsRange.Week, now).second)
        assertEquals(at(2026, 10, 1, 0), StatsAggregator.window(StatsRange.Month, now).first)
        assertEquals(at(2026, 11, 1, 0), StatsAggregator.window(StatsRange.Month, now).second)
        assertEquals(at(2026, 1, 1, 0), StatsAggregator.window(StatsRange.Year, now).first)
        assertEquals(at(2027, 1, 1, 0), StatsAggregator.window(StatsRange.Year, now).second)
        val custom = StatsAggregator.window(StatsRange.Custom, now, at(2026, 10, 2), at(2026, 10, 5))
        assertEquals(at(2026, 10, 2, 0), custom.first)
        assertEquals(at(2026, 10, 6, 0), custom.second)
    }

    @Test fun previousWindowFollowsThePeriod() {
        assertEquals(at(2026, 10, 19, 0), StatsAggregator.previousWindow(StatsRange.Week, at(2026, 10, 26, 0), at(2026, 11, 2, 0)).first)
        assertEquals(at(2026, 9, 1, 0), StatsAggregator.previousWindow(StatsRange.Month, at(2026, 10, 1, 0), at(2026, 11, 1, 0)).first)
        assertEquals(at(2025, 1, 1, 0), StatsAggregator.previousWindow(StatsRange.Year, at(2026, 1, 1, 0), at(2027, 1, 1, 0)).first)
        val custom = StatsAggregator.previousWindow(StatsRange.Custom, at(2026, 10, 10, 0), at(2026, 10, 15, 0))
        assertEquals(at(2026, 10, 5, 0), custom.first)
        assertEquals(at(2026, 10, 10, 0), custom.second)
    }

    @Test fun titlesReadLikeTheReference() {
        assertEquals("10/26-11/1", StatsAggregator.periodTitle(StatsRange.Week, at(2026, 10, 26, 0), at(2026, 11, 2, 0)))
        assertEquals("2026年10月", StatsAggregator.periodTitle(StatsRange.Month, at(2026, 10, 1, 0), at(2026, 11, 1, 0)))
        assertEquals("2026年", StatsAggregator.periodTitle(StatsRange.Year, at(2026, 1, 1, 0), at(2027, 1, 1, 0)))
        assertEquals("9/29-10/28", StatsAggregator.periodTitle(StatsRange.Custom, at(2026, 9, 29, 0), at(2026, 10, 29, 0)))
    }

    @Test fun weekSnapshotHasFiveWeeksAndCountsOnlyItsWindow() {
        val s = StatsAggregator.build(history, now, StatsRange.Week, now)
        assertEquals(5, s.buckets.size)
        assertTrue(s.buckets.last().isCurrent)
        assertEquals("10/26-11/1", s.buckets.last().top)
        assertEquals(6000L, s.total)                 // 10/26–10/28 only
        assertEquals(3, s.activeDays)
        assertEquals(3, s.days)                       // Mon–Wed
        assertEquals(2000L, s.avgPerDay)
        assertEquals(-6000L, s.balance)               // no income inside this week
        assertEquals(500L, s.buckets[3].cents)        // last week: 10/19–10/25
        assertEquals(6000L, s.buckets[4].cents)       // current week
    }

    @Test fun monthSnapshotUsesOneBucketPerDay() {
        val s = StatsAggregator.build(history, now, StatsRange.Month, now)
        assertEquals(31, s.buckets.size)
        assertEquals("10/1", s.buckets.first().top)
        assertEquals(28, s.days)                      // elapsed days, not the whole month
        assertEquals(15500L, s.total)                 // 6000 + 9000 + 500 on 10/25
        assertEquals(40000L - 15500L, s.balance)
    }

    @Test fun yearSnapshotHasTwelveMonths() {
        val s = StatsAggregator.build(history, now, StatsRange.Year, now)
        assertEquals(12, s.buckets.size)
        assertEquals("1月", s.buckets.first().top)
        assertEquals(22500L, s.total)                 // October + August, 10/25 included
        assertEquals(0L, s.buckets[11].cents)
    }

    @Test fun deltaComparesWithThePreviousPeriod() {
        val week = StatsAggregator.build(history, now, StatsRange.Week, now)
        assertEquals(5500L, week.delta!!.cents)       // 6000 now vs 500 last week
        assertEquals(1100, week.delta!!.percent)      // +1100%
        assertEquals("较上周", week.deltaCaption)

        val month = StatsAggregator.build(history, now, StatsRange.Month, now)
        assertEquals("较上月", month.deltaCaption)

        val emptyPrev = StatsAggregator.build(history.filter { it.occurredAt > at(2026, 10, 20) }, now, StatsRange.Month, now)
        assertNull(emptyPrev.delta!!.percent)         // nothing to compare with → 「—」
    }

    @Test fun entriesRankByAmountAndShareSumsToOne() {
        val s = StatsAggregator.build(history, now, StatsRange.Month, now)
        assertEquals(listOf("购物", "吃饭", "出行", "奶茶咖啡"), s.entries.map { it.label })
        assertEquals(9000L, s.entries[0].cents)
        assertEquals(3000L, s.entries[1].cents)
        assertEquals(1f, s.entries.map { it.share }.reduce { a, b -> a + b }, 0.001f)
        assertEquals(2, s.entries[1].count)
        assertEquals("瑞幸", s.entries[1].detail)
    }

    @Test fun secondaryGroupingUsesMerchantsAndTheirDominantCategory() {
        val s = StatsAggregator.build(history, now, StatsRange.Month, now, grouping = StatsGrouping.Secondary)
        assertEquals(listOf("淘宝", "瑞幸", "地铁", "奶茶店"), s.entries.map { it.label })
        val coffee = s.entries.first { it.label == "瑞幸" }
        assertEquals(3000L, coffee.cents)
        assertEquals(2, coffee.count)
        assertEquals(Category.MEAL, coffee.category)
    }

    @Test fun incomeDirectionSwitchesTheTotals() {
        val s = StatsAggregator.build(history, now, StatsRange.Month, now, direction = Direction.IN)
        assertEquals(40000L, s.total)
        assertEquals(1, s.entries.size)
        assertTrue(s.entries.first().label == "人情红包")
    }

    @Test fun emptyPeriodIsReportedNotFaked() {
        val s = StatsAggregator.build(emptyList(), now, StatsRange.Month, now)
        assertTrue(s.isEmpty)
        assertEquals(0L, s.total)
        assertEquals(0L, s.avgPerDay)
        assertTrue(s.entries.isEmpty())
        assertNull(StatsNarrative.line(s))
    }

    @Test fun customRangeCountsEveryDayIncludingBothEnds() {
        val s = StatsAggregator.build(
            history, now, StatsRange.Custom, now,
            customFrom = at(2026, 10, 2), customTo = at(2026, 10, 5),
        )
        assertEquals(4, s.buckets.size)
        assertEquals(4, s.days)
        assertEquals(9000L, s.total)
    }

    @Test fun shiftingTheAnchorMovesWeekMonthAndYear() {
        assertEquals(at(2026, 10, 19, 0), StatsAggregator.shiftAnchor(StatsRange.Week, now, -1))
        assertEquals(at(2026, 9, 1, 0), StatsAggregator.shiftAnchor(StatsRange.Month, now, -1))
        assertEquals(at(2025, 1, 1, 0), StatsAggregator.shiftAnchor(StatsRange.Year, now, -1))
    }

    @Test fun thousandsFormattingMatchesTheKpiStyle() {
        assertEquals("29,951.02", StatsAggregator.grouped(2_995_102))
        assertEquals("120", StatsAggregator.grouped(12_000))
        assertEquals("0.05", StatsAggregator.grouped(5))
        assertEquals("-1,234.56", StatsAggregator.grouped(-123_456))
        assertEquals("0", StatsAggregator.grouped(0))
    }

    @Test fun narrativeStatesFactsOnly() {
        val s = StatsAggregator.build(history, now, StatsRange.Month, now)
        val line = StatsNarrative.line(s)!!
        assertTrue(line.contains("「购物」"))
        assertTrue(line.contains("记账 6 天"))
        assertTrue(line.endsWith("。"))
        assertFalse(line.contains("建议"))
    }
}
