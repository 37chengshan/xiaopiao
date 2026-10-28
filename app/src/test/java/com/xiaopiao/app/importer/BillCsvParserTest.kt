package com.xiaopiao.app.importer

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class BillCsvParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private val wechat = "\uFEFF微信支付账单明细,,,,,,,,\n" +
        "微信昵称：[小明],,,,,,,,\n" +
        "起始时间：[2026-10-01 00:00:00] 终止时间：[2026-10-27 23:59:59],,,,,,,,\n" +
        "----------------------微信支付账单明细列表--------------------,,,,,,,,\n" +
        "交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注\n" +
        "2026-10-20 12:30:45,商户消费,瑞幸咖啡,生椰拿铁,支出,¥15.00,零钱,支付成功,4200001,M1,/\n" +
        "2026-10-20 18:02:10,转账,张三,转账备注,收入,\"¥1,200.00\",零钱,已收钱,4200002,M2,/\n" +
        "2026-10-21 09:00:00,微信红包,李四,/,支出,¥8.88,零钱,支付成功,4200003,M3,/\n" +
        "2026-10-21 10:00:00,零钱通转入,零钱通,/,/,¥100.00,零钱,支付成功,4200004,M4,/\n" +
        "2026-10-22 10:00:00,商户消费,某店,商品,支出,¥20.00,零钱,支付失败,4200005,M5,/\n" +
        "2026-10-22 11:00:00,商户消费,某店,商品,支出,abc,零钱,支付成功,4200006,M6,/\n"

    private val alipay = "------------------------支付宝（中国）网络技术有限公司  电子客户回单------------------------\n" +
        "姓名：小明\n" +
        "支付宝账户：x@example.com\n" +
        "起始时间：[2026-10-01 00:00:00]    终止时间：[2026-10-27 23:59:59]\n" +
        "------------------------交易明细列表------------------------\n" +
        "交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注,\n" +
        "2026-10-20 08:15:00 ,餐饮美食,美团外卖,mt***@mt.com,黄焖鸡米饭,支出,32.50,花呗,交易成功,2026102000001,M1,,\n" +
        "2026-10-20 09:00:00 ,交通出行,滴滴出行,did***,打车,支出,18.00,余额宝,交易成功,2026102000002,M2,,\n" +
        "2026-10-21 09:00:00 ,日用百货,某超市,abc,日用品,支出,66.00,花呗,交易关闭,2026102100003,M3,,\n" +
        "2026-10-21 12:00:00 ,其他,支付宝,/,余额宝-转入,不计收支,500.00,余额,交易成功,2026102100004,M4,,\n" +
        "2026-10-22 12:00:00 ,充值缴费,中国移动,13***,话费充值,支出,50.00,余额,交易成功,2026102200005,M5,,\n" +
        "2026-10-22 13:00:00 ,其他,小红,x,兼职结算,收入,300.00,余额,交易成功,2026102200006,M6,,\n"

    @Test fun wechatBasics() {
        val r = BillCsvParser.parse(wechat, zone)
        assertEquals(BillFormat.WECHAT, r.format)
        assertEquals(3, r.txns.size)
        assertEquals(2, r.skipped) // 零钱通 "/" and the failed payment
        assertEquals(1, r.failed)  // unreadable amount "abc"
    }

    @Test fun wechatRowFields() {
        val r = BillCsvParser.parse(wechat, zone)
        val coffee = r.txns[0]
        assertEquals(1500L, coffee.amountCents); assertEquals("瑞幸咖啡", coffee.merchant)
        assertEquals(Category.DRINK, coffee.category); assertEquals(Direction.OUT, coffee.direction)
        assertEquals(Source.CSV, coffee.source); assertEquals("生椰拿铁", coffee.note)
        assertEquals("csv|WECHAT|4200001", coffee.dedupeKey)
        val expectedAt = java.time.LocalDateTime.of(2026, 10, 20, 12, 30, 45).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expectedAt, coffee.occurredAt)
    }

    @Test fun wechatThousandsAndIncome() {
        val t = BillCsvParser.parse(wechat, zone).txns[1]
        assertEquals(120000L, t.amountCents); assertEquals(Direction.IN, t.direction); assertEquals(Category.SOCIAL, t.category)
    }

    @Test fun wechatRedPacketIsSocial() {
        assertEquals(Category.SOCIAL, BillCsvParser.parse(wechat, zone).txns[2].category)
    }

    @Test fun alipayBasics() {
        val r = BillCsvParser.parse(alipay, zone)
        assertEquals(BillFormat.ALIPAY, r.format)
        assertEquals(4, r.txns.size)
        assertEquals(2, r.skipped) // closed + 不计收支
        assertEquals(0, r.failed)
    }

    @Test fun alipayRowFields() {
        val r = BillCsvParser.parse(alipay, zone)
        val meal = r.txns[0]
        assertEquals(3250L, meal.amountCents); assertEquals("美团外卖", meal.merchant); assertEquals(Category.MEAL, meal.category)
        assertEquals("黄焖鸡米饭", meal.note); assertEquals("csv|ALIPAY|2026102000001", meal.dedupeKey)
        assertEquals(Category.TRANSPORT, r.txns[1].category)
    }

    @Test fun alipayCategoryFallbackAndIncome() {
        val r = BillCsvParser.parse(alipay, zone)
        assertEquals(Category.HOUSING, r.txns[2].category) // 中国移动
        assertEquals(Direction.IN, r.txns[3].direction)
    }

    @Test fun unknownFormat() {
        val r = BillCsvParser.parse("a,b,c\n1,2,3\n", zone)
        assertEquals(BillFormat.UNKNOWN, r.format); assertTrue(r.txns.isEmpty())
    }

    @Test fun csvReaderQuotes() {
        val rows = CsvReader.read("a,\"b,c\",\"d\"\"e\"\r\n1,2,3\n")
        assertEquals(listOf("a", "b,c", "d\"e"), rows[0]); assertEquals(listOf("1", "2", "3"), rows[1])
    }
}
