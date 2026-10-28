package com.xiaopiao.app.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RevenueBookTest {
    private fun day(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear(); set(year, month - 1, day, 0, 0, 0)
    }.timeInMillis

    private val today = day(2026, 10, 28) // Wednesday

    private fun entry(dayMs: Long, yuan: Long, orders: Int = 100, source: RevenueSource = RevenueSource.MANUAL) =
        RevenueEntry(dayMs, yuan * 100, orders, source = source)

    @Test fun normalizeKeepsLastRowPerDayAndSorts() {
        val book = RevenueBook.normalize(
            listOf(
                entry(day(2026, 10, 27), 100),
                entry(day(2026, 10, 26), 300),
                entry(day(2026, 10, 27), 200),
                RevenueEntry(day(2026, 10, 25), -1, 2),
            ),
        )
        assertEquals(listOf(day(2026, 10, 26), day(2026, 10, 27)), book.map { it.dayStart })
        assertEquals(200_00L, RevenueBook.entryFor(book, day(2026, 10, 27))!!.revenueCents)
    }

    @Test fun averageTicketIsWeightedByOrders() {
        val book = listOf(entry(day(2026, 10, 27), 1_000, 100), entry(day(2026, 10, 28), 2_000, 200))
        assertEquals((3_000 * 100.0 / 300).toLong(), RevenueBook.averageTicketCents(book))
        assertEquals(0L, RevenueBook.averageTicketCents(1_000_00, 0))
        assertEquals(33_33L, RevenueBook.averageTicketCents(1_000_00, 30))
    }

    @Test fun todayVersusYesterdayUsesMissingDayAsZero() {
        val book = listOf(entry(today, 2_000), entry(RevenueBook.plusDays(today, -1), 1_600))
        val change = RevenueBook.todayVsYesterday(book, today)
        assertEquals(2_000_00L, change.nowCents)
        assertEquals(1_600_00L, change.beforeCents)
        assertEquals(400_00L, change.diffCents)
        assertEquals(0.25f, change.pct!!, 1e-6f)

        val empty = RevenueBook.todayVsYesterday(book, RevenueBook.plusDays(today, 5))
        assertEquals(0L, empty.nowCents)
        assertEquals(0L, empty.beforeCents)
        assertFalse(empty.hasBase)
    }

    @Test fun sameWeekdayComparesSevenDaysBack() {
        val book = listOf(
            entry(today, 2_000),
            entry(RevenueBook.plusDays(today, -7), 2_500),
            entry(RevenueBook.plusDays(today, -1), 400),
        )
        val change = RevenueBook.sameWeekdayChange(book, today)
        assertEquals(2_500_00L, change.beforeCents)
        assertEquals(-500_00L, change.diffCents)
        assertEquals(-0.2f, change.pct!!, 1e-6f)
    }

    @Test fun weekToDateComparesSameSpanLastWeek() {
        // Monday 10/26 → Wednesday 10/28 vs Monday 10/19 → Wednesday 10/21.
        val book = listOf(
            entry(day(2026, 10, 19), 1_000),
            entry(day(2026, 10, 20), 1_000),
            entry(day(2026, 10, 21), 1_000),
            entry(day(2026, 10, 26), 1_200),
            entry(day(2026, 10, 27), 1_300),
            entry(day(2026, 10, 28), 1_500),
            // 10/22 (last week, Thursday) is outside both spans.
            entry(day(2026, 10, 22), 9_000),
        )
        val change = RevenueBook.weekToDateChange(book, today)
        assertEquals(4_000_00L, change.nowCents)
        assertEquals(3_000_00L, change.beforeCents)
        assertEquals(0.3333f, change.pct!!, 1e-3f)
    }

    @Test fun averageTicketTrendComparesTwoSevenDayWindows() {
        val book = listOf(
            entry(RevenueBook.plusDays(today, -7), 700, 70),
            entry(today, 600, 30),
        )
        val change = RevenueBook.averageTicketChange(book, today)
        // Last 7 days: 600 yuan / 30 orders = ¥20. The 7 before: 700 yuan / 70 orders = ¥10.
        assertEquals(2_000L, change.nowCents)
        assertEquals(1_000L, change.beforeCents)
        assertEquals(1_000L, change.diffCents)
        assertEquals(1f, change.pct!!, 1e-6f)
    }

    @Test fun seriesZeroFillsOldestFirst() {
        val book = listOf(entry(today, 900), entry(RevenueBook.plusDays(today, -2), 300))
        val series = RevenueBook.series(book, today, 4)
        assertEquals(4, series.size)
        assertEquals(listOf(0L, 300_00L, 0L, 900_00L), series.map { it.revenueCents })
        assertEquals(listOf(false, true, false, true), series.map { it.hasEntry })
        assertEquals(today, series.last().dayStart)
    }

    @Test fun weekdayAveragesIgnoreDaysWithoutEntries() {
        // 10/26 Mon, 10/19 Mon; 10/27 Tue.
        val book = listOf(entry(day(2026, 10, 26), 1_000), entry(day(2026, 10, 19), 3_000), entry(day(2026, 10, 27), 1_500))
        val averages = RevenueBook.weekdayAverages(book, today, weeks = 2)
        assertEquals(7, averages.size)
        assertEquals(2_000_00L, averages[0].averageCents)
        assertEquals(2, averages[0].days)
        assertEquals(1_500_00L, averages[1].averageCents)
        assertEquals(0, averages[6].days)
    }

    @Test fun bestAndWorstOnlyLookAtRecordedDays() {
        val book = listOf(
            entry(RevenueBook.plusDays(today, -1), 1_200),
            entry(today, 3_400),
            entry(RevenueBook.plusDays(today, -25), 9_900), // outside the 14 day window
        )
        assertEquals(today, RevenueBook.bestDay(book, today, 14)!!.dayStart)
        assertEquals(RevenueBook.plusDays(today, -1), RevenueBook.worstDay(book, today, 14)!!.dayStart)
        assertNull(RevenueBook.bestDay(emptyList(), today))
    }

    @Test fun declineStreakCountsConsecutiveFalls() {
        val book = listOf(
            entry(RevenueBook.plusDays(today, -2), 3_000),
            entry(RevenueBook.plusDays(today, -1), 2_400),
            entry(today, 1_800),
        )
        assertEquals(2, RevenueBook.declineStreak(book, today))
        assertEquals(3_000_00L to 1_800_00L, RevenueBook.declineRange(book, today, 2))

        val recovered = book.dropLast(1) + entry(today, 2_500)
        assertEquals(0, RevenueBook.declineStreak(recovered, today))
    }

    @Test fun declineStreakBreaksOnMissingDay() {
        val book = listOf(
            entry(RevenueBook.plusDays(today, -3), 3_000),
            // 10/26 missing
            entry(today, 1_000),
        )
        assertEquals(0, RevenueBook.declineStreak(book, today))
    }

    @Test fun monthGroupsSortNewestFirstAndTotal() {
        val book = listOf(
            entry(day(2026, 9, 30), 800, 40),
            entry(day(2026, 10, 1), 1_000, 50),
            entry(day(2026, 10, 2), 2_000, 60),
        )
        val groups = RevenueBook.groupByMonth(book)
        assertEquals(listOf("2026-10", "2026-09"), groups.map { it.key })
        assertEquals("2026年10月", groups[0].label)
        assertEquals(3_000_00L, groups[0].totalCents)
        assertEquals(110, groups[0].orders)
        assertEquals(2, groups[0].days)
    }

    @Test fun upsertReplacesSeededDayWithManualEntry() {
        val seeded = entry(today, 2_000, 70, RevenueSource.SEED)
        val manual = entry(today, 2_350, 75, RevenueSource.MANUAL)
        val book = RevenueBook.upsert(listOf(seeded), manual)
        assertEquals(1, book.size)
        assertEquals(2_350_00L, book.single().revenueCents)
        assertFalse(book.single().seeded)
    }

    @Test fun mergeLetsIncomingRowsWinByDay() {
        val book = listOf(entry(day(2026, 10, 26), 1_000), entry(day(2026, 10, 27), 1_100))
        val incoming = listOf(entry(day(2026, 10, 27), 1_500, source = RevenueSource.CSV), entry(day(2026, 10, 28), 900, source = RevenueSource.CSV))
        val merged = RevenueBook.merge(book, incoming)
        assertEquals(listOf(1_000L, 1_500L, 900L), merged.map { it.revenueCents / 100 })
    }

    @Test fun labelsAndWeekStartFollowTheCalendar() {
        assertEquals(2, RevenueBook.weekdayIndex(today))
        assertEquals("周三", RevenueBook.weekdayLabel(today))
        assertEquals(day(2026, 10, 26), RevenueBook.weekStart(today))
        assertEquals("10月28日 周三", RevenueBook.dayLabel(today))
        assertEquals("10/28", RevenueBook.shortDate(today))
        assertEquals("2026-10-28", RevenueBook.isoDate(today))
    }

    @Test fun moneyFormatsThousandsAndOptionalDecimals() {
        assertEquals("¥1,280", RevenueBook.money(128_000))
        assertEquals("¥1,280.5", RevenueBook.money(128_050))
        assertEquals("¥1,280.55", RevenueBook.money(128_055))
        assertEquals("-¥12.5", RevenueBook.money(-1_250))
        assertEquals("+12%", RevenueBook.percentText(0.1236f))
        assertEquals("-3%", RevenueBook.percentText(-0.034f))
        assertNull(RevenueBook.percentText(null))
    }

    @Test fun demoBookIsLabelledAndEndsToday() {
        val demo = DemoRevenue.generate(today)
        assertEquals(DemoRevenue.DAYS - 2, demo.size) // two closed days on purpose
        assertTrue(demo.all { it.seeded })
        assertTrue(demo.none { it.dayStart > today })
        assertTrue(demo.any { it.dayStart == today })
        assertEquals(demo, DemoRevenue.generate(today)) // deterministic
    }
}
