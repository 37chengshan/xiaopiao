package com.xiaopiao.app.ui.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantNavTest {
    @Test fun ledgerHalfIsTodayRevenueAnalysis() {
        listOf(MerchantTab.Today, MerchantTab.Revenue, MerchantTab.Analysis).forEach {
            assertFalse(MerchantNav.isSocial(it, null))
            assertEquals(it.name.lowercase(), MerchantNav.selectedId(it, null))
        }
    }

    @Test fun mineOpensTheRightHalfAndFlowStepsLightCrowdOrLab() {
        assertTrue(MerchantNav.isSocial(MerchantTab.Mine, null))
        assertEquals("mine", MerchantNav.selectedId(MerchantTab.Mine, null))
        assertEquals("crowd", MerchantNav.selectedId(MerchantTab.Mine, MerchantSub.Crowd))
        listOf(MerchantSub.Setup, MerchantSub.Results, MerchantSub.Decide).forEach {
            assertEquals("lab", MerchantNav.selectedId(MerchantTab.Mine, it))
            assertTrue(MerchantNav.isSocial(MerchantTab.Analysis, it))
        }
    }

    @Test fun labOpensResultsOnlyOnceThereIsAnExperiment() {
        assertEquals(MerchantSub.Setup, MerchantNav.labStep(false))
        assertEquals(MerchantSub.Results, MerchantNav.labStep(true))
    }

    @Test fun barIdsMapBackToTabs() {
        assertEquals(MerchantTab.Revenue, MerchantNav.tabFor("revenue"))
        assertEquals(MerchantTab.Mine, MerchantNav.tabFor("mine"))
        assertNull(MerchantNav.tabFor("crowd"))
        assertNull(MerchantNav.tabFor("fold"))
    }
}
