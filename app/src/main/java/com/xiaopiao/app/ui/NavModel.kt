package com.xiaopiao.app.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/** Every page the tab bar can land on. [route] doubles as the NavHost route and the bar item id. */
enum class NavTab(val route: String, private val zhLabel: String) {
    Ledger("ledger", "记账"),
    Receipt("receipt", "小票"),
    Insights("insights", "统计"),
    Chat("chat", "AI"),
    Life("life", "生活"),
    Circle("circle", "圈子"),
    /** Not in the bar since v8: reached from the 圈子 | 游戏 switch at the top of 圈子. */
    Games("games", "游戏");
    val label: String get() = if (!com.xiaopiao.app.ui.isEnglish) zhLabel else when(this) {
        Ledger -> "Ledger"; Receipt -> "Receipt"; Insights -> "Insights"; Chat -> "AI"; Life -> "Life"; Circle -> "Circles"; Games -> "Games"
    }
}

/** The two halves of the app. 记账 side is the default; 社交 side opens when 圈子 is tapped. */
enum class NavSide { Ledger, Social }

/** One slot in the bar. The bar always shows exactly [NavModel.ICON_COUNT] of them. */
enum class NavSlot { Ledger, Receipt, Insights, Plus, Fold, Chat, Life, Circle, Games }

/** 设置 → 打开 App 时进入. */
enum class LaunchSide(private val zhLabel: String) {
    Ledger("记账"),
    Social("AI · 圈子");
    val label: String get() = when {
        this == Ledger -> if (!com.xiaopiao.app.ui.isEnglish) zhLabel else "Ledger"
        NavModel.lifeTab -> if (!com.xiaopiao.app.ui.isEnglish) "AI · 生活 · 圈子" else "AI · Life · Circles"
        else -> if (!com.xiaopiao.app.ui.isEnglish) zhLabel else "AI · Circles"
    }
}

/**
 * Which tabs sit where (docs/APP-DESIGN.md §2, v7). Pure so the rules are unit-testable.
 *
 * ```
 * 记账态   ╭ 记账  小票  (⊕)  统计 ╮ ╭ 圈子 ╮        ← ⊕ at the screen centre
 * 圈子态   ╭ 账单  ＋ ╮ ╭ AI  生活  圈子 ╮            ← still 5 icons (v8, tt 2026-11-02)
 * ```
 * The side is a function of the current route, so tapping 圈子 (route `circle`) is what flips the bar; only 账单 flips it back.
 */
object NavModel {
    const val ICON_COUNT = 5

    /** Pages that own the whole screen without the tab bar. */
    val BARE_ROUTES = setOf("settings")

    /**
     * 生活 tab (v8) on: only the preview build (`com.xiaopiao.app.preview`) until 生活 ships; the everyday build keeps
     * AI · 圈子 · 游戏 (tt 2026-11-02). Tests flip it to cover both bars.
     */
    var lifeTab: Boolean = com.xiaopiao.app.BuildConfig.LIFE_TAB

    val LEDGER_SIDE = listOf(NavTab.Ledger, NavTab.Receipt, NavTab.Insights)
    /** Left to right, also the order a horizontal swipe walks through (only within a side). */
    val SOCIAL_SIDE get() = if (lifeTab) listOf(NavTab.Chat, NavTab.Life, NavTab.Circle) else listOf(NavTab.Chat, NavTab.Circle, NavTab.Games)

    /** With 生活 on, 游戏 lives inside 圈子: it counts as 圈子 for the bar, the side and swiping. */
    private fun barTab(tab: NavTab): NavTab = if (lifeTab && tab == NavTab.Games) NavTab.Circle else tab

    private fun tabOf(route: String?): NavTab? = NavTab.entries.firstOrNull { route?.startsWith(it.route) == true }

    fun sideOf(route: String?): NavSide? {
        val tab = tabOf(route)?.let(::barTab) ?: return null
        return when (tab) {
            in LEDGER_SIDE -> NavSide.Ledger
            in SOCIAL_SIDE -> NavSide.Social
            else -> null
        }
    }

    /** Settings and other bare pages carry no side of their own: the bar (hidden) keeps the one it had. */
    fun side(route: String?, previous: NavSide): NavSide = sideOf(route) ?: previous

    fun showsTabBar(route: String?): Boolean = route !in BARE_ROUTES

    fun selected(route: String?): NavTab? = tabOf(route)?.let(::barTab)

    /**
     * The tab a horizontal swipe lands on: [step] = +1 for the next tab to the right, -1 for the left. Null at
     * either end of the side — swiping never crosses from 记账 to the social side (tt 2026-11-02).
     */
    fun neighbor(route: String?, step: Int): NavTab? {
        val tab = tabOf(route)?.let(::barTab) ?: return null
        val side = if (tab in LEDGER_SIDE) LEDGER_SIDE else if (tab in SOCIAL_SIDE) SOCIAL_SIDE else return null
        return side.getOrNull(side.indexOf(tab) + step)
    }

    fun startRoute(launch: LaunchSide): String = when (launch) {
        LaunchSide.Ledger -> NavTab.Ledger.route
        LaunchSide.Social -> NavTab.Chat.route
    }

    /** The icons on screen for [side], left to right across both capsules. Always [ICON_COUNT] long. */
    fun slots(side: NavSide): List<NavSlot> = when (side) {
        NavSide.Ledger -> listOf(NavSlot.Ledger, NavSlot.Receipt, NavSlot.Plus, NavSlot.Insights, NavSlot.Circle)
        NavSide.Social -> if (lifeTab) listOf(NavSlot.Fold, NavSlot.Plus, NavSlot.Chat, NavSlot.Life, NavSlot.Circle)
        else listOf(NavSlot.Fold, NavSlot.Plus, NavSlot.Chat, NavSlot.Circle, NavSlot.Games)
    }

    /** Where tapping [slot] goes, or null when it does not navigate (＋ opens the record sheet; the bar stays). */
    fun destination(slot: NavSlot, lastLedger: NavTab): NavTab? = when (slot) {
        NavSlot.Ledger -> NavTab.Ledger
        NavSlot.Receipt -> NavTab.Receipt
        NavSlot.Insights -> NavTab.Insights
        NavSlot.Chat -> NavTab.Chat
        NavSlot.Life -> NavTab.Life
        // 圈子 always lands on the group chat home, never on 游戏 or a remembered sub-page.
        NavSlot.Circle -> NavTab.Circle
        NavSlot.Games -> NavTab.Games
        // 账单 folds the bar back and returns to the last 记账-side tab.
        NavSlot.Fold -> lastLedger
        NavSlot.Plus -> null
    }

    /** The 记账-side tab to return to: [route] when it is one, otherwise whatever it was. */
    fun lastLedger(route: String?, previous: NavTab): NavTab =
        LEDGER_SIDE.firstOrNull { route?.startsWith(it.route) == true } ?: previous
}

val Context.navStore by preferencesDataStore(name = "nav_prefs")

/** Persists [LaunchSide] (default 记账). */
class NavPrefs(private val store: DataStore<Preferences>) {
    private val launchKey = stringPreferencesKey("launch_side")

    fun observeLaunch(): Flow<LaunchSide> =
        store.data.map { p -> LaunchSide.entries.firstOrNull { it.name == p[launchKey] } ?: LaunchSide.Ledger }
            .onEach { cachedLaunch = it }

    suspend fun setLaunch(side: LaunchSide) {
        store.edit { it[launchKey] = side.name }
    }

    companion object {
        @Volatile var cachedLaunch: LaunchSide? = null
            private set
    }
}
