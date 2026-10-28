package com.xiaopiao.app.ui.record

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What tapping 「＋」 opens by default (docs/APP-DESIGN.md §4.1). Long-press is always voice. */
enum class RecordMethod(val label: String, val stored: String) {
    Manual("手动", "manual"),
    Text("一句话", "text");

    companion object {
        fun fromStored(value: String?): RecordMethod = entries.firstOrNull { it.stored == value } ?: Manual
    }
}

class RecordMethodPrefs(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("default_method")

    fun observe(): Flow<RecordMethod> = store.data.map { RecordMethod.fromStored(it[key]) }

    suspend fun set(method: RecordMethod) {
        store.edit { it[key] = method.stored }
    }
}

val Context.recordMethodStore: DataStore<Preferences> by preferencesDataStore(name = "record_method_prefs")
