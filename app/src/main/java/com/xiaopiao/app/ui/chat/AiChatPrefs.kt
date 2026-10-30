package com.xiaopiao.app.ui.chat

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.aiChatStore by preferencesDataStore("ai_chat")
class AiChatPrefs(context: Context) {
    private val store = context.applicationContext.aiChatStore
    private val automatic = booleanPreferencesKey("automatic_entry")
    val autoEntry = store.data.map { it[automatic] ?: false }
    suspend fun setAutoEntry(enabled: Boolean) { store.edit { it[automatic] = enabled } }
}
