package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegretSimulatorTest {
    private val now = at(2026, 10, 27, 12)

    /** 10 milk teas of ¥18 in each of the last 3 months (days 1..10 of Jul? no: relative to now). */
    private val history = (1..30).map { txn(addDays(now, -3 * it), 18.0, "喜茶", Category.DRINK) } +
        listOf(txn(at(2026, 10, 20), 200.0, "商场", Category.SHOPPING))

    @Test fun savesHalfOfCategorySpendInWindow() {
        val r = RegretSimulator.simulate(history, RegretScenario(RegretTarget.ByCategory(Category.DRINK), 50, 3), now)
        // days 3,6,..,90 back; window 3 months back from 10-27 = 07-27 which is 92 days -> all 30 in
        assertEquals(30, r.txnCount)
        assertEquals(54000L, r.spentCents)
        assertEquals(27000L, r.savedCents)
    }

    @Test fun equivalentsAreBiggestThreeThatFit() {
        val r = RegretSimulator.simulate(history, RegretScenario(RegretTarget.ByCategory(Category.DRINK), 50, 3), now)
        assertEquals(listOf("火锅", "电影票", "视频会员"), r.equivalents.map { it.noun })
        assertEquals(listOf(2, 6, 10), r.equivalents.map { it.count })
    }

    @Test fun windowExcludesOlderTransactions() {
        val one = RegretSimulator.simulate(history, RegretScenario(RegretTarget.ByCategory(Category.DRINK), 100, 1), now)
        assertTrue(one.txnCount in 9..11)
        assertTrue(one.spentCents < 54000L)
    }

    @Test fun merchantTargetMatchesCaseAndSpaceInsensitively() {
        val h = listOf(txn(addDays(now, -2), 30.0, "Luckin Coffee"), txn(addDays(now, -5), 30.0, "luckincoffee"))
        val r = RegretSimulator.simulate(h, RegretScenario(RegretTarget.ByMerchant("Luckin Coffee"), 50, 1), now)
        assertEquals(6000L, r.spentCents)
        assertEquals(3000L, r.savedCents)
    }

    @Test fun zeroReductionOrNoSpendYieldsNothingToShow() {
        val r0 = RegretSimulator.simulate(history, RegretScenario(RegretTarget.ByCategory(Category.DRINK), 0, 3), now)
        assertEquals(0L, r0.savedCents)
        assertTrue(r0.equivalents.isEmpty())
        val none = RegretSimulator.simulate(history, RegretScenario(RegretTarget.ByCategory(Category.HEALTH), 50, 3), now)
        assertEquals(0L, none.spentCents)
    }

    @Test fun incomeIsIgnored() {
        val h = listOf(txn(addDays(now, -2), 500.0, "喜茶", Category.DRINK, Direction.IN))
        assertEquals(0L, RegretSimulator.simulate(h, RegretScenario(RegretTarget.ByCategory(Category.DRINK), 50, 1), now).spentCents)
    }

    @Test fun bigSavingsIncludeWeekendTrip() {
        val eq = RegretSimulator.equivalentsFor(240000L)
        assertEquals("周末短途", eq.first().noun)
        assertEquals(3, eq.first().count)
    }

    @Test fun candidatesAreRankedBySpend() {
        val (cats, merchants) = RegretSimulator.candidates(history, now, 3)
        assertEquals(Category.DRINK, cats.first().category)
        assertEquals("喜茶", merchants.first().merchant)
    }

    @Test fun disclaimerIsAlwaysPresentAndHasNoReturnsTalk() {
        val r = RegretSimulator.simulate(history, RegretScenario(RegretTarget.ByCategory(Category.DRINK), 50, 3), now)
        assertTrue(r.disclaimer.contains("不构成"))
        assertTrue(r.disclaimer.contains("不含任何收益率"))
    }
}
