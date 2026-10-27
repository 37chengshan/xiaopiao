package com.xiaopiao.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DedupeKeyTest {
    private val t = 1_700_000_000_000L

    @Test fun sameMinuteSameKey() {
        val a = DedupeKeys.notification(PaymentApps.ALIPAY, "OUT", 1500, t, "瑞幸")
        assertEquals(a, DedupeKeys.notification(PaymentApps.ALIPAY, "OUT", 1500, t + 20_000 - (t % 60_000), "瑞幸"))
    }

    @Test fun differentMinuteOrAmountOrMerchantDiffers() {
        val a = DedupeKeys.notification(PaymentApps.ALIPAY, "OUT", 1500, t, "瑞幸")
        assertNotEquals(a, DedupeKeys.notification(PaymentApps.ALIPAY, "OUT", 1500, t + 120_000, "瑞幸"))
        assertNotEquals(a, DedupeKeys.notification(PaymentApps.ALIPAY, "OUT", 1600, t, "瑞幸"))
        assertNotEquals(a, DedupeKeys.notification(PaymentApps.ALIPAY, "OUT", 1500, t, "喜茶"))
        assertNotEquals(a, DedupeKeys.notification(PaymentApps.WECHAT, "OUT", 1500, t, "瑞幸"))
    }

    @Test fun amounts() {
        assertEquals(123450L, Amounts.toCents("¥1,234.5"))
        assertEquals(1500L, Amounts.toCents("15"))
        assertEquals(null, Amounts.toCents("abc"))
    }
}
