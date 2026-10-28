package com.xiaopiao.app.ui.today

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.receiptSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "receipt_settings")

/** Persists [ReceiptSettings]; the resident receipt strip and the 小票页 both read it (docs/APP-DESIGN.md §3.1). */
class ReceiptPrefs(private val store: DataStore<Preferences>) {
    private val themeKey = stringPreferencesKey("theme")
    private val inkKey = intPreferencesKey("custom_ink")
    private val contentKey = intPreferencesKey("content_bits")

    fun observe(): Flow<ReceiptSettings> = store.data.map { p ->
        ReceiptSettings(
            theme = PaperTheme.fromId(p[themeKey]),
            customInk = p[inkKey],
            content = p[contentKey]?.let(ReceiptContent::fromBits) ?: ReceiptPreset.Full.content,
        )
    }

    suspend fun setTheme(theme: PaperTheme) { store.edit { it[themeKey] = theme.id } }

    /** null clears the custom ink and goes back to the theme's own. */
    suspend fun setCustomInk(argb: Int?) {
        store.edit { if (argb == null) it.remove(inkKey) else it[inkKey] = argb }
    }

    suspend fun setContent(content: ReceiptContent) { store.edit { it[contentKey] = content.toBits() } }
}
