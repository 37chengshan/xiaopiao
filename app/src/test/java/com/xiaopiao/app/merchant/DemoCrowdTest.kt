package com.xiaopiao.app.merchant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCrowdTest {
    @Test fun sameSeedGivesIdenticalCrowd() {
        assertEquals(DemoCrowd.generate(), DemoCrowd.generate())
    }

    @Test fun differentSeedGivesDifferentCrowd() {
        assertNotEquals(DemoCrowd.generate(1L), DemoCrowd.generate(2L))
    }

    @Test fun defaultSizeAndFieldRanges() {
        val crowd = DemoCrowd.generate()
        assertEquals(300, crowd.size)
        assertTrue(crowd.all { it.purchases.isNotEmpty() })
        assertTrue(crowd.flatMap { it.purchases }.all { it.dayOfWeek in 0..6 && it.hour in 8..23 && it.priceCents >= 600 })
        assertTrue(crowd.all { it.toppingLean in 0.0..1.0 && it.loyaltyLean in 0.0..1.0 })
    }
}
