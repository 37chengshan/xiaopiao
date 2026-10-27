package com.xiaopiao.app.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregatorTest {
    private val crowd = DemoCrowd.generate()

    @Test fun cellBelowKIsSuppressedAndHidesCount() {
        val c = Aggregator.cell("x", 19, 300)
        assertTrue(c.suppressed)
        assertNull(c.people)
        assertNull(c.share)
    }

    @Test fun cellAtKIsShown() {
        val c = Aggregator.cell("x", 20, 200)
        assertFalse(c.suppressed)
        assertEquals(20, c.people)
        assertEquals(0.1f, c.share!!, 1e-6f)
    }

    @Test fun cohortSmallerThanKSuppressesEverything() {
        val tiny = crowd.take(19)
        val stats = Aggregator.compute(tiny)
        assertNull(stats.avgPriceCents)
        assertNull(stats.repeatRate)
        val all = stats.kindShare + stats.heatmap.flatten() + stats.priceBands + stats.toppings
        assertTrue(all.all { it.suppressed })
    }

    @Test fun fullCrowdHasDimensionsAndSomeSuppressedCells() {
        val stats = Aggregator.compute(crowd)
        assertEquals(300, stats.totalPeople)
        assertEquals(7, stats.heatmap.size)
        assertTrue(stats.heatmap.all { it.size == DayPart.entries.size })
        assertEquals(5, stats.priceBands.size)
        assertTrue("thin slots should be suppressed", stats.heatmap.flatten().any { it.suppressed })
        assertTrue("busy slots should be shown", stats.heatmap.flatten().any { !it.suppressed })
    }

    @Test fun noVisibleCellIsBelowK() {
        val stats = Aggregator.compute(crowd)
        val all = stats.kindShare + stats.heatmap.flatten() + stats.priceBands + stats.toppings
        assertTrue(all.filter { !it.suppressed }.all { it.people!! >= K_ANONYMITY })
    }

    @Test fun ageFilterNarrowsCohort() {
        val stats = Aggregator.compute(crowd, AgeBand.A45_PLUS)
        assertEquals(crowd.count { it.ageBand == AgeBand.A45_PLUS }, stats.totalPeople)
    }

    @Test fun priceBandBoundaries() {
        assertEquals(0, Aggregator.priceBand(999))
        assertEquals(1, Aggregator.priceBand(1000))
        assertEquals(4, Aggregator.priceBand(2500))
    }

    @Test fun repeatRateIsAShare() {
        val r = Aggregator.compute(crowd).repeatRate!!
        assertTrue(r in 0f..1f)
    }
}
