package com.xiaopiao.app.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BudgetMathTest {
    private val originalTz: TimeZone = TimeZone.getDefault()

    @After fun restoreTimeZone() = TimeZone.setDefault(originalTz)

    private fun use(tz: String) {
        TimeZone.setDefault(TimeZone.getTimeZone(tz))
    }

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min, 0) }.timeInMillis

    private fun txn(cents: Long, at: Long, dir: Direction = Direction.OUT, category: Category = Category.MEAL) = Txn(
        amountCents = cents, direction = dir, merchant = "x", category = category,
        occurredAt = at, source = Source.MANUAL,
    )

    @Test fun weekStartsOnMondayAndCountsDaysLeftIncludingToday() {
        use("Asia/Shanghai")
        val wed = at(2026, 10, 28, 15)
        assertEquals(at(2026, 10, 26, 0), BudgetMath.startOf(BudgetPeriod.Week, wed))
        assertEquals(at(2026, 11, 2, 0), BudgetMath.endOf(BudgetPeriod.Week, wed))
        assertEquals(5, BudgetMath.daysLeft(BudgetPeriod.Week, wed))

        // Sunday is the last day of the week: only itself is left, and Monday belongs to the next week.
        val sun = at(2026, 11, 1, 9)
        assertEquals(1, BudgetMath.daysLeft(BudgetPeriod.Week, sun))
        assertEquals(at(2026, 10, 26, 0), BudgetMath.startOf(BudgetPeriod.Week, sun))
        assertEquals(at(2026, 11, 2, 0), BudgetMath.startOf(BudgetPeriod.Week, at(2026, 11, 2, 9)))
    }

    @Test fun dayAndMonthWindowsAreHalfOpen() {
        use("Asia/Shanghai")
        val now = at(2026, 10, 28, 15)
        assertEquals(at(2026, 10, 28, 0), BudgetMath.startOf(BudgetPeriod.Day, now))
        assertEquals(at(2026, 10, 29, 0), BudgetMath.endOf(BudgetPeriod.Day, now))
        assertEquals(1, BudgetMath.daysLeft(BudgetPeriod.Day, now))
        assertEquals(at(2026, 10, 1, 0), BudgetMath.startOf(BudgetPeriod.Month, now))
        assertEquals(at(2026, 11, 1, 0), BudgetMath.endOf(BudgetPeriod.Month, now))
        assertEquals(4, BudgetMath.daysLeft(BudgetPeriod.Month, now))
    }

    @Test fun crossingMonthsSplitsTheSpend() {
        use("Asia/Shanghai")
        val oct = listOf(
            txn(9000, at(2026, 10, 31, 23, 59)),
            txn(1000, at(2026, 11, 1, 0, 0)),
        )
        val now = at(2026, 10, 31, 23)
        assertEquals(9000L, BudgetMath.status(BudgetPeriod.Month, 50_000, oct, now)!!.spent)
        assertEquals(1000L, BudgetMath.status(BudgetPeriod.Month, 50_000, oct, at(2026, 11, 1, 1))!!.spent)
        assertEquals(9000L, BudgetMath.status(BudgetPeriod.Day, 50_000, oct, now)!!.spent)
    }

    @Test fun statusSplitsRemainingAcrossTheDaysLeft() {
        use("Asia/Shanghai")
        val now = at(2026, 10, 28, 15)
        val txns = listOf(txn(20_000, at(2026, 10, 2)), txn(999_900, at(2026, 9, 30)))
        val month = BudgetMath.status(BudgetPeriod.Month, 100_000, txns, now)!!
        assertEquals(20_000L, month.spent)
        assertEquals(80_000L, month.remaining)
        assertEquals(4, month.daysLeft)
        assertEquals(20_000L, month.perDayLeft)
        assertEquals(0.2f, month.ratio, 0.0001f)

        // Already over: the remaining amount and the per-day figure both go negative.
        val over = BudgetMath.status(BudgetPeriod.Month, 10_000, txns, now)!!
        assertTrue(over.remaining < 0)
        assertEquals(-2_500L, over.perDayLeft)   // floorDiv(-10_000, 4)
    }

    @Test fun closedPeriodsReturnNull() {
        use("Asia/Shanghai")
        assertNull(BudgetMath.status(BudgetPeriod.Day, null, emptyList(), at(2026, 10, 28)))
        assertNull(BudgetMath.status(BudgetPeriod.Day, 0L, emptyList(), at(2026, 10, 28)))
        assertTrue(BudgetMath.statuses(BudgetSettings(), emptyList(), at(2026, 10, 28)).isEmpty())
    }

    @Test fun statusesFollowWhicheverPeriodsAreSet() {
        use("Asia/Shanghai")
        val now = at(2026, 10, 28, 15)
        val settings = BudgetSettings(dailyLimit = 10_000, monthlyLimit = 100_000)
        val list = BudgetMath.statuses(settings, emptyList(), now)
        assertEquals(listOf(BudgetPeriod.Day, BudgetPeriod.Month), list.map { it.period })
    }

    @Test fun summaryExposesOnlyTheMonth() {
        use("Asia/Shanghai")
        val now = at(2026, 10, 28, 15)
        val txns = listOf(
            txn(30_000, at(2026, 10, 3)),
            txn(5_000, at(2026, 10, 4), Direction.IN),
            txn(70_000, at(2026, 9, 30)),
        )
        val withLimit = BudgetMath.summary(BudgetSettings(monthlyLimit = 100_000), txns, now)
        assertEquals(100_000L, withLimit.monthLimit)
        assertEquals(30_000L, withLimit.monthSpent)
        assertEquals(70_000L, withLimit.monthRemaining)

        val without = BudgetMath.summary(BudgetSettings(dailyLimit = 5_000), txns, now)
        assertNull(without.monthLimit)
        assertNull(without.monthRemaining)
        assertEquals(30_000L, without.monthSpent)
    }

    @Test fun categoryBudgetsOnlyCountTheirCategoryInThisMonth() {
        use("Asia/Shanghai")
        val now = at(2026, 10, 28, 15)
        val txns = listOf(
            txn(12_000, at(2026, 10, 5), category = Category.MEAL),
            txn(3_000, at(2026, 10, 6), category = Category.MEAL),
            txn(50_000, at(2026, 9, 20), category = Category.MEAL),
            txn(9_000, at(2026, 10, 7), category = Category.DRINK),
        )
        val budgets = BudgetMath.categoryBudgets(
            BudgetSettings(categoryLimits = mapOf(Category.MEAL to 40_000, Category.DRINK to 5_000)),
            txns, now,
        )
        val meal = budgets.first { it.category == Category.MEAL }
        assertEquals(15_000L, meal.spent)
        assertEquals(25_000L, meal.remaining)
        val drink = budgets.first { it.category == Category.DRINK }
        assertEquals(9_000L, drink.spent)
        assertTrue(drink.remaining < 0)
    }

    @Test fun levelsAreEightyAndOneHundredPercent() {
        assertEquals(0, BudgetMath.level(7_999, 10_000))
        assertEquals(80, BudgetMath.level(8_000, 10_000))
        assertEquals(80, BudgetMath.level(9_999, 10_000))
        assertEquals(100, BudgetMath.level(10_000, 10_000))
        assertEquals(100, BudgetMath.level(12_000, 10_000))
        assertEquals(0, BudgetMath.level(12_000, null))
    }

    /** 同一笔本地时间在 UTC+8 与 UTC+14 下都必须算进「当地那一天/那一月」。 */
    @Test fun windowsFollowTheDeviceTimeZone() {
        use("Pacific/Kiritimati")            // UTC+14
        val kiritimati = at(2026, 10, 1, 0, 30)
        assertEquals(at(2026, 10, 1, 0), BudgetMath.startOf(BudgetPeriod.Day, kiritimati))
        assertEquals(at(2026, 10, 1, 0), BudgetMath.startOf(BudgetPeriod.Month, kiritimati))
        assertEquals(1, BudgetMath.daysLeft(BudgetPeriod.Day, kiritimati))

        use("Asia/Shanghai")                 // UTC+8
        val shanghai = at(2026, 10, 1, 0, 30)
        assertEquals(at(2026, 10, 1, 0), BudgetMath.startOf(BudgetPeriod.Day, shanghai))
        assertEquals(at(2026, 10, 1, 0), BudgetMath.startOf(BudgetPeriod.Month, shanghai))
        // 同一笔记录在两地都算「本月」，但换算到 UTC 的时间戳不同 —— 断言的是本地日历，不是 UTC。
        assertTrue(BudgetMath.status(BudgetPeriod.Month, 100_000, listOf(txn(1_000, shanghai)), shanghai)!!.spent == 1_000L)
    }
}
