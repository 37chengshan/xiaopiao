package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionDetectorTest {
    private val now = at(2026, 10, 27, 10)

    private fun monthly(merchant: String, yuan: Double, vararg months: Int, day: Int = 5, cat: Category = Category.SUBSCRIPTION) =
        months.map { txn(at(2026, it, day, 1), yuan, merchant, cat) }

    @Test fun detectsMonthlyWithNextChargeAndAnnualCost() {
        val subs = SubscriptionDetector.detect(monthly("爱奇艺会员", 25.0, 7, 8, 9, 10), now)
        assertEquals(1, subs.size)
        val s = subs.single()
        assertEquals(30, s.cycleDays)
        assertEquals(4, s.occurrences)
        assertEquals(2500L, s.amountCents)
        assertEquals(at(2026, 11, 5, 1), s.nextChargeAt)
        assertEquals(30000L, s.annualCents())
    }

    @Test fun detectsYearly() {
        val h = listOf(
            txn(at(2024, 11, 20, 9), 168.0, "Apple 年度订阅", Category.SUBSCRIPTION),
            txn(at(2025, 11, 20, 9), 168.0, "Apple 年度订阅", Category.SUBSCRIPTION),
        )
        val s = SubscriptionDetector.detect(h, now).single()
        assertEquals(365, s.cycleDays)
        assertEquals(at(2026, 11, 20, 9), s.nextChargeAt)
        assertEquals(16800L, s.annualCents())
    }

    @Test fun detectsQuarterly() {
        val h = listOf(3, 6, 9).map { txn(at(2026, it, 15, 8), 90.0, "云盘会员", Category.SUBSCRIPTION) }
        val s = SubscriptionDetector.detect(h, now).single()
        assertEquals(91, s.cycleDays)
        assertEquals(at(2026, 12, 15, 8), s.nextChargeAt)
        assertEquals(36000L, s.annualCents())
    }

    @Test fun detectsWeeklyOnlyWithThreeCharges() {
        val w = (0..2).map { txn(at(2026, 10, 6 + 7 * it, 8), 10.0, "周卡会员", Category.SUBSCRIPTION) }
        assertEquals(7, SubscriptionDetector.detect(w, now).single().cycleDays)
        assertTrue(SubscriptionDetector.detect(w.take(2), now).isEmpty())
    }

    @Test fun toleratesNoisyAmounts() {
        val h = listOf(
            txn(at(2026, 8, 5, 1), 25.0, "爱奇艺会员", Category.SUBSCRIPTION),
            txn(at(2026, 9, 5, 1), 25.8, "爱奇艺会员", Category.SUBSCRIPTION),
            txn(at(2026, 10, 5, 1), 24.3, "爱奇艺会员", Category.SUBSCRIPTION),
        )
        val s = SubscriptionDetector.detect(h, now).single()
        assertEquals(3, s.occurrences)
        assertTrue(SubscriptionDetector.analyze(h, now).single().flags.isEmpty())
    }

    @Test fun toleratesIrregularDayOfMonth() {
        val h = listOf(
            txn(at(2026, 8, 30, 1), 15.0, "音乐会员", Category.SUBSCRIPTION),
            txn(at(2026, 9, 28, 1), 15.0, "音乐会员", Category.SUBSCRIPTION),
            txn(at(2026, 10, 29, 1), 15.0, "音乐会员", Category.SUBSCRIPTION),
        )
        assertEquals(3, SubscriptionDetector.detect(h, at(2026, 10, 30)).single().occurrences)
    }

    @Test fun flagsPriceIncrease() {
        val h = monthly("网易云音乐会员", 15.0, 8, 9) + txn(at(2026, 10, 5, 1), 18.0, "网易云音乐会员", Category.SUBSCRIPTION)
        val insight = SubscriptionDetector.analyze(h, now).single()
        assertTrue(SubscriptionFlag.PRICE_UP in insight.flags)
    }

    @Test fun flagsDuplicateKinds() {
        val h = monthly("爱奇艺会员", 25.0, 8, 9, 10) + monthly("腾讯视频会员", 25.0, 8, 9, 10, day = 9) + monthly("网易云音乐会员", 15.0, 8, 9, 10, day = 12)
        val flags = SubscriptionDetector.analyze(h, now).associate { it.subscription.merchant to it.flags }
        assertTrue(SubscriptionFlag.DUPLICATE_KIND in flags.getValue("爱奇艺会员"))
        assertTrue(SubscriptionFlag.DUPLICATE_KIND in flags.getValue("腾讯视频会员"))
        assertTrue(SubscriptionFlag.DUPLICATE_KIND !in flags.getValue("网易云音乐会员"))
    }

    @Test fun sortedByNextCharge() {
        val h = monthly("A会员", 10.0, 8, 9, 10, day = 25) + monthly("B会员", 10.0, 8, 9, 10, day = 3)
        assertEquals(listOf("B会员", "A会员"), SubscriptionDetector.detect(h, now).map { it.merchant })
    }

    @Test fun ignoresDailyHabitsWithVaryingAmounts() {
        val coffee = (1..20).map { txn(at(2026, 10, it, 9), 12.0 + it % 5, "瑞幸咖啡", Category.DRINK) }
        assertTrue(SubscriptionDetector.detect(coffee, now).isEmpty())
    }

    @Test fun ignoresWeeklyMealsEvenWithEqualAmounts() {
        val meals = (0..3).map { txn(at(2026, 10, 2 + 7 * it, 19), 68.0, "公司楼下小馆", Category.MEAL) }
        assertTrue(SubscriptionDetector.detect(meals, now).isEmpty())
    }

    @Test fun ignoresMonthlyHabitWithoutHint() {
        val shop = listOf(8, 9, 10).map { txn(at(2026, it, 5), 100.0, "某超市", Category.GROCERY) }
        assertTrue(SubscriptionDetector.detect(shop, now).isEmpty())
    }

    @Test fun ignoresChangingAmountsAndGaps() {
        val jumpy = listOf(
            txn(at(2026, 8, 5), 25.0, "某会员", Category.SUBSCRIPTION),
            txn(at(2026, 9, 5), 60.0, "某会员", Category.SUBSCRIPTION),
            txn(at(2026, 10, 5), 140.0, "某会员", Category.SUBSCRIPTION),
        )
        assertTrue(SubscriptionDetector.detect(jumpy, now).isEmpty())
        val sparse = listOf(txn(at(2026, 5, 5), 25.0, "某会员", Category.SUBSCRIPTION), txn(at(2026, 7, 5), 25.0, "某会员", Category.SUBSCRIPTION))
        assertTrue(SubscriptionDetector.detect(sparse, now).isEmpty())
    }

    @Test fun singleChargeIsNotASubscription() =
        assertTrue(SubscriptionDetector.detect(listOf(txn(at(2026, 10, 5), 25.0, "某会员", Category.SUBSCRIPTION)), now).isEmpty())

    @Test fun lapsedSubscriptionsAreHiddenFromActiveList() {
        val h = monthly("健身会员", 99.0, 3, 4, 5, cat = Category.HEALTH).map { it.copy(merchant = "健身会员") }
        assertTrue(SubscriptionDetector.detect(h, now).isEmpty())
        assertTrue(SubscriptionDetector.analyzeAll(h, now).single().lapsed)
    }

    @Test fun ignoresIncomeAndSameDayRetries() {
        val retry = listOf(
            txn(at(2026, 10, 5, 1), 25.0, "爱奇艺会员", Category.SUBSCRIPTION),
            txn(at(2026, 10, 5, 2), 25.0, "爱奇艺会员", Category.SUBSCRIPTION),
        )
        assertTrue(SubscriptionDetector.detect(retry, now).isEmpty())
    }

    @Test fun guidesAreProvidedAndAppleFirstForAppleMerchants() {
        assertEquals("Apple", CancelGuides.forMerchant("iCloud+ 存储").first().channel)
        assertEquals("支付宝", CancelGuides.forMerchant("爱奇艺会员").first().channel)
        assertEquals(3, CancelGuides.forMerchant("x").size)
    }
}
