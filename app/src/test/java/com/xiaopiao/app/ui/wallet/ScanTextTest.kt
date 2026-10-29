package com.xiaopiao.app.ui.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** OCR 结果里挑卡号：只认 15–19 位、允许四位一组带空格的那一段。 */
class ScanTextTest {
    @Test fun readsAGroupedCardNumber() {
        assertEquals("6222021234567890", longestDigits("招商银行 6222 0212 3456 7890 08/29"))
    }

    @Test fun ignoresAmountsAndDates() {
        assertNull(longestDigits("消费 18.00 元 10/28 12:30"))
        assertNull(longestDigits("尾号 1234"))
    }

    @Test fun prefersTheLongestRun() {
        assertEquals("4111111111111111", longestDigits("1234 5678 4111 1111 1111 1111"))
    }
}
