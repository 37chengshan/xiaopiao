package com.xiaopiao.app.ui.ledger

import com.xiaopiao.app.capture.QuickDraft
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LedgerModelsTest {
    /** Fixed local instant so the suite never depends on when it runs. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long = Calendar.getInstance().apply {
        clear(); set(year, month - 1, day, hour, 0, 0)
    }.timeInMillis

    private val now = at(2026, 10, 28, 15)
    private val today = startOfDay(now)

    private fun txn(
        cents: Long,
        at: Long,
        dir: Direction = Direction.OUT,
        merchant: String = "x",
        category: Category = Category.OTHER,
        source: Source = Source.MANUAL,
        note: String = "",
    ) = Txn(
        amountCents = cents, direction = dir, merchant = merchant, category = category,
        occurredAt = at, source = source, note = note,
    )

    @Test fun groupsByDayNewestFirstWithTotals() {
        val groups = LedgerGrouping.group(
            listOf(
                txn(1500, now), txn(2500, now - 3_600_000L),
                txn(800000, now, Direction.IN),
                txn(900, now - DAY_MS),
            ),
            now,
        )
        assertEquals(2, groups.size)
        assertEquals(4000L, groups[0].outCents)
        assertEquals(800000L, groups[0].inCents)
        assertEquals(3, groups[0].items.size)
        assertTrue(groups[0].items.first().occurredAt >= groups[0].items.last().occurredAt)
    }

    /** §3.7: the card header leads with the date and only suffixes the relative word. */
    @Test fun dayHeaderLeadsWithTheDate() {
        assertEquals("10月28日 今天", LedgerGrouping.header(today, now))
        assertEquals("10月27日 昨天", LedgerGrouping.header(today - DAY_MS, now))
        assertEquals("10月19日 周一", LedgerGrouping.header(today - 9 * DAY_MS, now))
    }

    @Test fun relativeDayLabelSurvivesForTheRecordSheet() {
        assertEquals("今天", LedgerGrouping.dayLabel(today, now))
        assertEquals("昨天", LedgerGrouping.dayLabel(today - DAY_MS, now))
        assertEquals("前天", LedgerGrouping.dayLabel(today - 2 * DAY_MS, now))
        assertTrue(LedgerGrouping.dayLabel(today - 10 * DAY_MS, now).contains("月"))
    }

    @Test fun summarySplitsTodayFromTheMonth() {
        val summary = LedgerTotals.summary(
            listOf(
                txn(1800, now),                       // today, counts in both
                txn(23200, at(2026, 10, 2)),          // this month, not today
                txn(12000, at(2026, 10, 3), Direction.IN),
                txn(9900, at(2026, 9, 30)),           // last month: ignored
            ),
            now,
        )
        assertEquals(1800L, summary.todayOutCents)
        assertEquals(25000L, summary.monthOutCents)
        assertEquals(12000L, summary.monthInCents)
    }

    @Test fun rangesAreHalfOpenMonthAndYearWindows() {
        assertEquals(at(2026, 10, 1, 0), LedgerRanges.monthStart(now))
        assertEquals(at(2026, 11, 1, 0), LedgerRanges.nextMonthStart(now))
        assertEquals(at(2026, 9, 1, 0), LedgerRanges.lastMonthStart(now))
        assertEquals(at(2026, 1, 1, 0), LedgerRanges.yearStart(now))
        // 2026-10-28 is a Wednesday, so the week starts on Monday the 26th.
        assertEquals(at(2026, 10, 26, 0), LedgerRanges.weekStart(now))
    }

    @Test fun emptyLedgerHasNoGroups() = assertTrue(LedgerGrouping.group(emptyList(), now).isEmpty())

    @Test fun draftYesterdayLandsOnYesterday() {
        val t = QuickDraft(1800, "打车", Category.TRANSPORT, -1, Direction.OUT).toTxn(now)
        assertEquals("昨天", LedgerGrouping.dayLabel(startOfDay(t.occurredAt), now))
        assertEquals(Source.QUICK_ADD, t.source)
    }

    @Test fun draftTodayKeepsNow() {
        assertEquals(now, QuickDraft(100, "x", Category.OTHER, 0, Direction.OUT).toTxn(now).occurredAt)
    }

    @Test fun moveToDayKeepsClock() {
        val moved = moveToDay(now, startOfDay(now - DAY_MS))
        assertEquals(LedgerGrouping.clock(now), LedgerGrouping.clock(moved))
        assertEquals("昨天", LedgerGrouping.dayLabel(startOfDay(moved), now))
    }

    @Test fun signedAmountCarriesTheDirection() {
        assertEquals("-18.28", signedAmount(1828, Direction.OUT))
        assertEquals("+120", signedAmount(12000, Direction.IN))
        assertEquals("-0.50", signedAmount(50, Direction.OUT))
    }

    @Test fun queryMatchesMerchantNoteCategoryAndAmount() {
        val coffee = txn(1828, now, merchant = "瑞幸", category = Category.DRINK, note = "上午提神")
        assertTrue(coffee.matchesQuery("瑞"))
        assertTrue(coffee.matchesQuery("提神"))
        assertTrue(coffee.matchesQuery("奶茶"))
        assertTrue(coffee.matchesQuery("18.28"))
        assertTrue(coffee.matchesQuery("¥18.28"))
        assertFalse(coffee.matchesQuery("打车"))
        assertTrue(coffee.matchesQuery("  "))
    }

    @Test fun recentSearchesAreDedupedNewestFirstAndCapped() {
        var recent = emptyList<String>()
        listOf("瑞幸", "打车", "瑞幸", " ").forEach { recent = RecentSearches.push(recent, it) }
        assertEquals(listOf("瑞幸", "打车"), recent)

        listOf("奶茶", "a", "b", "c", "d", "e").forEach { recent = RecentSearches.push(recent, it) }
        assertEquals(RecentSearches.LIMIT, recent.size)
        assertEquals(listOf("e", "d", "c", "b", "a", "奶茶"), recent)
        assertFalse(recent.any { it.isBlank() })
    }

    @Test fun calendarCellsPadToFullWeeksStartingMonday() {
        // 2026-10-01 is a Thursday → three blanks, 31 days, 35 cells.
        val cells = LedgerCalendar.cells(at(2026, 10, 1))
        assertEquals(35, cells.size)
        assertEquals(listOf(0L, 0L, 0L), cells.take(3))
        assertEquals(at(2026, 10, 1, 0), cells[3])
        assertEquals(at(2026, 10, 31, 0), cells[33])
        assertEquals(0L, cells[34])
        assertEquals(31, cells.count { it != 0L })
    }

    @Test fun calendarShiftsMonthsAcrossTheYearBoundary() {
        assertEquals(at(2026, 12, 1, 0), LedgerCalendar.shiftMonths(at(2027, 1, 15), -1))
        assertEquals(at(2027, 2, 1, 0), LedgerCalendar.shiftMonths(at(2027, 1, 15), 1))
        assertEquals("2027年2月", LedgerCalendar.title(at(2027, 2, 3)))
    }

    @Test fun keypadBuildsAmounts() {
        fun type(vararg keys: String) = keys.fold("") { acc, k -> AmountEntry.press(acc, k) }
        assertEquals(3850L, AmountEntry.toCents(type("3", "8", ".", "5")))
        assertEquals("0.", type("."))
        assertEquals("5", type("0", "5"))
        assertEquals("12.34", type("1", "2", ".", "3", "4", "5"))
        assertEquals("1", type("1", "2", AmountEntry.BACKSPACE))
        assertEquals("1.5", type("1", ".", ".", "5"))
        assertEquals(500L, AmountEntry.toCents("5."))
        assertEquals(0L, AmountEntry.toCents(""))
        assertEquals("0", AmountEntry.display(""))
    }

    @Test fun keypadCapsIntegerDigits() {
        assertEquals("1234567", "123456789".fold("") { acc, k -> AmountEntry.press(acc, k.toString()) })
    }

    @Test fun fromCentsRoundTrips() {
        assertEquals("32", AmountEntry.fromCents(3200))
        assertEquals("38.5", AmountEntry.fromCents(3850))
        assertEquals("0.05", AmountEntry.fromCents(5))
        assertEquals(3850L, AmountEntry.toCents(AmountEntry.fromCents(3850)))
    }
}
