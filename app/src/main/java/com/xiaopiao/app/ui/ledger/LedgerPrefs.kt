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
 * Ledger-only preferences (docs/APP-DESIGN.md §3.7): the 「隐藏金额」 eye state and the recent-search list.
 * Both belong to this screen, so they live next to it instead of in the global settings store.
 */
class LedgerPrefs(private val context: Context) {
    private val hideKey = booleanPreferencesKey("hide_amounts")
    private val recentKey = stringPreferencesKey("recent_searches")

    fun observeHidden(): Flow<Boolean> = context.ledgerStore.data.map { it[hideKey] ?: false }

    suspend fun setHidden(hidden: Boolean) {
        context.ledgerStore.edit { it[hideKey] = hidden }
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
