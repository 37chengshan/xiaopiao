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
}
