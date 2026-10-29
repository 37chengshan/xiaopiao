package com.xiaopiao.app.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 跟随系统 / 浅色 / 深色. */
enum class ThemeMode(val label: String) { System("跟随系统"), Light("浅色"), Dark("深色") }

private val Context.themeStore by preferencesDataStore(name = "theme_prefs")

/** Persists the user's appearance choice (docs/APP-DESIGN.md §1). */
class ThemePrefs(private val context: Context) {
    private val modeKey = stringPreferencesKey("theme_mode")
    private val themeKey = stringPreferencesKey("color_theme")

    fun observeMode(): Flow<ThemeMode> = context.themeStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[modeKey] } ?: ThemeMode.System
    }

    suspend fun setMode(mode: ThemeMode) {
        context.themeStore.edit { it[modeKey] = mode.name }
    }

    fun observeTheme(): Flow<ColorTheme> = context.themeStore.data.map { prefs -> ColorTheme.fromId(prefs[themeKey]) }

    suspend fun setTheme(theme: ColorTheme) {
        context.themeStore.edit { it[themeKey] = theme.id }
    }
}
