package com.xiaopiao.app.ui.ledger

import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.yuanGrouped

/**
 * What the ledger needs to print the budget line of the summary card.
 *
 * TODO(budget-v7): the budget lane owns the real type; at merge, point this at it (or delete it and
 * pass theirs). Only [limitCents] and [spentCents] are read, and only to state a number the user set.
 */
data class BudgetSummary(val limitCents: Cents, val spentCents: Cents) {
    val remainingCents: Cents get() = limitCents - spentCents
}

/**
 * The summary card's bottom strip, as one string: budget entry when nothing is set, the user's own
 * remaining quota once it is, and the mask when amounts are hidden. Never a recommendation.
 */
internal fun budgetLineText(budget: BudgetSummary?, hidden: Boolean): String = when {
    budget == null -> "设置预算，合理规划支出"
    hidden -> "本月预算剩余 ¥ ···"
    budget.remainingCents >= 0 -> "本月预算剩余 ¥" + budget.remainingCents.yuanGrouped()
    else -> "本月已超预算 ¥" + (-budget.remainingCents).yuanGrouped()
}
