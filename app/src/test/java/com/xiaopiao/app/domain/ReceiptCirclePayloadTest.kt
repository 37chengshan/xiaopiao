package com.xiaopiao.app.domain

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class ReceiptCirclePayloadTest {
    private fun receipt() = DailyReceipt(1_600_000_000_000L, listOf(
        Txn(1, 100, merchant = "PRIVATE_MERCHANT", category = Category.MEAL, occurredAt = 1_600_000_000_000L, source = Source.MANUAL),
        Txn(2, 200, merchant = "PRIVATE_STORE", category = Category.DRINK, occurredAt = 1_600_000_000_001L, source = Source.MANUAL)), 300, "private commentary", null)
    @Test fun receiptSharingPreservesThePrintedSnapshotWithoutMerchantDetails() {
        val first = ReceiptCirclePayload.build(receipt(), "caption", false)
        val second = ReceiptCirclePayload.build(receipt(), "caption", false)
        assertEquals(first, second)
        val card = first.getValue("receipt").jsonObject
        assertEquals(300L, card.getValue("totalCents").jsonPrimitive.long)
        assertEquals(2, card.getValue("count").jsonPrimitive.int)
        assertFalse(card.containsKey("lines")); assertFalse(first.toString().contains("PRIVATE")); assertFalse(first.toString().contains("private commentary"))
    }
    @Test fun optInOnlyAddsCategoryTotalsAndKeepsRawFieldsOut() {
        val card = ReceiptCirclePayload.build(receipt(), "", true).getValue("receipt").jsonObject
        assertEquals(300L, card.getValue("lines").jsonArray.sumOf { it.jsonObject.getValue("amountCents").jsonPrimitive.long })
        assertFalse(card.toString().contains("merchant"))
    }
}
