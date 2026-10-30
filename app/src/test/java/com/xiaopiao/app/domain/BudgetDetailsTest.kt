package com.xiaopiao.app.domain

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BudgetDetailsTest {
    private val original = TimeZone.getDefault()
    init { TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai")) }
    @After fun restore() { TimeZone.setDefault(original) }
    private fun at(month: Int, day: Int, hour: Int = 12, minute: Int = 0) = Calendar.getInstance().apply {
        clear(); set(2026, month - 1, day, hour, minute)
    }.timeInMillis
    private fun txn(id: Long, cents: Long, at: Long, category: Category = Category.MEAL,
        merchant: String = "店铺", direction: Direction = Direction.OUT, categoryId: String = "") = Txn(
        id, cents, direction, merchant, category, at, Source.MANUAL, categoryId = categoryId)

    @Test fun dailyDetailsExcludeIncomeAndNextMidnightAndSortNewestFirst() {
        val d = BudgetDetails.build(BudgetPeriod.Day, at(10, 30), listOf(
            txn(1, 100, at(10, 30, 0)), txn(2, 200, at(10, 30, 23, 59)),
            txn(3, 900, at(10, 31, 0)), txn(4, 900, at(10, 30), direction = Direction.IN)))
        assertEquals(300L, d.spent); assertEquals(listOf(2L, 1L), d.txns.map { it.id })
    }
    @Test fun weekContainsSevenDaysIncludingZerosAndCrossesMonthWithoutLoss() {
        val d = BudgetDetails.build(BudgetPeriod.Week, at(10, 30), listOf(
            txn(1, 100, at(10, 26)), txn(2, 200, at(11, 1)), txn(3, 900, at(11, 2, 0))))
        assertEquals(7, d.buckets.size); assertEquals(at(10, 26, 0), d.from)
        assertEquals(at(11, 2, 0), d.until); assertEquals(300L, d.buckets.sumOf { it.spent })
        assertEquals(5, d.buckets.count { it.count == 0 })
    }
    @Test fun monthWeeksAreMondayBasedClippedAndExactlyPartitionTheMonth() {
        val d = BudgetDetails.build(BudgetPeriod.Month, at(10, 30), listOf(
            txn(1, 100, at(10, 1)), txn(2, 200, at(10, 31)), txn(3, 900, at(11, 1, 0))))
        assertEquals(at(10, 1, 0), d.buckets.first().from)
        assertEquals(at(10, 5, 0), d.buckets.first().until)
        assertEquals(at(11, 1, 0), d.buckets.last().until)
        d.buckets.zipWithNext().forEach { (a, b) -> assertEquals(a.until, b.from) }
        assertEquals(d.spent, d.buckets.sumOf { it.spent }); assertEquals(300L, d.spent)
    }
    @Test fun categoryBudgetAndCustomCategoryRankingsUseTheirRespectiveIdentities() {
        val custom = CategoryDef("custom:tea", "茶饮", "local_cafe", "brown")
        val txns = listOf(txn(1, 100, at(10, 30), Category.OTHER, " 茶店 ", categoryId = custom.id),
            txn(2, 300, at(10, 30), Category.MEAL, "饭店"), txn(3, 200, at(10, 30), Category.OTHER, "茶店", categoryId = custom.id))
        val defs = CategoryDefaults.all + custom
        val d = BudgetDetails.build(BudgetPeriod.Month, at(10, 30), txns, defs = defs)
        assertEquals(300L, d.categories.first { it.key == custom.id }.spent)
        assertEquals(2, d.merchants.first { it.key == "茶店" }.count)
        val c = BudgetDetails.build(BudgetPeriod.Month, at(10, 30), txns, Category.OTHER, defs)
        assertEquals(300L, c.spent); assertEquals(2, c.txns.size)
    }
    @Test fun shiftingMonthsStartsOnFirstAndDoesNotSkipShortMonths() {
        val previous = BudgetDetails.shift(BudgetPeriod.Month, at(3, 31), -1)
        assertEquals(at(2, 1, 0), previous)
        assertEquals(at(3, 1, 0), BudgetDetails.shift(BudgetPeriod.Month, previous, 1))
    }
    @Test fun daylightSavingWeekStillContainsSevenLocalCalendarDays() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        val d = BudgetDetails.build(BudgetPeriod.Week, at(11, 1), emptyList())
        assertEquals(7, d.buckets.size)
        assertEquals(169L * 60 * 60 * 1000, d.until - d.from)
        assertTrue(d.buckets.all { Calendar.getInstance().apply { timeInMillis = it.from }.get(Calendar.HOUR_OF_DAY) == 0 })
    }
}
