package com.xiaopiao.app.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/** 跟随系统 / 浅色 / 深色. */
enum class ThemeMode(val label: String) { System("跟随系统"), Light("浅色"), Dark("深色") }

private val Context.themeStore by preferencesDataStore(name = "theme_prefs")

/** Persists the user's appearance choice (docs/APP-DESIGN.md §1). */
class ThemePrefs(private val context: Context) {
    private val modeKey = stringPreferencesKey("theme_mode")
    private val themeKey = stringPreferencesKey("color_theme")

    fun observeMode(): Flow<ThemeMode> = context.themeStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[modeKey] } ?: ThemeMode.System
    }.onEach { cachedMode = it }

    suspend fun setMode(mode: ThemeMode) {
        context.themeStore.edit { it[modeKey] = mode.name }
    }

    fun observeTheme(): Flow<ColorTheme> = context.themeStore.data.map { prefs -> ColorTheme.fromId(prefs[themeKey]) }
        .onEach { cachedTheme = it }

    suspend fun setTheme(theme: ColorTheme) {
        context.themeStore.edit { it[themeKey] = theme.id }
    }

    /** Last emitted value, so opening 外观 on a warm app does not show the wrong option for a frame. */
    companion object {
        @Volatile var cachedMode: ThemeMode? = null
            private set
        @Volatile var cachedTheme: ColorTheme? = null
            private set
    }
}
