package com.xiaopiao.app.domain.ai

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Eight real-looking OCR blobs (WeChat payment, WeChat transfer, Alipay payment, Alipay bill detail,
 * supermarket receipt, convenience-store receipt, a delivery row, and a receipt-less page) plus the
 * failure modes we must not guess through.
 */
class ReceiptParserTest {

    private val zone = TimeZone.getTimeZone("Asia/Shanghai")

    private fun parse(text: String) = ReceiptParser.parse(text, now = 0L, zone = zone)

    @Test
    fun wechatPaymentSuccess() {
        val text = """
            微信支付
            支付成功
            商户全称
            山姆会员店（深圳）
            付款金额
            ¥186.40
            支付时间
            2026-10-28 19:14:03
        """.trimIndent()
        val one = parse(text).single()
        assertEquals("山姆会员店", one.merchant)
        assertEquals(18_640L, one.amountCents)
        assertNotNull(one.occurredAt)
    }

    @Test
    fun wechatTransferIn() {
        val text = """
            微信支付
            转账
            转账-来自老王
            ￥120.00
            转账时间 2026-10-27 12:02
        """.trimIndent()
        val one = parse(text).single()
        assertEquals("老王", one.merchant)
        assertEquals(12_000L, one.amountCents)
    }

    @Test
    fun alipayPaymentWithLabel() {
        val text = """
            支付宝
            付款成功
            付款金额 18.50
            收款方全称 瑞幸咖啡
            创建时间 2026-10-28 08:12:44
        """.trimIndent()
        val one = parse(text).single()
        assertEquals("瑞幸咖啡", one.merchant)
        assertEquals(1_850L, one.amountCents)
    }

    @Test
    fun alipayBillDetailMinusAmount() {
        val text = """
            账单详情
            交易成功
            -32.80
            商户名称：美团外卖
            交易时间 2026-10-26 19:41
        """.trimIndent()
        val one = parse(text).single()
        assertEquals("美团外卖", one.merchant)
        assertEquals(3_280L, one.amountCents)
    }

    @Test
    fun supermarketReceiptTotal() {
        val text = """
            永辉超市
            2026-10-25 18:33
            纯牛奶 12.90
            鸡蛋 15.80
            合计 ¥28.70
        """.trimIndent()
        val one = parse(text).single()
        assertEquals("永辉超市", one.merchant)
        assertEquals(2_870L, one.amountCents)
        assertNotNull(one.occurredAt)
    }

    @Test
    fun convenienceStorePayable() {
        val text = """
            便利蜂
            应付金额：9.90
            2026/10/24 07:05
        """.trimIndent()
        val one = parse(text).single()
        assertEquals("便利蜂", one.merchant)
        assertEquals(990L, one.amountCents)
    }

    @Test
    fun deliveryRowUsesCounterparty() {
        val text = """
            支付宝
            交易对方：滴滴出行
            金额 8.50
            2026-10-23 22:10
        """.trimIndent()
        val one = parse(text).single()
        assertEquals("滴滴出行", one.merchant)
        assertEquals(850L, one.amountCents)
    }

    @Test
    fun fullWidthDigitsAreFolded() {
        val text = """
            微信支付
            支付成功
            商户全称：楼下小卖部
            付款金额 ￥２３．５０
        """.trimIndent()
        val one = parse(text).single()
        assertEquals("楼下小卖部", one.merchant)
        assertEquals(2_350L, one.amountCents)
    }

    @Test
    fun twoPaymentsInOneLongScreenshotBecomeTwoCandidates() {
        val text = """
            微信支付
            支付成功
            ¥12.00
            商户全称 包子铺
            2026-10-20 10:00
            订单号 123456
            支付成功
            ¥30.00
            商户全称 面馆
            2026-10-21 11:00
        """.trimIndent()
        val found = parse(text)
        assertEquals(2, found.size)
        assertEquals(listOf(1_200L, 3_000L), found.map { it.amountCents })
        assertEquals(listOf("包子铺", "面馆"), found.map { it.merchant })
    }

    @Test
    fun textWithoutAnAmountYieldsNothing() {
        assertTrue(parse("订单已送达\n感谢使用").isEmpty())
        assertTrue(parse("").isEmpty())
        assertTrue(parse("   ").isEmpty())
    }

    @Test
    fun refundedAndClosedPaymentsAreIgnored() {
        assertTrue(parse("微信支付\n已退款\n¥18.50\n商户名称：某店").isEmpty())
        assertTrue(parse("支付宝\n交易关闭\n-18.50\n商户名称：某店").isEmpty())
    }

    @Test
    fun centsAreBuiltWithoutFloatingPoint() {
        assertEquals(1_850L, ReceiptParser.toCents("18.50"))
        assertEquals(1_800L, ReceiptParser.toCents("18"))
        assertEquals(1_850L, ReceiptParser.toCents("18.5"))
        assertEquals(5L, ReceiptParser.toCents("0.05"))
        assertEquals(-1_850L, ReceiptParser.toCents("-18.50"))
        assertNull(ReceiptParser.toCents(""))
    }
}
