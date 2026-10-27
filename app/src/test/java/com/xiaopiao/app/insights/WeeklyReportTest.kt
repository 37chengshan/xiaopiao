package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReportTest {
    @Test fun weekBoundariesAreMondayThroughSunday() {
        val h = listOf(
            txn(at(2026, 10, 18, 23, 59), 99.0), // Sunday of previous week
            txn(at(2026, 10, 19, 0, 0), 10.0),   // Monday 00:00 -> in
            txn(at(2026, 10, 25, 23, 59), 20.0), // Sunday 23:59 -> in
            txn(at(2026, 10, 26, 0, 0), 77.0),   // next Monday -> out
        )
        val r = WeeklyReportBuilder.build(at(2026, 10, 22), h, AFTER_WEEK + 3 * 86_400_000L)
        assertEquals(WEEK, r.weekStart)
        assertEquals(3000L, r.totalOutCents)
        assertEquals(2, r.txnCount)
        assertEquals(7, r.dayTotals.size)
        assertEquals(1000L, r.dayTotals[0])
        assertEquals(2000L, r.dayTotals[6])
    }

    @Test fun anyDayInTheWeekResolvesToTheSameWeek() {
        val h = listOf(txn(at(2026, 10, 20), 10.0))
        val a = WeeklyReportBuilder.build(at(2026, 10, 19, 0), h, AFTER_WEEK)
        val b = WeeklyReportBuilder.build(at(2026, 10, 25, 23, 30), h, AFTER_WEEK)
        assertEquals(a.weekStart, b.weekStart)
        assertEquals(a.totalOutCents, b.totalOutCents)
    }

    @Test fun comparesWithPreviousWeek() {
        val h = listOf(txn(at(2026, 10, 14), 100.0), txn(at(2026, 10, 21), 150.0))
        assertEquals(50, WeeklyReportBuilder.build(WEEK, h, AFTER_WEEK).vsPrevPercent)
        val down = listOf(txn(at(2026, 10, 14), 200.0), txn(at(2026, 10, 21), 150.0))
        assertEquals(-25, WeeklyReportBuilder.build(WEEK, down, AFTER_WEEK).vsPrevPercent)
        assertNull(WeeklyReportBuilder.build(WEEK, listOf(txn(at(2026, 10, 21), 150.0)), AFTER_WEEK).vsPrevPercent)
    }

    @Test fun unfinishedWeekComparesSameSpanOfLastWeek() {
        // now = Wednesday noon -> 3 started days. Last week Mon-Wed 100, Sat 900 (must not count).
        val h = listOf(txn(at(2026, 10, 13), 100.0), txn(at(2026, 10, 17), 900.0), txn(at(2026, 10, 20), 120.0))
        val r = WeeklyReportBuilder.build(WEEK, h, at(2026, 10, 21, 12))
        assertFalse(r.isComplete)
        assertEquals(3, r.startedDays)
        assertEquals(20, r.vsPrevPercent)
    }

    @Test fun futureDaysAreNotZeroSpendDays() {
        val r = WeeklyReportBuilder.build(WEEK, listOf(txn(at(2026, 10, 19), 10.0)), at(2026, 10, 21, 12))
        assertEquals(1, r.zeroSpendDays) // Tuesday is over and empty; Wednesday is still running
    }

    @Test fun topCategoriesBiggestAndStreak() {
        val h = listOf(
            txn(at(2026, 10, 19), 50.0, "A", Category.MEAL),
            txn(at(2026, 10, 20), 30.0, "B", Category.MEAL),
            txn(at(2026, 10, 21), 15.0, "C", Category.DRINK),
            txn(at(2026, 10, 23), 220.0, "D", Category.SHOPPING),
            txn(at(2026, 10, 24), 5.0, "E", Category.TRANSPORT),
            txn(at(2026, 10, 24), 1000.0, "工资", Category.OTHER, Direction.IN),
        )
        val r = WeeklyReportBuilder.build(WEEK, h, AFTER_WEEK)
        assertEquals(listOf(Category.SHOPPING, Category.MEAL, Category.DRINK), r.topCategories.map { it.category })
        assertEquals("D", r.biggest?.merchant)
        assertEquals(3, r.longestRecordStreak) // Mon-Wed, then a gap, then Fri-Sat = 2
        assertEquals(32000L, r.totalOutCents)
        assertEquals(22000f / 32000f, r.topCategories.first().share, 0.001f)
    }

    @Test fun picksAtMostTwoBadgesAndFallsBackGently() {
        val busy = (19..25).flatMap { d ->
            listOf(txn(at(2026, 10, d, 8), 9.0, "早餐", Category.MEAL), txn(at(2026, 10, d, 15), 16.0, "茶", Category.DRINK))
        }
        val r = WeeklyReportBuilder.build(WEEK, busy + oldTxn(), AFTER_WEEK)
        assertTrue(r.badges.size in 1..2)
        val plain = WeeklyReportBuilder.build(WEEK, listOf(txn(at(2026, 10, 21, 12), 40.0), txn(at(2026, 10, 22, 13), 10.0)), at(2026, 10, 22, 14))
        assertTrue(plain.badges.isNotEmpty())
    }

    @Test fun emptyWeekIsDesignedNotBroken() {
        val r = WeeklyReportBuilder.build(WEEK, emptyList(), AFTER_WEEK)
        assertTrue(r.isEmpty)
        assertTrue(r.badges.isEmpty())
        assertNull(r.biggest)
        assertNotNull(r.comment.takeIf { it.isNotBlank() })
        assertEquals(7, r.dayTotals.size)
    }
}
