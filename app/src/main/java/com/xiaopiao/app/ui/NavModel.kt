package com.xiaopiao.app.ui

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Every page the tab bar can land on. [route] doubles as the NavHost route and the bar item id. */
enum class NavTab(val route: String, val label: String) {
    Ledger("ledger", "记账"),
    Receipt("receipt", "小票"),
    Insights("insights", "统计"),
    Chat("chat", "AI"),
    Circle("circle", "圈子"),
    Games("games", "游戏"),
}

/** The two halves of the app. 记账 side is the default; 社交 side opens when 圈子 is tapped. */
enum class NavSide { Ledger, Social }

/** One slot in the bar. The bar always shows exactly [NavModel.ICON_COUNT] of them. */
enum class NavSlot { Ledger, Receipt, Insights, Plus, Fold, Chat, Circle, Games }

/** 设置 → 打开 App 时进入. */
enum class LaunchSide(val label: String) {
    Ledger("记账"),
    Social("AI · 圈子"),
}

/**
 * Which tabs sit where (docs/APP-DESIGN.md §2, v7). Pure so the rules are unit-testable.
 *
 * ```
 * 记账态   ╭ 记账  小票  (⊕)  统计 ╮ ╭ 圈子 ╮        ← ⊕ at the screen centre
 * 圈子态   ╭ 账单  ＋ ╮ ╭ AI  圈子  游戏 ╮            ← still 5 icons
 * ```
 * The side is a function of the current route, so tapping 圈子 (route `circle`) is what flips the bar; only 账单 flips it back.
 */
object NavModel {
    const val ICON_COUNT = 5

    /** Pages that own the whole screen without the tab bar. */
    val BARE_ROUTES = setOf("settings")

    val LEDGER_SIDE = listOf(NavTab.Ledger, NavTab.Receipt, NavTab.Insights)
    val SOCIAL_SIDE = listOf(NavTab.Chat, NavTab.Circle, NavTab.Games)

    fun sideOf(route: String?): NavSide? = when {
        route == null -> null
        LEDGER_SIDE.any { route.startsWith(it.route) } -> NavSide.Ledger
        SOCIAL_SIDE.any { route.startsWith(it.route) } -> NavSide.Social
        else -> null
    }

    /** Settings and other bare pages carry no side of their own: the bar (hidden) keeps the one it had. */
    fun side(route: String?, previous: NavSide): NavSide = sideOf(route) ?: previous

    fun showsTabBar(route: String?): Boolean = route !in BARE_ROUTES

    fun selected(route: String?): NavTab? = NavTab.entries.firstOrNull { route?.startsWith(it.route) == true }

    fun startRoute(launch: LaunchSide): String = when (launch) {
        LaunchSide.Ledger -> NavTab.Ledger.route
        LaunchSide.Social -> NavTab.Chat.route
    }

    /** The icons on screen for [side], left to right across both capsules. Always [ICON_COUNT] long. */
    fun slots(side: NavSide): List<NavSlot> = when (side) {
        NavSide.Ledger -> listOf(NavSlot.Ledger, NavSlot.Receipt, NavSlot.Plus, NavSlot.Insights, NavSlot.Circle)
        NavSide.Social -> listOf(NavSlot.Fold, NavSlot.Plus, NavSlot.Chat, NavSlot.Circle, NavSlot.Games)
    }

    /** Where tapping [slot] goes, or null when it does not navigate (＋ opens the record sheet; the bar stays). */
    fun destination(slot: NavSlot, lastLedger: NavTab): NavTab? = when (slot) {
        NavSlot.Ledger -> NavTab.Ledger
        NavSlot.Receipt -> NavTab.Receipt
        NavSlot.Insights -> NavTab.Insights
        NavSlot.Chat -> NavTab.Chat
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

    suspend fun setLaunch(side: LaunchSide) {
        store.edit { it[launchKey] = side.name }
    }
}
