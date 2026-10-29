package com.xiaopiao.app.ui.ledger

import com.xiaopiao.app.domain.BudgetSummary
import com.xiaopiao.app.domain.yuanGrouped

/**
 * The summary card's bottom strip, as one string: budget entry when no monthly budget is set, the user's own
 * remaining quota once it is, and the mask when amounts are hidden. Never a recommendation.
 */
internal fun budgetLineText(budget: BudgetSummary?, hidden: Boolean): String {
    val remaining = budget?.monthRemaining
    return when {
        budget?.monthLimit == null || remaining == null -> "设置预算，合理规划支出"
        hidden -> "本月预算剩余 ¥ ···"
        remaining >= 0 -> "本月预算剩余 ¥" + remaining.yuanGrouped()
        else -> "本月已超预算 ¥" + (-remaining).yuanGrouped()
    }
}
