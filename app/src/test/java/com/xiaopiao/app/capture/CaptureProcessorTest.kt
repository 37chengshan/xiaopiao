package com.xiaopiao.app.capture

import com.xiaopiao.app.data.CaptureLogEntry
import com.xiaopiao.app.data.InMemoryLedgerRepository
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.needsAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureProcessorTest {
    private class FakeLog : CaptureLogSink {
        val entries = mutableListOf<CaptureLogEntry>()
        override suspend fun record(entry: CaptureLogEntry): Long { entries += entry; return entries.size.toLong() }
    }

    private val ledger = InMemoryLedgerRepository()
    private val log = FakeLog()
    private val processor = CaptureProcessor(ledger, log)
    private val t = 1_761_550_000_000L

    @Test fun storesParsedPaymentAndLogsIt() = runBlocking {
        processor.process(PaymentApps.ALIPAY, "付款成功", "付款成功 ¥15.00 瑞幸咖啡", t)
        val txn = ledger.observeAll().first().single()
        assertEquals(1500L, txn.amountCents); assertEquals("瑞幸咖啡", txn.merchant)
        assertEquals(Category.DRINK, txn.category); assertEquals(Source.NOTIFICATION, txn.source)
        assertEquals("", txn.note) // raw text never reaches the ledger
        assertTrue(log.entries.single().result.startsWith("PARSED:"))
    }

    @Test fun redPacketWithoutAmountLandsInLedgerToFillIn() = runBlocking {
        processor.process(PaymentApps.WECHAT, "袁开浩", "[微信红包] 恭喜发财，大吉大利", t)
        val txn = ledger.observeAll().first().single()
        assertEquals(0L, txn.amountCents); assertEquals("袁开浩的红包", txn.merchant)
        assertEquals(Category.SOCIAL, txn.category); assertTrue(txn.needsAmount)
        assertTrue(log.entries.single().result.startsWith(CaptureProcessor.NEEDS_AMOUNT))
    }

    @Test fun repeatedNotificationIsDeduped() = runBlocking {
        repeat(2) { processor.process(PaymentApps.WECHAT, "微信支付", "已支付¥15.00", t + it * 1000) }
        assertEquals(1, ledger.observeAll().first().size)
        assertTrue(log.entries.last().result.startsWith("DUPLICATE"))
    }

    @Test fun bareThenMerchantNotificationDoesNotDoubleCount() = runBlocking {
        processor.process(PaymentApps.ALIPAY, "支付宝通知", "你有一笔15.00元的支出", t)
        processor.process(PaymentApps.ALIPAY, "付款成功", "付款成功 ¥15.00 喜茶", t + 30_000)
        assertEquals(1, ledger.observeAll().first().size)
    }

    @Test fun adsAndUnparsedAreLoggedButNotStored() = runBlocking {
        processor.process(PaymentApps.ALIPAY, "支付宝", "限时领取5元红包，快来领取", t)
        processor.process(PaymentApps.ALIPAY, "支付宝", "你的花呗本月应还款金额¥300.00，请及时支付", t)
        assertTrue(ledger.observeAll().first().isEmpty())
        assertEquals(listOf("IGNORED", "UNPARSED"), log.entries.map { it.result.substringBefore(':') })
    }

    @Test fun deferredPaymentBecomesNotificationRow() = runBlocking {
        processor.process(PaymentApps.WECHAT, "微信支付", "先享后付扣款成功 ¥8.80 收款方：青禾便利", t)
        val txn = ledger.observeAll().first().single()
        assertEquals(880L, txn.amountCents)
        assertEquals("青禾便利", txn.merchant)
        assertEquals(Source.NOTIFICATION, txn.source)
        assertTrue(log.entries.single().result.startsWith("PARSED:wechat.deferred"))
    }

    /** 转账入账时商户是对方的名字，「转账」二字要留在备注里，预算的「计入」开关才认得出来。 */
    @Test fun transferKeepsItsWordInTheNoteSoBudgetCanCountIt() = runBlocking {
        processor.process(PaymentApps.ALIPAY, "支付宝", "张三向你转账 ¥50.00", t)
        processor.process(PaymentApps.WECHAT, "微信支付", "收到转账 ¥20.00", t + 3_600_000L)
        val (alipay, wechat) = ledger.observeAll().first().sortedBy { it.occurredAt }
        assertEquals("张三", alipay.merchant); assertEquals("转账", alipay.note)
        assertEquals("转账", wechat.note)
    }

    /** 微信/支付宝偶尔在毫秒级连发两条同样的账单：读-写必须串行，否则账本会多一行。 */
    @Test fun concurrentDuplicatePostsStoreOneRow() = runBlocking {
        (0 until 4).map { i ->
            async(Dispatchers.Default) {
                processor.process(PaymentApps.WECHAT, "微信支付", "已支付¥15.00", t + i * 300L)
            }
        }.awaitAll()
        assertEquals(1, ledger.observeAll().first().size)
    }

    @Test fun chatterIsNeitherStoredNorLogged() = runBlocking {
        processor.process(PaymentApps.WECHAT, "张三", "晚上吃啥", t)
        assertTrue(log.entries.isEmpty())
    }
}
