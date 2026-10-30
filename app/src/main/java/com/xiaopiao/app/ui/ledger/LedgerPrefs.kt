package com.xiaopiao.app.ui.ledger

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ledgerStore by preferencesDataStore(name = "ledger_prefs")

/**
 * Ledger-only preferences (docs/APP-DESIGN.md §3.7): the 「隐藏金额」 eye state, the recent-search list and
 * the one-shot swipe hint. All belong to this screen, so they live next to it instead of in the global settings store.
 */
class LedgerPrefs(private val context: Context) {
    private val hideKey = booleanPreferencesKey("hide_amounts")
    private val recentKey = stringPreferencesKey("recent_searches")
    private val swipeHintKey = booleanPreferencesKey("swipe_hint_shown")

    fun observeHidden(): Flow<Boolean> = context.ledgerStore.data.map { it[hideKey] ?: false }

    suspend fun setHidden(hidden: Boolean) {
        context.ledgerStore.edit { it[hideKey] = hidden }
    }

    /**
     * The one-time 左滑删除 hint (mobile rules §5: every gesture gets a hint, but only once): false until the
     * first bill row has peeked its delete action, true forever after.
     */
    fun observeSwipeHintShown(): Flow<Boolean> = context.ledgerStore.data.map { it[swipeHintKey] ?: false }

    suspend fun markSwipeHintShown() {
        context.ledgerStore.edit { it[swipeHintKey] = true }
    }

    fun observeRecent(): Flow<List<String>> = context.ledgerStore.data.map { prefs -> prefs[recentKey].toRecent() }

    suspend fun pushRecent(query: String) {
        context.ledgerStore.edit { prefs ->
            prefs[recentKey] = RecentSearches.push(prefs[recentKey].toRecent(), query).joinToString(SEP)
        }
    }

    suspend fun clearRecent() {
        context.ledgerStore.edit { it.remove(recentKey) }
    }

    private fun String?.toRecent(): List<String> =
        this?.split(SEP)?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        /** Unit-less separator that cannot appear inside a query typed on a phone keyboard. */
        const val SEP = "\u0000"
    }
}
