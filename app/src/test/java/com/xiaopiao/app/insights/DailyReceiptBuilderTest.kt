package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyReceiptBuilderTest {
    private val day = at(2026, 10, 21, 0)

    @Test fun totalsOutgoingOnlyAndSortsItems() {
        val a = txn(at(2026, 10, 21, 18), 30.0, category = Category.MEAL)
        val b = txn(at(2026, 10, 21, 9), 12.0, category = Category.DRINK)
        val income = txn(at(2026, 10, 21, 10), 500.0, direction = Direction.IN)
        val r = DailyReceiptBuilder.build(day, listOf(a, income, b), listOf(oldTxn(), a, b, income))
        assertEquals(4200L, r.totalOutCents)
        assertEquals(listOf(b, income, a), r.items)
        assertEquals(day, r.dayStart)
    }

    @Test fun stampsTheHighestPriorityBadge() {
        val items = listOf(
            txn(at(2026, 10, 21, 10), 15.0, category = Category.DRINK),
            txn(at(2026, 10, 21, 15), 18.0, category = Category.DRINK),
        )
        val r = DailyReceiptBuilder.build(day, items, listOf(oldTxn()) + items)
        assertEquals(BadgeCatalog.MILKTEA_WAR_GOD, r.badge?.code)
        assertTrue(r.aiComment.contains("奶茶战神") || r.aiComment.isNotBlank())
    }

    @Test fun emptyDayWithHistoryGetsSleeperStamp() {
        val r = DailyReceiptBuilder.build(day, emptyList(), listOf(oldTxn()))
        assertEquals(0L, r.totalOutCents)
        assertEquals(BadgeCatalog.WALLET_SLEEPER, r.badge?.code)
        assertTrue(r.aiComment.isNotBlank())
    }

    @Test fun brandNewUserEmptyDayHasNoBadgeButAComment() {
        val r = DailyReceiptBuilder.build(day, emptyList(), emptyList())
        assertNull(r.badge)
        assertNotNull(r.aiComment)
        assertTrue(r.aiComment.isNotBlank())
    }

    @Test fun reprintIsStable() {
        val items = listOf(txn(at(2026, 10, 21, 12), 35.0, category = Category.MEAL))
        val h = listOf(oldTxn()) + items
        assertEquals(DailyReceiptBuilder.build(day, items, h), DailyReceiptBuilder.build(day, items, h))
    }
}
