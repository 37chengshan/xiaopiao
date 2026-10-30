package com.xiaopiao.app.ui.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.xiaopiao.app.ui.components.GroupedRow
import com.xiaopiao.app.ui.components.GroupedSection
import java.util.Locale

/** Per-app choice. Android 13+ also exposes it in system App language settings. */
object AppLanguage {
    fun selected(context: Context): String = if (Build.VERSION.SDK_INT >= 33)
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        else context.getSharedPreferences("language-v1", Context.MODE_PRIVATE).getString("tag", "").orEmpty()
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val tag = selected(context)
        if (tag.isBlank()) return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return context.createConfigurationContext(config)
    }
    fun apply(context: Context, tag: String) {
        require(tag in listOf("", "zh-CN", "en"))
        context.getSharedPreferences("language-v1", Context.MODE_PRIVATE).edit().putString("tag", tag).apply()
        Locale.setDefault(if (tag.isBlank()) android.content.res.Resources.getSystem().configuration.locales[0] else Locale.forLanguageTag(tag))
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        else {
            var current = context
            while (current is ContextWrapper && current !is Activity) current = current.baseContext
            (current as? Activity)?.recreate()
        }
    }
}

@Composable
fun LanguageSection() {
    val context = LocalContext.current
    val en = LocalConfiguration.current.locales[0].language == "en"
    val selected = AppLanguage.selected(context)
    GroupedSection(header = if (en) "Language" else "语言 / Language") {
        listOf("" to if (en) "Follow system" else "跟随系统", "zh-CN" to "中文", "en" to "English").forEach { (tag, label) ->
            item(tag) { GroupedRow(title = label, value = if (selected == tag) "✓" else null,
                onClick = { if (selected != tag) AppLanguage.apply(context, tag) }) }
        }
    }
}
