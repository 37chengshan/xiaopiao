package com.xiaopiao.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavModelTest {
    @Test fun leftCapsuleFoldsRecordLedgerInsightsReceiptAndAiMovesRight() {
        assertEquals(listOf(NavTab.Ledger, NavTab.Insights, NavTab.Receipt), NavModel.FOLDING_LEFT)
        assertFalse(NavTab.Chat in NavModel.FOLDING_LEFT)
        assertEquals(NavTab.Receipt, NavModel.selected("receipt"))
    }

    @Test fun onlyCircleRouteIsCircleMode() {
        assertTrue(NavModel.isCircleMode("circle"))
        NavTab.entries.filter { it != NavTab.Circle }.forEach { assertFalse(NavModel.isCircleMode(it.route)) }
        assertFalse(NavModel.isCircleMode(null))
    }

    @Test fun settingsHidesTabBarEveryTabShowsIt() {
        assertFalse(NavModel.showsTabBar("settings"))
        NavTab.entries.forEach { assertTrue(NavModel.showsTabBar(it.route)) }
        assertNull(NavModel.selected("settings"))
    }

    @Test fun gamesRequestIsConsumedExactlyOnce() {
        val r = GamesRequest()
        assertFalse(r.pending)
        r.request()
        assertTrue(r.pending)
        assertTrue(r.consume())
        assertFalse(r.pending)
        // Entering 圈子 later must not open the hub again.
        assertFalse(r.consume())
    }
}
