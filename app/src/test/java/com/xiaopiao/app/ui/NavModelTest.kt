package com.xiaopiao.app.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavModelTest {
    @Test fun barAlwaysShowsFiveIconsOnBothSides() {
        NavSide.entries.forEach { assertEquals(NavModel.ICON_COUNT, NavModel.slots(it).size) }
        // Plus is the one element that exists on both sides.
        NavSide.entries.forEach { assertEquals(1, NavModel.slots(it).count { s -> s == NavSlot.Plus }) }
    }

    @Test fun ledgerSideHasNoAiAndPlusSitsBetweenTabs() {
        val l = NavModel.slots(NavSide.Ledger)
        assertEquals(listOf(NavSlot.Ledger, NavSlot.Receipt, NavSlot.Plus, NavSlot.Insights, NavSlot.Circle), l)
        assertFalse(NavSlot.Chat in l)
        // The social half: ＋ right of 账单, then AI · 圈子 · 游戏.
        assertEquals(listOf(NavSlot.Fold, NavSlot.Plus, NavSlot.Chat, NavSlot.Circle, NavSlot.Games), NavModel.slots(NavSide.Social))
    }

    @Test fun tappingCircleOpensTheSocialSideOnTheCircleHome() {
        val dest = NavModel.destination(NavSlot.Circle, lastLedger = NavTab.Insights)!!
        assertEquals(NavTab.Circle, dest)
        assertEquals(NavSide.Social, NavModel.sideOf(dest.route))
        // 圈子 is never 游戏, whatever was open before.
        assertTrue(NavModel.destination(NavSlot.Circle, NavTab.Ledger) != NavTab.Games)
    }

    @Test fun plusNeverNavigatesAndSocialTabsKeepTheBarSocial() {
        assertNull(NavModel.destination(NavSlot.Plus, NavTab.Ledger))
        NavModel.SOCIAL_SIDE.forEach { tab ->
            assertEquals(NavSide.Social, NavModel.sideOf(tab.route))
            // Moving between AI / 圈子 / 游戏 never lands on the ledger side.
            NavModel.slots(NavSide.Social).mapNotNull { NavModel.destination(it, NavTab.Ledger) }
                .filter { it != NavTab.Ledger }.forEach { assertEquals(NavSide.Social, NavModel.sideOf(it.route)) }
        }
    }

    @Test fun onlyFoldReturnsToTheLastLedgerTab() {
        assertEquals(NavTab.Receipt, NavModel.destination(NavSlot.Fold, NavTab.Receipt))
        assertEquals(NavTab.Ledger, NavModel.destination(NavSlot.Fold, NavTab.Ledger))
        // Remembering: ledger-side routes update it, social / settings routes leave it alone.
        assertEquals(NavTab.Insights, NavModel.lastLedger("insights", NavTab.Ledger))
        assertEquals(NavTab.Insights, NavModel.lastLedger("circle", NavTab.Insights))
        assertEquals(NavTab.Insights, NavModel.lastLedger("settings", NavTab.Insights))
        assertEquals(NavTab.Insights, NavModel.lastLedger(null, NavTab.Insights))
    }

    @Test fun settingsHidesTheBarAndKeepsTheSide() {
        assertFalse(NavModel.showsTabBar("settings"))
        NavTab.entries.forEach { assertTrue(NavModel.showsTabBar(it.route)) }
        assertNull(NavModel.sideOf("settings"))
        assertEquals(NavSide.Social, NavModel.side("settings", NavSide.Social))
        assertEquals(NavSide.Ledger, NavModel.side("ledger", NavSide.Social))
        assertNull(NavModel.selected("settings"))
    }

    @Test fun everySlotDestinationBelongsToTheSideItIsShownOn() {
        for (side in NavSide.entries) for (slot in NavModel.slots(side)) {
            val dest = NavModel.destination(slot, NavTab.Ledger) ?: continue
            // Ledger-side slots lead to ledger tabs, except 圈子 (the way across); social slots stay social, except 账单 (the way back).
            val ok = when (slot) {
                NavSlot.Circle -> true
                NavSlot.Fold -> NavModel.sideOf(dest.route) == NavSide.Ledger
                else -> NavModel.sideOf(dest.route) == side
            }
            assertTrue("$slot on $side -> $dest", ok)
        }
    }

    @Test fun launchSideChoosesTheStartRoute() {
        assertEquals("ledger", NavModel.startRoute(LaunchSide.Ledger))
        assertEquals("chat", NavModel.startRoute(LaunchSide.Social))
        assertEquals(NavSide.Social, NavModel.sideOf(NavModel.startRoute(LaunchSide.Social)))
        assertEquals(NavSide.Ledger, NavModel.sideOf(NavModel.startRoute(LaunchSide.Ledger)))
    }

    @Test fun launchSidePreferenceDefaultsToLedgerAndPersists() = runBlocking {
        val file = File.createTempFile("nav_prefs", ".preferences_pb").also { it.delete(); it.deleteOnExit() }
        val prefs = NavPrefs(PreferenceDataStoreFactory.create(produceFile = { file }))
        assertEquals(LaunchSide.Ledger, prefs.observeLaunch().first())
        prefs.setLaunch(LaunchSide.Social)
        assertEquals(LaunchSide.Social, prefs.observeLaunch().first())
        prefs.setLaunch(LaunchSide.Ledger)
        assertEquals(LaunchSide.Ledger, prefs.observeLaunch().first())
        assertNotNull(LaunchSide.entries.firstOrNull { it.label == "AI · 圈子" })
    }
}
