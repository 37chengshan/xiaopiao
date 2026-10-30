# Findings

- BudgetPage has day/week/month edit-only cards; category rows also edit directly. Ledger drawer and Stats page share it.
- BudgetMath uses cents, device-local boundaries, Monday-based weeks, monthly category budgets.
- Keep edit buttons separate from new whole-card detail action; use XpSheet and existing design tokens.
- Weekly details include all seven calendar days; monthly weeks are clipped at month boundaries. Rankings resolve custom category IDs.
