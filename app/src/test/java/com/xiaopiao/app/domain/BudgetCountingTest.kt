package com.xiaopiao.app.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BudgetCountingTest {
    private val original = TimeZone.getDefault()
    init { TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai")) }
    @After fun restore() { TimeZone.setDefault(original) }

    private fun at(month: Int, day: Int, hour: Int = 12) = Calendar.getInstance().apply {
        clear(); set(2026, month - 1, day, hour, 0)
    }.timeInMillis

    private fun txn(id: Long, cents: Long, at: Long, merchant: String = "店", dir: Direction = Direction.OUT,
        category: Category = Category.MEAL, note: String = "") = Txn(
        id = id, amountCents = cents, direction = dir, merchant = merchant, category = category,
        occurredAt = at, source = Source.NOTIFICATION, note = note)

    private val now = at(10, 28)
    private val spend = txn(1, 1_000, at(10, 28))
    private val transferIn = txn(2, 5_000, at(10, 28), "张三", Direction.IN, Category.OTHER, note = "转账")
    private val redPacketIn = txn(3, 800, at(10, 28), "袁开浩的红包", Direction.IN, Category.SOCIAL)
    private val txns = listOf(spend, transferIn, redPacketIn)

    @Test fun defaultsCountOnlyOutgoingMoney() {
        val s = BudgetMath.status(BudgetPeriod.Day, 10_000, txns, now)!!
        assertEquals(1_000L, s.spent); assertEquals(0L, s.income); assertEquals(9_000L, s.remaining)
    }

    @Test fun countedTransferRaisesTheLimitAndTheRatioFollowsIt() {
        val s = BudgetMath.status(BudgetPeriod.Day, 10_000, txns, now, BudgetCounting(transfer = true))!!
        assertEquals(5_000L, s.income); assertEquals(14_000L, s.remaining)
        assertEquals(1.0 / 15, s.ratio.toDouble(), 1e-4)
    }

    @Test fun redPacketSwitchIsIndependentOfTransfer() {
        val s = BudgetMath.status(BudgetPeriod.Day, 10_000, txns, now, BudgetCounting(redPacket = true))!!
        assertEquals(800L, s.income)
    }

    @Test fun perEntryOverrideBeatsTheGlobalSwitchInBothDirections() {
        val excludedSpend = BudgetCounting(overrides = mapOf(1L to false))
        assertEquals(0L, BudgetMath.status(BudgetPeriod.Day, 10_000, txns, now, excludedSpend)!!.spent)

        val forcedIn = BudgetCounting(transfer = false, overrides = mapOf(2L to true))
        assertEquals(5_000L, BudgetMath.status(BudgetPeriod.Day, 10_000, txns, now, forcedIn)!!.income)

        val refusedIn = BudgetCounting(transfer = true, overrides = mapOf(2L to false))
        assertEquals(0L, BudgetMath.status(BudgetPeriod.Day, 10_000, txns, now, refusedIn)!!.income)
    }

    @Test fun incomeReachesWeekAndMonthToo() {
        val counting = BudgetCounting(transfer = true)
        val settings = BudgetSettings(weeklyLimit = 20_000, monthlyLimit = 30_000, counting = counting)
        val statuses = BudgetMath.statuses(settings, txns, now).associateBy { it.period }
        // 周：20000 + 5000 收到 − 1000 已花；月：30000 + 5000 − 1000。
        assertEquals(24_000L, statuses.getValue(BudgetPeriod.Week).remaining)
        assertEquals(34_000L, statuses.getValue(BudgetPeriod.Month).remaining)
    }

    @Test fun summaryKeepsMonthRemainingAndShowsIncome() {
        val summary = BudgetMath.summary(BudgetSettings(monthlyLimit = 10_000, counting = BudgetCounting(transfer = true)), txns, now)
        assertEquals(1_000L, summary.monthSpent); assertEquals(5_000L, summary.monthIncome)
        assertEquals(14_000L, summary.monthRemaining)
    }

    @Test fun categoryBudgetsOnlyEverSeeSpending() {
        val settings = BudgetSettings(categoryLimits = mapOf(Category.MEAL to 5_000), counting = BudgetCounting(transfer = true))
        assertEquals(1_000L, BudgetMath.categoryBudgets(settings, txns, now).single().spent)
        val excluded = settings.copy(counting = BudgetCounting(overrides = mapOf(1L to false)))
        assertEquals(0L, BudgetMath.categoryBudgets(excluded, txns, now).single().spent)
    }

    @Test fun alertUsesTheRaisedLimitAndSaysWhatWasReceived() {
        val settings = BudgetSettings(dailyLimit = 10_000, counting = BudgetCounting(transfer = true))
        val busy = listOf(txn(1, 9_600, at(10, 28, 9)), transferIn.copy(amountCents = 2_000))
        val alert = BudgetAlerts.pending(settings, busy, now, emptySet()).single()
        assertEquals("日预算已用 80%", alert.title)
        assertEquals("今天已花 ¥96，日预算 ¥100，含收到 ¥20", alert.body)
    }

    @Test fun detailsListEverythingButTotalOnlyCountedSpending() {
        val excluded = BudgetCounting(overrides = mapOf(1L to false))
        val d = BudgetDetails.build(BudgetPeriod.Day, now, txns, defs = CategoryDefaults.all, counting = excluded)
        assertEquals(0L, d.spent)
        assertTrue(d.txns.isEmpty())
        // 没计入的仍在列表里，详情页才能把它再打开。
        assertEquals(listOf(1L), d.listed.filter { it.direction == Direction.OUT }.map { it.id })
    }

    @Test fun detailsDefaultToTheOldExpenseOnlyView() {
        val d = BudgetDetails.build(BudgetPeriod.Day, now, txns, defs = CategoryDefaults.all)
        assertEquals(listOf(1L), d.txns.map { it.id })
        assertEquals(3, d.listed.size)
    }
}
