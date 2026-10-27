package com.xiaopiao.app.data

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.startOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSeedTest {
    private val now = 1_761_550_000_000L // fixed instant
    private val txns = DemoSeed.generate(now)

    @Test fun enoughRealisticData() {
        assertTrue(txns.size > 50)
        assertTrue(txns.all { it.note == DemoSeed.NOTE && it.occurredAt <= now && it.direction == Direction.OUT })
        assertEquals(txns.size, txns.map { it.dedupeKey }.toSet().size)
    }

    @Test fun hasMonthlySubscriptions() {
        for (name in listOf("爱奇艺", "网易云音乐", "iCloud")) {
            val hits = txns.filter { it.merchant == name }
            assertEquals(3, hits.size)
            assertTrue(hits.all { it.category == Category.SUBSCRIPTION })
        }
        assertEquals(2500L, txns.first { it.merchant == "爱奇艺" }.amountCents)
    }

    @Test fun hasZeroSpendDaysAndManyDrinks() {
        val days = txns.map { startOfDay(it.occurredAt) }.toSet()
        val windowDays = (-21..0).map { startOfDay(now + it * DAY_MS) }
        assertTrue(windowDays.count { it !in days } >= 2)
        assertTrue(txns.count { it.category == Category.DRINK } >= 8)
    }

    @Test fun deterministic() { assertEquals(txns, DemoSeed.generate(now)) }
}
