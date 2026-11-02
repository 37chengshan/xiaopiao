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
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The v8 bar (生活 on, the preview build); [everydayBarKeepsGamesAndCircleInTheMiddle] covers the everyday one. */
class NavModelTest {
    @Before fun lifeOn() { NavModel.lifeTab = true }
    @After fun reset() { NavModel.lifeTab = com.xiaopiao.app.BuildConfig.LIFE_TAB }

    @Test fun everydayBarKeepsGamesAndCircleInTheMiddle() {
        NavModel.lifeTab = false
        assertEquals(listOf(NavSlot.Fold, NavSlot.Plus, NavSlot.Chat, NavSlot.Circle, NavSlot.Games), NavModel.slots(NavSide.Social))
        assertEquals(NavTab.Games, NavModel.selected("games"))
        assertEquals(NavTab.Circle, NavModel.neighbor("chat", +1))
        assertEquals(NavTab.Games, NavModel.neighbor("circle", +1))
        assertNull(NavModel.neighbor("games", +1))
        assertEquals(NavSide.Social, NavModel.sideOf("games"))
    }

    @Test fun barAlwaysShowsFiveIconsOnBothSides() {
        NavSide.entries.forEach { assertEquals(NavModel.ICON_COUNT, NavModel.slots(it).size) }
        // Plus is the one element that exists on both sides.
        NavSide.entries.forEach { assertEquals(1, NavModel.slots(it).count { s -> s == NavSlot.Plus }) }
    }

    @Test fun ledgerSideHasNoAiAndPlusSitsBetweenTabs() {
        val l = NavModel.slots(NavSide.Ledger)
        assertEquals(listOf(NavSlot.Ledger, NavSlot.Receipt, NavSlot.Plus, NavSlot.Insights, NavSlot.Circle), l)
        assertFalse(NavSlot.Chat in l)
        // The social half (v8): ＋ right of 账单, then AI · 生活 · 圈子 with 圈子 rightmost.
        assertEquals(listOf(NavSlot.Fold, NavSlot.Plus, NavSlot.Chat, NavSlot.Life, NavSlot.Circle), NavModel.slots(NavSide.Social))
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
            // Moving between AI / 生活 / 圈子 never lands on the ledger side.
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
        // 游戏 sits inside 圈子: the bar highlights 圈子 and keeps the social side.
        assertEquals(NavTab.Circle, NavModel.selected("games"))
        assertEquals(NavSide.Social, NavModel.sideOf("games"))
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
        // Labels follow the app's own language switch (ui.AppLocale), not the JVM default locale.
        val original = com.xiaopiao.app.ui.AppLocale.english
        try {
            com.xiaopiao.app.ui.AppLocale.english = false
            assertEquals("AI · 生活 · 圈子", LaunchSide.Social.label)
            com.xiaopiao.app.ui.AppLocale.english = true
            assertEquals("AI · Life · Circles", LaunchSide.Social.label)
        } finally { com.xiaopiao.app.ui.AppLocale.english = original }
    }

    @Test fun swipingWalksOneSideAndStopsAtItsEnds() {
        assertEquals(NavTab.Receipt, NavModel.neighbor("ledger", +1))
        assertEquals(NavTab.Insights, NavModel.neighbor("receipt", +1))
        assertNull(NavModel.neighbor("insights", +1))      // never crosses into AI
        assertNull(NavModel.neighbor("ledger", -1))
        assertEquals(NavTab.Life, NavModel.neighbor("chat", +1))
        assertEquals(NavTab.Circle, NavModel.neighbor("life", +1))
        assertNull(NavModel.neighbor("circle", +1))
        assertNull(NavModel.neighbor("chat", -1))
        // From 游戏 a swipe behaves as from 圈子.
        assertEquals(NavTab.Life, NavModel.neighbor("games", -1))
        assertNull(NavModel.neighbor("settings", +1))
    }
}
