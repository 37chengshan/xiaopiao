package com.xiaopiao.app.ui.today

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.receiptSoundStore by preferencesDataStore(name = "receipt_sound_prefs")

/** Whether the receipt printer plays its print/stamp sounds and vibrates (docs/DESIGN.md §7). Both on by default. */
class ReceiptSoundPrefs(private val context: Context) {
    private val key = booleanPreferencesKey("enabled")

    fun observeEnabled(): Flow<Boolean> = context.receiptSoundStore.data.map { it[key] ?: true }

    suspend fun setEnabled(enabled: Boolean) {
        context.receiptSoundStore.edit { it[key] = enabled }
    }

    private val vibrationKey = booleanPreferencesKey("vibration")

    /** Whether the printer vibrates (tick per line, stamp, tear). On by default; independent of the sound switch. */
    fun observeVibration(): Flow<Boolean> = context.receiptSoundStore.data.map { it[vibrationKey] ?: true }

    suspend fun setVibration(enabled: Boolean) {
        context.receiptSoundStore.edit { it[vibrationKey] = enabled }
    }
}
