package com.xiaopiao.app.ui


/**
 * The app's own language switch (设置 → 语言), not the system one.
 *
 * It is an explicit process value rather than `Locale.getDefault()`: the activity writes it in
 * `onCreate` from the resources Android already resolved for the per-app locale, and unit tests (which run
 * on a JVM whose default locale depends on the machine) keep the source language unless they say otherwise.
 */
object AppLocale {
    @Volatile
    var english: Boolean = false
}

val isEnglish: Boolean get() = AppLocale.english

/**
 * Chinese is the source language: [zh] is the string the product was written in, [en] the translation.
 * Every user-visible literal that is not data goes through here, so “switch to English” means English,
 * not “Chinese with a few English labels”.
 */
fun tr(zh: String, en: String): String = if (isEnglish) en else zh
