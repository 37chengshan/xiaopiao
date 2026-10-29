package com.xiaopiao.app.capture

import com.xiaopiao.app.data.CaptureLogEntry
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.LedgerRepository
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CaptureLogSink {
    suspend fun record(entry: CaptureLogEntry): Long
}

/**
 * Glue between a raw notification and the ledger. Android-free so it can be unit tested.
 * Raw text goes only into the on-device capture log; the ledger gets structured fields.
 */
class CaptureProcessor(
    private val ledger: LedgerRepository,
    private val log: CaptureLogSink,
) {
    /** Apps sometimes post the same payment twice within milliseconds; the read-then-write must not interleave. */
    private val gate = Mutex()

    suspend fun process(pkg: String, title: String, text: String, postedAt: Long): ParseResult = gate.withLock {
        val result = PaymentNotificationParser.parse(pkg, title, text)
        when (result) {
            ParseResult.Irrelevant -> Unit
            ParseResult.Unparsed -> record(pkg, title, text, postedAt, "UNPARSED", null)
            is ParseResult.Ignored -> record(pkg, title, text, postedAt, "IGNORED:${result.reason}", null)
            is ParseResult.Parsed -> store(pkg, title, text, postedAt, result.payment)
        }
        result
    }

    private suspend fun store(pkg: String, title: String, text: String, postedAt: Long, p: ParsedPayment) {
        val txn = toTxn(pkg, postedAt, p, CardTail.extract(title + " " + text).orEmpty())
        val id = if (isNearDuplicate(txn)) null else ledger.add(txn)
        val tag = if (id == null) "DUPLICATE:${p.ruleId}" else "PARSED:${p.ruleId}"
        record(pkg, title, text, postedAt, tag, id)
    }

    /** Apps often post the same payment twice (e.g. a bare "支出" then a "付款成功" with merchant). */
    private suspend fun isNearDuplicate(txn: Txn): Boolean =
        ledger.observeRange(txn.occurredAt - NEAR_MS, txn.occurredAt + NEAR_MS).first().any {
            it.source == Source.NOTIFICATION && it.amountCents == txn.amountCents && it.direction == txn.direction
        }

    private suspend fun record(pkg: String, title: String, text: String, at: Long, result: String, txnId: Long?) {
        log.record(CaptureLogEntry(packageName = pkg, title = title, text = text, postedAt = at, result = result, txnId = txnId))
    }

    companion object {
        private const val NEAR_MS = 2 * 60_000L

        fun toTxn(pkg: String, postedAt: Long, p: ParsedPayment, cardLast4: String = ""): Txn {
            val redPacket = p.ruleId.contains("redpacket")
            val merchant = p.merchant ?: when {
                redPacket -> "微信红包"
                pkg == PaymentApps.ALIPAY -> "支付宝"
                else -> "微信支付"
            }
            val category = if (redPacket) Category.SOCIAL else MerchantCategorizer.categorize(merchant)
            return Txn(
                amountCents = p.amountCents, direction = p.direction, merchant = merchant, category = category,
                occurredAt = postedAt, source = Source.NOTIFICATION, cardLast4 = cardLast4,
                dedupeKey = DedupeKeys.notification(pkg, p.direction.name, p.amountCents, postedAt, p.merchant.orEmpty()),
            )
        }
    }
}
