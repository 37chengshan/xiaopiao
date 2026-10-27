package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.insights.BadgeCatalog as C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeEngineTest {
    private val day = at(2026, 10, 21, 0) // a Wednesday
    private val old = oldTxn()

    private fun dayCodes(items: List<com.xiaopiao.app.domain.Txn>, history: List<com.xiaopiao.app.domain.Txn> = listOf(old)) =
        BadgeEngine.badgesForDay(day, items, history).map { it.code }

    private fun weekCodes(history: List<com.xiaopiao.app.domain.Txn>, now: Long = AFTER_WEEK) =
        BadgeEngine.badgesForWeek(WEEK, history + oldTxn(), now).map { it.code }

    @Test fun catalogHasAtLeastFourteenTitles() {
        assertTrue(C.all.size >= 14)
        assertEquals(C.all.size, C.all.map { it.code }.toSet().size)
    }

    @Test fun mondayIsIndexZero() = assertEquals(0, weekdayIndex(WEEK))

    // ---- day ----
    @Test fun dayFirstTxn() {
        val t = txn(at(2026, 10, 21, 9), 12.0, category = Category.MEAL)
        assertEquals(C.FIRST_TXN, BadgeEngine.stampForDay(day, listOf(t), listOf(t))?.code)
    }

    @Test fun dayMilkTea() {
        val items = listOf(
            txn(at(2026, 10, 21, 10), 15.0, category = Category.DRINK),
            txn(at(2026, 10, 21, 15), 18.0, category = Category.DRINK),
        )
        assertTrue(C.MILKTEA_WAR_GOD in dayCodes(items))
        assertFalse(C.MILKTEA_WAR_GOD in dayCodes(items.take(1)))
    }

    @Test fun dayNightForager() {
        assertTrue(C.NIGHT_FORAGER in dayCodes(listOf(txn(at(2026, 10, 21, 22, 30), 30.0, category = Category.MEAL))))
        assertFalse(C.NIGHT_FORAGER in dayCodes(listOf(txn(at(2026, 10, 21, 21, 59), 30.0, category = Category.MEAL))))
    }

    @Test fun dayRedPacket() =
        assertTrue(C.RED_PACKET_AMBASSADOR in dayCodes(listOf(txn(at(2026, 10, 21), 66.0, category = Category.SOCIAL))))

    @Test fun dayLearner() =
        assertTrue(C.LEARNING_INVESTOR in dayCodes(listOf(txn(at(2026, 10, 21), 99.0, category = Category.LEARNING))))

    @Test fun dayMetro() {
        val two = listOf(
            txn(at(2026, 10, 21, 8), 4.0, "地铁", Category.TRANSPORT),
            txn(at(2026, 10, 21, 18), 4.0, "地铁", Category.TRANSPORT),
        )
        assertTrue(C.METRO_COMMUTER in dayCodes(two))
        assertFalse(C.METRO_COMMUTER in dayCodes(two.take(1)))
    }

    @Test fun dayBreakfast() {
        assertTrue(C.BREAKFAST_GUARD in dayCodes(listOf(txn(at(2026, 10, 21, 8), 9.0, category = Category.MEAL))))
        assertFalse(C.BREAKFAST_GUARD in dayCodes(listOf(txn(at(2026, 10, 21, 12), 9.0, category = Category.MEAL))))
    }

    @Test fun dayWalletSleeper() {
        assertEquals(listOf(C.WALLET_SLEEPER), dayCodes(emptyList()))
        // income only is still a zero-spend day
        assertTrue(C.WALLET_SLEEPER in dayCodes(listOf(txn(at(2026, 10, 21), 50.0, direction = Direction.IN))))
        // no history at all: nothing to compare with, so no badge
        assertNull(BadgeEngine.stampForDay(day, emptyList(), emptyList()))
    }

    @Test fun dayPriorityPicksOneStamp() {
        val items = listOf(
            txn(at(2026, 10, 21, 8), 9.0, category = Category.MEAL),
            txn(at(2026, 10, 21, 10), 15.0, category = Category.DRINK),
            txn(at(2026, 10, 21, 15), 18.0, category = Category.DRINK),
        )
        val codes = dayCodes(items)
        assertEquals(listOf(C.MILKTEA_WAR_GOD, C.BREAKFAST_GUARD), codes)
        assertEquals(C.MILKTEA_WAR_GOD, BadgeEngine.stampForDay(day, items, listOf(old))?.code)
    }

    @Test fun reasonsAreNeverBlank() {
        val items = listOf(txn(at(2026, 10, 21, 23), 50.0, category = Category.SOCIAL))
        BadgeEngine.badgesForDay(day, items, listOf(old)).forEach { assertTrue(it.reason.isNotBlank()) }
    }

    // ---- week ----
    @Test fun weekMilkTea() {
        val drinks = (0..2).map { txn(at(2026, 10, 19 + it, 15), 16.0, category = Category.DRINK) }
        assertTrue(C.MILKTEA_WAR_GOD in weekCodes(drinks))
        assertFalse(C.MILKTEA_WAR_GOD in weekCodes(drinks.take(2)))
    }

    @Test fun weekMilkTeaNeedsToBeTheMostOfRecentWeeks() {
        val thisWeek = (0..2).map { txn(at(2026, 10, 19 + it, 15), 16.0, category = Category.DRINK) }
        val lastWeek = (0..5).map { txn(at(2026, 10, 12 + it, 15), 16.0, category = Category.DRINK) }
        assertFalse(C.MILKTEA_WAR_GOD in weekCodes(thisWeek + lastWeek))
    }

    @Test fun weekNoTakeout() {
        val home = (0..3).map { txn(at(2026, 10, 19 + it, 12), 25.0, "公司食堂", Category.MEAL) }
        assertTrue(C.NO_TAKEOUT in weekCodes(home))
        val withTakeout = home + txn(at(2026, 10, 23, 19), 40.0, "美团外卖", Category.MEAL)
        assertFalse(C.NO_TAKEOUT in weekCodes(withTakeout))
        // too little activity to say anything
        assertFalse(C.NO_TAKEOUT in weekCodes(home.take(1)))
    }

    @Test fun weekWalletSleeper() {
        val two = listOf(txn(at(2026, 10, 19, 12), 20.0), txn(at(2026, 10, 20, 12), 20.0))
        assertTrue(C.WALLET_SLEEPER in weekCodes(two)) // 5 empty days
        val busy = (0..6).map { txn(at(2026, 10, 19 + it, 12), 20.0) }
        assertFalse(C.WALLET_SLEEPER in weekCodes(busy))
    }

    @Test fun weekFrugalStar() {
        val prev = (1..3).map { n -> txn(at(2026, 10, 19 - 7 * n + 1, 12), 500.0) }
        assertTrue(C.FRUGAL_STAR in weekCodes(prev + txn(at(2026, 10, 20), 300.0)))
        assertFalse(C.FRUGAL_STAR in weekCodes(prev + txn(at(2026, 10, 20), 450.0)))
        // an unfinished week is never judged
        assertFalse(C.FRUGAL_STAR in weekCodes(prev + txn(at(2026, 10, 20), 100.0), now = at(2026, 10, 21, 20)))
    }

    @Test fun weekSubCleaner() {
        val subs = listOf(txn(at(2026, 8, 22, 1), 25.0, "爱奇艺会员", Category.SUBSCRIPTION), txn(at(2026, 9, 22, 1), 25.0, "爱奇艺会员", Category.SUBSCRIPTION))
        // expected again on 10-22 (inside the week) but nothing came
        assertTrue(C.SUB_CLEANER in weekCodes(subs))
        val renewed = subs + txn(at(2026, 10, 22, 1), 25.0, "爱奇艺会员", Category.SUBSCRIPTION)
        assertFalse(C.SUB_CLEANER in weekCodes(renewed))
    }

    @Test fun weekNightForager() {
        val late = listOf(txn(at(2026, 10, 19, 23), 30.0), txn(at(2026, 10, 21, 22, 45), 30.0))
        assertTrue(C.NIGHT_FORAGER in weekCodes(late))
        assertFalse(C.NIGHT_FORAGER in weekCodes(late.take(1)))
    }

    @Test fun weekWeekendWild() {
        val weekdays = (19..23).map { txn(at(2026, 10, it, 12), 30.0) }
        assertTrue(C.WEEKEND_WILD in weekCodes(weekdays + txn(at(2026, 10, 24, 15), 300.0)))
        assertFalse(C.WEEKEND_WILD in weekCodes(weekdays + txn(at(2026, 10, 24, 15), 40.0)))
    }

    @Test fun weekSteadyDog() {
        val steady = (19..25).map { txn(at(2026, 10, it, 12), 50.0 + (it % 3)) }
        assertTrue(C.STEADY_DOG in weekCodes(steady))
        val wild = (19..25).map { txn(at(2026, 10, it, 12), if (it % 2 == 0) 10.0 else 190.0) }
        assertFalse(C.STEADY_DOG in weekCodes(wild))
    }

    @Test fun weekSmallFeeSlayer() {
        val big = (19..23).map { txn(at(2026, 10, it, 12), 30.0) }
        assertTrue(C.SMALL_FEE_SLAYER in weekCodes(big))
        val small = (19..23).map { txn(at(2026, 10, it, 12), 8.0) }
        assertFalse(C.SMALL_FEE_SLAYER in weekCodes(small))
    }

    @Test fun weekRedPacket() {
        val two = listOf(txn(at(2026, 10, 20), 88.0, category = Category.SOCIAL), txn(at(2026, 10, 22), 66.0, category = Category.SOCIAL))
        assertTrue(C.RED_PACKET_AMBASSADOR in weekCodes(two))
        assertFalse(C.RED_PACKET_AMBASSADOR in weekCodes(two.take(1)))
    }

    @Test fun weekLearner() =
        assertTrue(C.LEARNING_INVESTOR in weekCodes(listOf(txn(at(2026, 10, 22), 199.0, category = Category.LEARNING))))

    @Test fun weekBreakfast() {
        val three = (19..21).map { txn(at(2026, 10, it, 8), 9.0, category = Category.MEAL) }
        assertTrue(C.BREAKFAST_GUARD in weekCodes(three))
        assertFalse(C.BREAKFAST_GUARD in weekCodes(three.take(2)))
    }

    @Test fun weekMetro() {
        val four = (19..22).map { txn(at(2026, 10, it, 8), 4.0, "地铁", Category.TRANSPORT) }
        assertTrue(C.METRO_COMMUTER in weekCodes(four))
        assertFalse(C.METRO_COMMUTER in weekCodes(four.take(3)))
    }

    @Test fun weekFirstTxn() {
        val t = txn(at(2026, 10, 21), 12.0)
        assertTrue(C.FIRST_TXN in BadgeEngine.badgesForWeek(WEEK, listOf(t), AFTER_WEEK).map { it.code })
    }

    @Test fun futureWeekHasNoBadges() =
        assertTrue(BadgeEngine.badgesForWeek(WEEK, listOf(oldTxn()), at(2026, 10, 1)).isEmpty())

    @Test fun deterministic() {
        val items = (19..23).map { txn(at(2026, 10, it, 12), 30.0) }
        assertEquals(weekCodes(items), weekCodes(items))
    }
}
