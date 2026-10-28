package com.xiaopiao.app.insights

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.insightsStore by preferencesDataStore(name = "insights_prefs")

/** Tiny local prefs for the insights lane: which subscriptions the user marked "我已取消". */
class InsightsPrefs(private val context: Context) {
    private val cancelledKey = stringSetPreferencesKey("cancelled_subscriptions")

    fun observeCancelled(): Flow<Set<String>> = context.insightsStore.data.map { it[cancelledKey].orEmpty() }

    suspend fun cancelled(): Set<String> = observeCancelled().first()

    suspend fun setCancelled(merchant: String, cancelled: Boolean) {
        context.insightsStore.edit { prefs: androidx.datastore.preferences.core.MutablePreferences ->
            val current = prefs[cancelledKey].orEmpty()
            val key = normalizeMerchant(merchant)
            prefs[cancelledKey] = if (cancelled) current + key else current - key
        }
    }

    companion object {
        fun isCancelled(set: Set<String>, merchant: String): Boolean = normalizeMerchant(merchant) in set
    }
}
