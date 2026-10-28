package com.xiaopiao.app.wallet

import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import java.util.Calendar

/**
 * 把账本和卡包连起来（docs/APP-DESIGN.md §5.5）：通知里解析出的尾号决定这笔账归哪张卡；
 * 会员卡按商户名归。纯函数，方便单测。
 */
object CardLedger {
    /** 归属规则：先看结构化尾号，没有才退回备注里的「尾号 1234」。 */
    fun belongsTo(txn: Txn, card: BankCard): Boolean {
        if (card.last4.length != 4) return false
        if (txn.cardLast4.isNotEmpty()) return txn.cardLast4 == card.last4
        return txn.note.contains("尾号${card.last4}")
    }

    fun monthSpend(txns: List<Txn>, now: Long, card: BankCard): CardSpend =
        summarize(txns.filter { it.occurredAt >= monthStart(now) && belongsTo(it, card) })

    fun monthSpendAt(txns: List<Txn>, now: Long, storeName: String): CardSpend {
        val key = normalize(storeName)
        if (key.isEmpty()) return CardSpend(0, 0)
        return summarize(
            txns.filter {
                it.occurredAt >= monthStart(now) &&
                    (normalize(it.merchant).contains(key) || key.contains(normalize(it.merchant)))
            },
        )
    }

    fun recent(txns: List<Txn>, card: BankCard, limit: Int = 5): List<Txn> =
        txns.filter { belongsTo(it, card) }.sortedByDescending { it.occurredAt }.take(limit)

    fun recentAt(txns: List<Txn>, storeName: String, limit: Int = 5): List<Txn> {
        val key = normalize(storeName)
        if (key.isEmpty()) return emptyList()
        return txns.filter {
            normalize(it.merchant).contains(key) || key.contains(normalize(it.merchant))
        }.sortedByDescending { it.occurredAt }.take(limit)
    }

    /** 卡包总览的「本月卡上支出」：只算真正落在某张已录入卡上的账。 */
    fun monthCardSpend(txns: List<Txn>, now: Long, cards: List<BankCard>): CardSpend {
        val from = monthStart(now)
        val tails = cards.map { it.last4 }.filter { it.length == 4 }.toSet()
        return summarize(
            txns.filter {
                it.occurredAt >= from &&
                    (it.cardLast4 in tails || tails.any { tail -> it.note.contains("尾号$tail") })
            },
        )
    }

    private fun summarize(list: List<Txn>): CardSpend {
        val outs = list.filter { it.direction == Direction.OUT }
        return CardSpend(outs.sumOf { it.amountCents }, outs.size)
    }

    private fun monthStart(now: Long): Long = startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = startOfDay(now)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis,
    )

    private fun normalize(name: String): String =
        name.trim().lowercase().replace(" ", "").removeSuffix("店").removeSuffix("(中国)")
}
