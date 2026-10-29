package com.xiaopiao.app.wallet

import com.xiaopiao.app.capture.CardTail
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CardTailTest {
    @Test fun readsTheTailFromNotificationPhrases() {
        assertEquals("1234", CardTail.extract("招商银行信用卡尾号1234消费18.00元"))
        assertEquals("5678", CardTail.extract("您尾号5678的储蓄卡支出100.00元"))
        assertEquals("9012", CardTail.extract("工商银行(9012) 支付宝-瑞幸"))
        assertEquals("3456", CardTail.extract("卡号后四位：3456 消费 22.50"))
        assertEquals("7890", CardTail.extract("**** **** **** 7890 支付成功"))
    }

    @Test fun ignoresTextWithoutACardTail() {
        assertNull(CardTail.extract("你有一笔 18.00 元的支出"))
        assertNull(CardTail.extract(""))
    }
}

class CardLedgerTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long = Calendar.getInstance().apply {
        clear(); set(year, month - 1, day, hour, 0, 0)
    }.timeInMillis

    private val now = at(2026, 10, 28, 15)
    private val cardA = BankCard(id = "a", bankName = "中国工商银行", last4 = "1234")
    private val cardB = BankCard(id = "b", bankName = "招商银行", last4 = "5678")

    private fun txn(cents: Long, at: Long, cardLast4: String = "", note: String = "", merchant: String = "瑞幸") = Txn(
        amountCents = cents, direction = Direction.OUT, merchant = merchant, category = Category.MEAL,
        occurredAt = at, source = Source.NOTIFICATION, note = note, cardLast4 = cardLast4,
    )

    private val txns = listOf(
        txn(3000, at(2026, 10, 27), cardLast4 = "1234"),
        txn(1500, at(2026, 10, 3), note = "尾号1234"),
        txn(2000, at(2026, 10, 5), cardLast4 = "5678"),
        txn(9999, at(2026, 9, 30), cardLast4 = "1234"),
        txn(3500, at(2026, 10, 6), merchant = "星巴克(国贸店)"),
    )

    @Test fun structuredTailWins() {
        assertTrue(CardLedger.belongsTo(txn(100, now, cardLast4 = "1234"), cardA))
        assertFalse(CardLedger.belongsTo(txn(100, now, cardLast4 = "5678"), cardA))
    }

    @Test fun noteTailIsTheFallback() {
        assertTrue(CardLedger.belongsTo(txn(100, now, note = "尾号1234 线下"), cardA))
        assertFalse(CardLedger.belongsTo(txn(100, now, note = "尾号5678 线下"), cardA))
    }

    @Test fun monthSpendOnlyCountsThisMonthAndThisCard() {
        val spend = CardLedger.monthSpend(txns, now, cardA)
        assertEquals(4500L, spend.cents)
        assertEquals(2, spend.count)
        assertEquals(2000L, CardLedger.monthSpend(txns, now, cardB).cents)
    }

    @Test fun walletTotalCountsEveryKnownTailOnce() {
        val total = CardLedger.monthCardSpend(txns, now, listOf(cardA, cardB))
        assertEquals(6500L, total.cents)
        assertEquals(3, total.count)
    }

    @Test fun memberSpendMatchesTheStoreName() {
        assertEquals(3500L, CardLedger.monthSpendAt(txns, now, "星巴克").cents)
        assertEquals(1, CardLedger.monthSpendAt(txns, now, "星巴克").count)
        assertEquals(0L, CardLedger.monthSpendAt(txns, now, "麦当劳").cents)
    }

    @Test fun recentIsNewestFirstAndCapped() {
        val recent = CardLedger.recent(txns + txn(100, at(2026, 10, 20), cardLast4 = "1234"), cardA, limit = 2)
        assertEquals(2, recent.size)
        assertTrue(recent[0].occurredAt >= recent[1].occurredAt)
    }
}
