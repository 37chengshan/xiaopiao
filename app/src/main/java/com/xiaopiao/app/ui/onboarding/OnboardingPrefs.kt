package com.xiaopiao.app.ui.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingStore by preferencesDataStore(name = "onboarding_prefs")

/** First-launch gate. [reset] is what 「我的 → 重看引导」 should call. */
class OnboardingPrefs(private val context: Context) {
    private val doneKey = booleanPreferencesKey("done")

    fun observeDone(): Flow<Boolean> = context.onboardingStore.data.map { it[doneKey] ?: false }
    suspend fun markDone() { context.onboardingStore.edit { it[doneKey] = true } }
    suspend fun reset() { context.onboardingStore.edit { it[doneKey] = false } }
}
