package com.xiaopiao.app.ui.ledger

import com.xiaopiao.app.domain.BudgetSummary
import com.xiaopiao.app.domain.yuanGrouped
import com.xiaopiao.app.ui.tr

/**
 * The summary card's bottom strip, as one string: budget entry when no monthly budget is set, the user's own
 * remaining quota once it is, and the mask when amounts are hidden. Never a recommendation.
 */
internal fun budgetLineText(budget: BudgetSummary?, hidden: Boolean): String {
    val remaining = budget?.monthRemaining
    return when {
        budget?.monthLimit == null || remaining == null -> tr("设置预算，合理规划支出", "Set a budget and plan ahead")
        hidden -> tr("本月预算剩余 ¥ ···", "Budget left this month ¥ ···")
        remaining >= 0 -> tr("本月预算剩余 ¥", "Budget left this month ¥") + remaining.yuanGrouped()
        else -> tr("本月已超预算 ¥", "Over budget this month ¥") + (-remaining).yuanGrouped()
    }
}
