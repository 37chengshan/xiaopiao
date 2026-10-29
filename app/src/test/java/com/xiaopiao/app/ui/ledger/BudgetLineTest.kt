package com.xiaopiao.app.ui.ledger

import org.junit.Assert.assertEquals
import org.junit.Test

/** The summary card's budget strip switches copy once a budget exists (task A.2). */
class BudgetLineTest {

    @Test
    fun `no budget shows the entry, never a recommendation`() {
        assertEquals("设置预算，合理规划支出", budgetLineText(null, hidden = false))
    }

    @Test
    fun `a set budget shows what is left`() {
        val budget = BudgetSummary(limitCents = 300_000, spentCents = 128_050)
        assertEquals("本月预算剩余 ¥1,719.50", budgetLineText(budget, hidden = false))
    }

    @Test
    fun `an overdrawn budget says by how much`() {
        val budget = BudgetSummary(limitCents = 50_000, spentCents = 61_200)
        assertEquals("本月已超预算 ¥112.00", budgetLineText(budget, hidden = false))
    }

    @Test
    fun `masked amounts hide the remaining number too`() {
        val budget = BudgetSummary(limitCents = 300_000, spentCents = 0)
        assertEquals("本月预算剩余 ¥ ···", budgetLineText(budget, hidden = true))
    }
}
