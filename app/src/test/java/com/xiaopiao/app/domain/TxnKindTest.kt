package com.xiaopiao.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxnKindTest {
    private fun txn(merchant: String, direction: Direction = Direction.OUT, category: Category = Category.OTHER, note: String = "") =
        Txn(amountCents = 100, direction = direction, merchant = merchant, category = category,
            occurredAt = 0, source = Source.NOTIFICATION, note = note)

    @Test fun recognisesTransferAndRedPacketFromMerchantOrNote() {
        assertEquals(TxnKind.Transfer, TxnKinds.of(txn("转账给张三")))
        assertEquals(TxnKind.Transfer, TxnKinds.of(txn("张三", Direction.IN, note = "转账")))
        assertEquals(TxnKind.RedPacket, TxnKinds.of(txn("袁开浩的红包", Direction.IN)))
    }

    @Test fun categoryAloneNeverMakesARedPacket() {
        assertNull(TxnKinds.of(txn("朋友", Direction.IN, Category.SOCIAL)))
    }

    @Test fun ordinaryPaymentsAreNotKinds() {
        assertNull(TxnKinds.of(txn("瑞幸咖啡", category = Category.DRINK)))
        assertNull(TxnKinds.of(txn("工资", Direction.IN, Category.OTHER)))
    }

    @Test fun switchShowsForKindsAndForAnyIncomeOnly() {
        assertTrue(TxnKinds.showsCountSwitch(txn("转账", Direction.OUT)))
        assertTrue(TxnKinds.showsCountSwitch(txn("工资", Direction.IN)))
        assertFalse(TxnKinds.showsCountSwitch(txn("瑞幸咖啡")))
    }
}
