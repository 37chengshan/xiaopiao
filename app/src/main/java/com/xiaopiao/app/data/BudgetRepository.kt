package com.xiaopiao.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xiaopiao.app.domain.BudgetMath
import com.xiaopiao.app.domain.BudgetPeriod
import com.xiaopiao.app.domain.BudgetSettings
import com.xiaopiao.app.domain.BudgetSummary
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.LedgerRepository
import com.xiaopiao.app.domain.Txn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.budgetStore by preferencesDataStore(name = "budget_prefs")

/**
 * 预算存储用 **DataStore** 而不是 Room：这里只有几组标量配置（三个周期上限 + 分类上限 + 已提醒的 key），
 * 没有查询、没有关系，也不该因为预算再加一个 Room 版本（Room 版本被其它 lane 共用）。
 * 已花/剩余永远由账本实时算出来，不落库，所以不存在两边对不上的问题。
 */
class BudgetRepository(private val context: Context) {
    private val dailyKey = longPreferencesKey("daily_limit")
    private val weeklyKey = longPreferencesKey("weekly_limit")
    private val monthlyKey = longPreferencesKey("monthly_limit")
    private val categoryKey = stringSetPreferencesKey("category_limits")
    private val notifiedKey = stringSetPreferencesKey("notified_milestones")

    fun observeSettings(): Flow<BudgetSettings> = context.budgetStore.data.map { it.toSettings() }

    suspend fun settings(): BudgetSettings = observeSettings().first()

    fun observeSummary(ledger: LedgerRepository, now: () -> Long = { System.currentTimeMillis() }): Flow<BudgetSummary> =
        combine(observeSettings(), ledger.observeAll()) { settings, txns -> BudgetMath.summary(settings, txns, now()) }

    suspend fun setLimit(period: BudgetPeriod, limit: Cents?) {
        context.budgetStore.edit { prefs ->
            val key = when (period) {
                BudgetPeriod.Day -> dailyKey
                BudgetPeriod.Week -> weeklyKey
                BudgetPeriod.Month -> monthlyKey
            }
            // 0 或负数等于「关掉这个周期」，与留空一致。
            if (limit == null || limit <= 0) prefs.remove(key) else prefs[key] = limit
        }
    }

    suspend fun setCategoryLimit(category: Category, limit: Cents?) {
        context.budgetStore.edit { prefs ->
            val current = decodeCategories(prefs[categoryKey]).toMutableMap()
            if (limit == null || limit <= 0) current.remove(category) else current[category] = limit
            prefs[categoryKey] = current.map { (c, cents) -> "${c.name}:$cents" }.toSet()
        }
    }

    suspend fun notified(): Set<String> = context.budgetStore.data.first()[notifiedKey].orEmpty()

    /** 只保留当前仍有效的 key，避免一年后攒出几千条。 */
    suspend fun markNotified(keys: Set<String>, pruneBefore: Long) {
        context.budgetStore.edit { prefs ->
            val kept = prefs[notifiedKey].orEmpty().filterTo(mutableSetOf()) { key ->
                val start = key.split(':').getOrNull(1)?.toLongOrNull()
                start == null || start >= pruneBefore
            }
            prefs[notifiedKey] = kept + keys
        }
    }

    private fun androidx.datastore.preferences.core.Preferences.toSettings(): BudgetSettings = BudgetSettings(
        dailyLimit = this[dailyKey]?.takeIf { it > 0 },
        weeklyLimit = this[weeklyKey]?.takeIf { it > 0 },
        monthlyLimit = this[monthlyKey]?.takeIf { it > 0 },
        categoryLimits = decodeCategories(this[categoryKey]),
    )

    private fun decodeCategories(raw: Set<String>?): Map<Category, Cents> =
        raw.orEmpty().mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) return@mapNotNull null
            val category = Category.entries.firstOrNull { it.name == parts[0] } ?: return@mapNotNull null
            val cents = parts[1].toLongOrNull() ?: return@mapNotNull null
            category to cents
        }.toMap()
}

/** 供 UI 直接算一次（无 Flow 的场合，例如通知 worker）。 */
suspend fun BudgetRepository.snapshot(ledger: LedgerRepository, now: Long): Pair<BudgetSettings, List<Txn>> =
    settings() to ledger.observeAll().first()
