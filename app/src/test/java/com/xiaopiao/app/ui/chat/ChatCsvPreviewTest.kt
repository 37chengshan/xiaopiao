package com.xiaopiao.app.ui.chat

import com.xiaopiao.app.importer.BillFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CSV attachments are parsed but not written until the user confirms, so the preview numbers must be
 * right on their own (rows, spend total, skipped rows) without touching a repository.
 */
class ChatCsvPreviewTest {

    private val wechat = """
        微信支付账单明细,,,,,,,,,
        交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号
        2026-10-28 12:00:00,商户消费,瑞幸咖啡,拿铁,支出,¥18.50,零钱,支付成功,1001
        2026-10-28 13:00:00,商户消费,美团,午餐,支出,32.00,零钱,支付成功,1002
        2026-10-28 14:00:00,转账,老王,/,收入,120.00,零钱,已收款,1003
        2026-10-28 15:00:00,商户消费,某店,饮料,支出,8.00,零钱,已退款,1004
        2026-10-28 16:00:00,零钱提现,/,/,不计收支,50.00,零钱,已提现,1005
    """.trimIndent()

    private val alipay = """
        支付宝交易记录明细查询,,,,,
        交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号
        2026-10-27 09:30:00,餐饮美食,肯德基,/,早餐,支出,12.00,余额,交易成功,2001
        2026-10-27 10:30:00,交通出行,滴滴,/,打车,支出,25.50,余额,交易成功,2002
    """.trimIndent()

    @Test
    fun wechatCsvPreviewCountsRowsAndSpendOnly() {
        val preview = ChatCsvPreview.from("微信账单.csv", wechat)
        assertNotNull(preview)
        preview!!
        assertEquals(BillFormat.WECHAT, preview.format)
        // The importer keeps income too (two 支出 + one 收入); the refund and the 不计收支 withdrawal are skipped.
        assertEquals(3, preview.rows)
        assertEquals(5_050L, preview.totalCents)
        assertEquals(2, preview.skipped)
        assertEquals(0, preview.failed)
        assertEquals("微信账单.csv", preview.label)
        assertEquals(wechat, preview.rawText)
    }

    @Test
    fun alipayCsvPreviewUsesTheSameRules() {
        val preview = ChatCsvPreview.from("支付宝账单.csv", alipay)!!
        assertEquals(BillFormat.ALIPAY, preview.format)
        assertEquals(2, preview.rows)
        assertEquals(3_750L, preview.totalCents)
    }

    @Test
    fun unknownTextIsRejectedInsteadOfImportedEmpty() {
        assertNull(ChatCsvPreview.from("notes.csv", "a,b,c\n1,2,3"))
        assertNull(ChatCsvPreview.from("empty.csv", ""))
        // A header with no data rows is not a preview either.
        assertNull(
            ChatCsvPreview.from(
                "empty.csv",
                "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号",
            ),
        )
    }

    @Test
    fun repeatedImportsOfTheSameFileCarryTheSameRawText() {
        val a = ChatCsvPreview.from("a.csv", wechat)!!
        val b = ChatCsvPreview.from("a.csv", wechat)!!
        assertEquals(a.rawText, b.rawText)
        assertEquals(a.totalCents, b.totalCents)
    }
}
