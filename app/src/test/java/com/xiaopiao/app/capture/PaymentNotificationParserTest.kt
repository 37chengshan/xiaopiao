package com.xiaopiao.app.capture

import com.xiaopiao.app.domain.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentNotificationParserTest {
    private fun ali(title: String, text: String) = PaymentNotificationParser.parse(PaymentApps.ALIPAY, title, text)
    private fun wx(title: String, text: String) = PaymentNotificationParser.parse(PaymentApps.WECHAT, title, text)

    private fun ParseResult.payment(): ParsedPayment = (this as ParseResult.Parsed).payment

    @Test fun alipay_count_expense() {
        val p = ali("支付宝通知", "你有一笔15.00元的支出，点击查看详情").payment()
        assertEquals(1500L, p.amountCents); assertEquals(Direction.OUT, p.direction); assertNull(p.merchant)
    }

    @Test fun alipay_count_income() {
        val p = ali("支付宝通知", "你有一笔200.50元的收入").payment()
        assertEquals(20050L, p.amountCents); assertEquals(Direction.IN, p.direction)
    }

    @Test fun alipay_success_paid() {
        val p = ali("支付宝支付", "成功付款15.00元").payment()
        assertEquals(1500L, p.amountCents); assertEquals(Direction.OUT, p.direction)
    }

    @Test fun alipay_pay_success_with_merchant() {
        val p = ali("付款成功", "付款成功 ¥15.00 瑞幸咖啡").payment()
        assertEquals(1500L, p.amountCents); assertEquals("瑞幸咖啡", p.merchant)
    }

    @Test fun alipay_pay_success_merchant_first() {
        val p = ali("支付宝", "付款成功 喜茶 ¥28.00").payment()
        assertEquals(2800L, p.amountCents); assertEquals("喜茶", p.merchant)
    }

    @Test fun alipay_pay_to_merchant() {
        val p = ali("支付宝", "你已向蜜雪冰城付款￥9.90").payment()
        assertEquals(990L, p.amountCents); assertEquals("蜜雪冰城", p.merchant)
    }

    @Test fun alipay_thousands_separator() {
        val p = ali("支付宝通知", "你有一笔1,234.56元的支出").payment()
        assertEquals(123456L, p.amountCents)
    }

    @Test fun alipay_receive_money() {
        val p = ali("支付宝", "成功收款¥88.00").payment()
        assertEquals(8800L, p.amountCents); assertEquals(Direction.IN, p.direction)
    }

    @Test fun alipay_transfer_from_friend() {
        val p = ali("支付宝", "张三向你转账100元").payment()
        assertEquals(10000L, p.amountCents); assertEquals(Direction.IN, p.direction); assertEquals("张三", p.merchant)
    }

    @Test fun wechat_paid() {
        val p = wx("微信支付", "微信支付: 已支付¥15.00").payment()
        assertEquals(1500L, p.amountCents); assertEquals(Direction.OUT, p.direction)
    }

    @Test fun wechat_paid_with_merchant_line() {
        val p = wx("微信支付", "已支付¥32.00 · 老乡鸡").payment()
        assertEquals(3200L, p.amountCents); assertEquals("老乡鸡", p.merchant)
    }

    @Test fun wechat_voucher() {
        val p = wx("微信支付凭证", "支付金额 ¥18.50 商户：美宜佳").payment()
        assertEquals(1850L, p.amountCents); assertEquals("美宜佳", p.merchant)
    }

    @Test fun wechat_receive_qr() {
        val p = wx("微信收款助手", "收款到账通知 收款金额¥6.00").payment()
        assertEquals(600L, p.amountCents); assertEquals(Direction.IN, p.direction)
    }

    @Test fun wechat_receive_transfer() {
        val p = wx("微信", "李四向你转账¥520.00").payment()
        assertEquals(52000L, p.amountCents); assertEquals(Direction.IN, p.direction); assertEquals("李四", p.merchant)
    }

    @Test fun wechat_red_packet_received() {
        val p = wx("微信红包", "你收到一个红包，金额8.88元").payment()
        assertEquals(888L, p.amountCents); assertEquals(Direction.IN, p.direction)
    }

    @Test fun wechat_red_packet_without_amount_is_unparsed() {
        assertEquals(ParseResult.Unparsed, wx("张三", "[微信红包]恭喜发财，大吉大利"))
    }

    @Test fun wechat_pay_to_merchant() {
        val p = wx("微信支付", "向沙县小吃付款 ¥14.00").payment()
        assertEquals(1400L, p.amountCents); assertEquals("沙县小吃", p.merchant)
    }

    @Test fun wechat_refund_is_income() {
        val p = wx("微信支付", "退款通知 退款¥25.00 已原路退回").payment()
        assertEquals(2500L, p.amountCents); assertEquals(Direction.IN, p.direction)
    }

    @Test fun ad_coupon_ignored() {
        assertTrue(ali("支付宝", "限时领取5元红包，满20可用，快来领取！") is ParseResult.Ignored)
    }

    @Test fun ad_instant_discount_ignored() {
        assertTrue(wx("微信支付", "微信支付立减优惠，本周五下单立减¥10") is ParseResult.Ignored)
    }

    @Test fun ad_monthly_bill_ignored() {
        assertTrue(ali("支付宝", "你的月度账单已出，本月支出3,200元，点击查看") is ParseResult.Ignored)
    }

    @Test fun payment_with_marketing_suffix_still_parses() {
        val p = ali("支付宝", "付款成功 ¥15.00 瑞幸咖啡，领取优惠券").payment()
        assertEquals(1500L, p.amountCents)
    }

    @Test fun ordinary_chat_is_irrelevant() {
        assertEquals(ParseResult.Irrelevant, wx("张三", "晚上一起吃饭吗"))
    }

    @Test fun other_package_is_irrelevant() {
        assertEquals(ParseResult.Irrelevant, PaymentNotificationParser.parse("com.other", "x", "已支付¥15.00"))
    }

    @Test fun payment_related_text_with_unknown_phrasing_is_unparsed() {
        assertEquals(ParseResult.Unparsed, ali("支付宝", "你的花呗本月应还款金额¥300.00，请及时支付"))
    }

    @Test fun rule_ids_are_unique() {
        val ids = PaymentNotificationParser.rules.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    // ---------------------------------------------------------------------
    // 语料（docs/research/wechat-deferred-pay.md §5）：三种通知，中英各 ≥10 条。
    // 真实格式脱敏后写成固定样本；识别不了的写 Unparsed，不做猜测。
    // ---------------------------------------------------------------------

    private sealed interface Expected {
        data class Paid(val cents: Long, val direction: Direction = Direction.OUT, val merchant: String? = null) : Expected
        data object Unparsed : Expected
        data object Ignored : Expected
        data object Irrelevant : Expected
    }

    private data class Sample(val title: String, val text: String, val expected: Expected)

    private fun runCorpus(pkg: String, samples: List<Sample>) {
        samples.forEach { s ->
            val label = "「${s.title} / ${s.text}」"
            val r = PaymentNotificationParser.parse(pkg, s.title, s.text)
            when (val e = s.expected) {
                is Expected.Paid -> {
                    assertTrue("$label 应识别为记账，实际 $r", r is ParseResult.Parsed)
                    val p = (r as ParseResult.Parsed).payment
                    assertEquals("$label 金额", e.cents, p.amountCents)
                    assertEquals("$label 方向", e.direction, p.direction)
                    if (e.merchant != null) assertEquals("$label 商户", e.merchant, p.merchant)
                }
                Expected.Unparsed -> assertEquals("$label 应为 Unparsed，实际 $r", ParseResult.Unparsed, r)
                Expected.Ignored -> assertTrue("$label 应为 Ignored，实际 $r", r is ParseResult.Ignored)
                Expected.Irrelevant -> assertEquals("$label 应为 Irrelevant，实际 $r", ParseResult.Irrelevant, r)
            }
        }
    }

    private val alipayCorpus = listOf(
        Sample("支付宝通知", "你有一笔 15.00 元的支出，点击查看详情", Expected.Paid(1500)),
        Sample("支付宝", "你有一笔12.00元的支出", Expected.Paid(1200)),
        Sample("支付宝", "你有一笔1,234.56元的支出，点击查看", Expected.Paid(123456)),
        Sample("支付宝通知", "你有一笔200.50元的收入", Expected.Paid(20050, Direction.IN)),
        Sample("支付宝", "付款成功 15.00元", Expected.Paid(1500)),
        Sample("支付宝支付", "成功付款15.00元", Expected.Paid(1500)),
        Sample("付款成功", "付款成功 ¥15.00 山野咖啡", Expected.Paid(1500, merchant = "山野咖啡")),
        Sample("支付宝", "你已向蜜雪冰城付款￥9.90", Expected.Paid(990, merchant = "蜜雪冰城")),
        Sample("支付宝", "在青禾便利消费￥23.50", Expected.Paid(2350, merchant = "青禾便利")),
        Sample("支付宝", "成功收款¥88.00", Expected.Paid(8800, Direction.IN)),
        Sample("支付宝", "张三向你转账100元", Expected.Paid(10000, Direction.IN, "张三")),
        Sample("支付宝", "退款¥25.00已到账", Expected.Paid(2500, Direction.IN)),
        Sample("Alipay", "Payment amount: 15.00 yuan", Expected.Paid(1500)),
        Sample("Alipay", "amount paid 15.00 Yuan.", Expected.Paid(1500)),
        Sample("Alipay", "Paid ¥12.00 to Green Cafe", Expected.Paid(1200, merchant = "Green Cafe")),
        Sample("Alipay", "Your payment of ¥8.80 was successful", Expected.Paid(880)),
        Sample("Alipay", "Payment amount: 1,280.00 yuan", Expected.Paid(128000)),
        Sample("Alipay", "Refund ¥8.00 received", Expected.Paid(800, Direction.IN)),
        Sample("Alipay", "You have a payment of ¥15.00", Expected.Paid(1500)),
        Sample("Alipay", "Payment amount: 15.00", Expected.Unparsed),
        Sample("Alipay", "Get a ¥5.00 coupon today", Expected.Ignored),
        Sample("Alipay", "Payment processing…", Expected.Irrelevant),
        Sample("支付宝", "交易提醒：你有一笔支付，点击查看>", Expected.Unparsed),
        // 2026-10-29 真机实测文案（金额已脱敏）：标题「交易提醒」，channel ACCOUNT
        Sample("交易提醒", "你有一笔 45.60 元的支出，点此查看详情。", Expected.Paid(4560)),
    )

    private val wechatCorpus = listOf(
        Sample("微信支付", "微信支付: 已支付¥15.00", Expected.Paid(1500)),
        Sample("微信支付", "已支付¥32.00 · 老乡鸡", Expected.Paid(3200, merchant = "老乡鸡")),
        Sample("微信支付凭证", "支付金额 ¥18.50 商户：美宜佳", Expected.Paid(1850, merchant = "美宜佳")),
        Sample("微信支付", "支付金额 12.00 元 收款方：青禾便利", Expected.Paid(1200, merchant = "青禾便利")),
        Sample("微信支付", "向沙县小吃付款 ¥14.00", Expected.Paid(1400, merchant = "沙县小吃")),
        Sample("微信支付", "支付成功 ¥7.50", Expected.Paid(750)),
        Sample("微信收款助手", "收款到账通知 收款金额¥6.00", Expected.Paid(600, Direction.IN)),
        Sample("微信", "李四向你转账¥520.00", Expected.Paid(52000, Direction.IN, "李四")),
        Sample("微信支付", "退款通知 退款¥25.00 已原路退回", Expected.Paid(2500, Direction.IN)),
        Sample("微信红包", "你收到一个红包，金额8.88元", Expected.Paid(888, Direction.IN)),
        Sample("微信支付", "付款成功 ¥15.00 山野咖啡", Expected.Paid(1500)),
        Sample("微信支付", "微信支付凭证 支付金额¥9.90 收款方：山楂书店", Expected.Paid(990, merchant = "山楂书店")),
        Sample("WeChat Pay", "Payment successful ¥15.00", Expected.Paid(1500)),
        Sample("WeChat Pay", "Payment successful! ¥18.50", Expected.Paid(1850)),
        Sample("WeChat Pay", "Payment of 25.50 yuan", Expected.Paid(2550)),
        Sample("WeChat Pay", "Paid ¥32.00 yuan to Local Chicken", Expected.Paid(3200, merchant = "Local Chicken")),
        Sample("WeChat Pay", "Payment successful ¥1,280.00", Expected.Paid(128000)),
        Sample("WeChat Pay", "New payment request\nPayment amount: 7.00 yuan", Expected.Paid(700)),
        Sample("WeChat Pay", "Refund ¥25.00 issued", Expected.Paid(2500, Direction.IN)),
        Sample("WeChat Pay", "Payment successful", Expected.Unparsed),
        Sample("WeChat Pay", "Cashback ¥2.00 credited", Expected.Ignored),
        Sample("WeChat Pay Score", "WeChat Pay Score enabled, limit ¥5,000.00", Expected.Ignored),
        Sample("WeChat", "Hello, are we still on for tonight?", Expected.Irrelevant),
    )

    private val deferredPayCorpus = listOf(
        Sample("微信支付", "先享后付订单扣款成功 ¥12.00", Expected.Paid(1200)),
        Sample("微信支付", "先享后付扣款成功 ¥8.80 收款方：青禾便利", Expected.Paid(880, merchant = "青禾便利")),
        Sample("微信支付分", "你的先享后付订单已完成，扣款 ¥23.50", Expected.Paid(2350)),
        Sample("微信支付", "先用后付订单已扣款 ¥6.00", Expected.Paid(600)),
        Sample("微信支付", "支付分先享后付本次扣款 ¥15.90", Expected.Paid(1590)),
        Sample("微信支付", "先享后付补缴成功 ¥9.90", Expected.Paid(990)),
        Sample("微信支付", "先享后付扣款成功 ¥30.00 尾号6688", Expected.Paid(3000)),
        Sample("微信支付", "先享后付 ¥18.00 收款方：山楂书店", Expected.Paid(1800, merchant = "山楂书店")),
        Sample("微信支付", "先享后付扣款成功 ¥1,280.00", Expected.Paid(128000)),
        Sample("微信支付", "先享后付扣款成功 ￥4.50", Expected.Paid(450)),
        Sample("微信支付", "微信支付分已开通，额度 ¥5,000.00", Expected.Ignored),
        Sample("微信支付", "先享后付服务已关闭", Expected.Ignored),
        Sample("微信支付", "免密支付扣款成功 ¥20.00", Expected.Unparsed),
        Sample("WeChat Pay", "Deferred payment charged ¥12.00", Expected.Paid(1200)),
        Sample("WeChat Pay", "Pay-after-use order completed, charged ¥23.50", Expected.Paid(2350)),
        Sample("WeChat Pay", "Deferred payment charged ¥8.80 · Green Cafe", Expected.Paid(880, merchant = "Green Cafe")),
        Sample("WeChat Pay", "Deferred payment deduction ¥30.00", Expected.Paid(3000)),
        Sample("WeChat Pay", "Pay later payment successful ¥6.00", Expected.Paid(600)),
        Sample("WeChat Pay", "Deferred payment charged ¥1,280.00", Expected.Paid(128000)),
        Sample("WeChat Pay", "Pay-after-use charge ¥15.90", Expected.Paid(1590)),
        Sample("WeChat Pay", "Deferred payment charged ¥4.50 at Green Cafe", Expected.Paid(450, merchant = "Green Cafe")),
        Sample("WeChat Pay", "Deferred payment for order A123 charged ¥11.00", Expected.Paid(1100)),
        Sample("WeChat Pay", "Deferred payment service closed", Expected.Irrelevant),
    )

    @Test fun corpus_alipay_payment() = runCorpus(PaymentApps.ALIPAY, alipayCorpus)

    @Test fun corpus_wechat_payment() = runCorpus(PaymentApps.WECHAT, wechatCorpus)

    @Test fun corpus_wechat_deferred_pay() = runCorpus(PaymentApps.WECHAT, deferredPayCorpus)

    /** 每种通知的中英文样本各 ≥10 条（docs/APP-DESIGN.md §4.4 测试流程第 1 步）。 */
    @Test fun corpus_covers_ten_samples_per_language() {
        listOf(alipayCorpus, wechatCorpus, deferredPayCorpus).forEach { corpus ->
            val zh = corpus.count { it.title.any { c -> c.code > 0x2E80 } }
            val en = corpus.size - zh
            assertTrue("中文样本 $zh 条，应 ≥10", zh >= 10)
            assertTrue("英文样本 $en 条，应 ≥10", en >= 10)
        }
    }

    @Test fun corpus_minus_negative_samples_are_all_paid() {
        listOf(alipayCorpus, wechatCorpus, deferredPayCorpus).forEach { corpus ->
            assertTrue(corpus.count { it.expected is Expected.Paid } >= 10)
        }
    }
}
