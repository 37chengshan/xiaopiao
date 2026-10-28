package com.xiaopiao.app.ui.ledger

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

class LedgerFilterTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long = Calendar.getInstance().apply {
        clear(); set(year, month - 1, day, hour, 0, 0)
    }.timeInMillis

    private val now = at(2026, 10, 28, 15)
    private val today = startOfDay(now)

    private fun txn(
        cents: Long,
        at: Long,
        dir: Direction = Direction.OUT,
        category: Category = Category.OTHER,
        source: Source = Source.MANUAL,
        merchant: String = "商户",
    ) = Txn(
        amountCents = cents, direction = dir, merchant = merchant, category = category,
        occurredAt = at, source = source,
    )

    private val october = listOf(
        txn(1800, now, merchant = "瑞幸"),
        txn(23200, at(2026, 10, 2), category = Category.MEAL, source = Source.NOTIFICATION),
        txn(12000, at(2026, 10, 3), Direction.IN, Category.SOCIAL),
    )
    private val september = txn(9900, at(2026, 9, 30))
    private val lastYear = txn(5000, at(2025, 12, 31))

    @Test fun defaultWindowIsThisMonthOnly() {
        val f = LedgerFilter()
        assertTrue(f.isDefault)
        assertEquals(0, f.activeCount)
        assertEquals(october, f.apply(october + september + lastYear, now))
    }

    @Test fun halfOpenWindowDropsTheFirstMillisecondOfNextMonth() {
        val (from, to) = LedgerFilter().window(now)
        assertEquals(at(2026, 10, 1, 0), from)
        assertEquals(at(2026, 11, 1, 0), to)
        val edge = txn(100, to)
        assertFalse(LedgerFilter().apply(listOf(edge), now).contains(edge))
        val last = txn(100, to - 1)
        assertTrue(LedgerFilter().apply(listOf(last), now).contains(last))
    }

    @Test fun lastMonthAndYearWindowsAreDistinct() {
        val lastMonth = LedgerFilter(range = LedgerRange.LastMonth)
        assertEquals(listOf(september), lastMonth.apply(listOf(september) + october + lastYear, now))

        val year = LedgerFilter(range = LedgerRange.Year)
        assertEquals(4, year.apply(october + september + lastYear, now).size)
    }

    @Test fun directionCategorySourceAndAmountNarrowTheList() {
        val list = october + september
        assertEquals(
            listOf(october[2]),
            LedgerFilter(direction = Direction.IN).apply(list, now),
        )
        assertEquals(
            listOf(october[1]),
            LedgerFilter(categories = setOf(Category.MEAL)).apply(list, now),
        )
        assertEquals(
            listOf(october[1]),
            LedgerFilter(sources = setOf(Source.NOTIFICATION)).apply(list, now),
        )
        assertEquals(
            listOf(october[1]),
            LedgerFilter(minCents = 20000L).apply(list, now),
        )
        assertEquals(
            listOf(october[0]),
            LedgerFilter(maxCents = 2000L).apply(list, now),
        )
    }

    @Test fun customRangeIsInclusiveOfBothEndDays() {
        val f = LedgerFilter(
            range = LedgerRange.Custom,
            customFrom = at(2026, 10, 2, 8),
            customTo = at(2026, 10, 3, 9),
        )
        assertEquals(listOf(october[2], october[1]).map { it.amountCents }.toSet(), f.apply(october, now).map { it.amountCents }.toSet())
        assertEquals(2, f.apply(october, now).size)
    }

    @Test fun reversedBoundsAreNormalisedInsteadOfEmptyingTheList() {
        val reversed = LedgerFilter(minCents = 900000L, maxCents = 100L)
        assertEquals(100L, reversed.normalized().minCents)
        assertEquals(900000L, reversed.normalized().maxCents)

        val backwards = LedgerFilter(
            range = LedgerRange.Custom,
            customFrom = today,
            customTo = today - DAY_MS,
        ).normalized()
        assertEquals(today - DAY_MS, backwards.customFrom)
        assertEquals(today, backwards.customTo)
        assertEquals(listOf(october[0]), backwards.apply(october, now))
    }

    @Test fun activeCountCountsGroupsNotChips() {
        val f = LedgerFilter(
            direction = Direction.OUT,
            range = LedgerRange.Year,
            categories = setOf(Category.MEAL, Category.DRINK),
            minCents = 100L,
        )
        assertEquals(4, f.activeCount)
        assertFalse(f.isDefault)
    }

    @Test fun queryCombinesWithTheWindow() {
        assertEquals(listOf(october[0]), LedgerFilter().apply(october, now, "瑞幸"))
        assertTrue(LedgerFilter().apply(october, now, "不存在的商户").isEmpty())
    }
}
