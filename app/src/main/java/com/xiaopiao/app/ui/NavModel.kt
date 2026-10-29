package com.xiaopiao.app.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which tabs sit where (docs/APP-DESIGN.md §2, v6). Pure so the routing rules are unit-testable.
 * Left capsule: 记账 · 统计 · ＋ · 小票. Right capsule: AI · 圈子. In circle mode the left capsule folds to ＋ alone
 * and the right one opens into AI · 圈子 · 游戏.
 */
enum class NavTab(val route: String, val label: String) {
    Ledger("ledger", "记账"),
    Insights("insights", "统计"),
    Receipt("receipt", "小票"),
    Chat("chat", "AI"),
    Circle("circle", "圈子"),
}

object NavModel {
    /** Pages that own the whole screen without the tab bar. */
    val BARE_ROUTES = setOf("settings")

    fun isCircleMode(route: String?): Boolean = route == NavTab.Circle.route

    fun showsTabBar(route: String?): Boolean = route !in BARE_ROUTES

    /** Tabs that fold away in circle mode (the left capsule keeps only ＋). */
    val FOLDING_LEFT = listOf(NavTab.Ledger, NavTab.Insights, NavTab.Receipt)

    /** The tab a route highlights, or null (settings, unknown). */
    fun selected(route: String?): NavTab? = NavTab.entries.firstOrNull { route?.startsWith(it.route) == true }
}

/**
 * One-shot "open the games hub" request from the 游戏 cell. The circle page consumes it exactly once, so a
 * later visit to 圈子 never lands on the hub by itself (the bug: a stale counter re-fired on every entry).
 */
@Stable
class GamesRequest {
    var pending: Boolean by mutableStateOf(false)
        private set

    fun request() { pending = true }

    /** Returns true once per request. */
    fun consume(): Boolean = pending.also { pending = false }
}
