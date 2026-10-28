package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Badge
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.insights.BadgeCatalog as C

/**
 * Rule-based titles. Everything is deterministic and every badge carries a plain-language
 * [Badge.reason] so the user can always see *why* they got it.
 */
object BadgeEngine {
    /** All badges earned on a day, highest priority first. */
    fun badgesForDay(dayStart: Long, dayItems: List<Txn>, history: List<Txn>): List<Badge> {
        val all = (history + dayItems).distinct()
        val outs = dayItems.filter { it.direction == Direction.OUT }
        return listOfNotNull(
            firstTxn(dayStart, all),
            milkTea(outs),
            nightForager(outs),
            redPacket(outs),
            learner(outs),
            metro(outs),
            breakfast(outs),
            walletSleeper(dayStart, outs, all),
        )
    }

    /** The one stamp printed at the bottom of a daily receipt. */
    fun stampForDay(dayStart: Long, dayItems: List<Txn>, history: List<Txn>): Badge? =
        badgesForDay(dayStart, dayItems, history).firstOrNull()

    /** All badges earned in the week starting [weekStart] (as seen at [now]), highest priority first. */
    fun badgesForWeek(weekStart: Long, history: List<Txn>, now: Long): List<Badge> =
        WeekBadges.compute(WeekSlice(startOfWeek(weekStart), now, history))

    // ---- day rules -------------------------------------------------------------------------

    private fun firstTxn(dayStart: Long, all: List<Txn>): Badge? {
        val first = all.minOfOrNull { it.occurredAt } ?: return null
        return if (daysBetween(dayStart, first) == 0) {
            C.badge(C.FIRST_TXN, "人生第一笔账诞生在今天，小票机正式开张！")
        } else null
    }

    private fun milkTea(outs: List<Txn>): Badge? {
        val n = outs.count { it.category == Category.DRINK }
        return if (n >= 2) C.badge(C.MILKTEA_WAR_GOD, "今天一口气 $n 杯奶茶咖啡，续航拉满。") else null
    }

    private fun nightForager(outs: List<Txn>): Badge? {
        val late = outs.filter { it.category != Category.SUBSCRIPTION && isLateNight(it.occurredAt) }
        return if (late.isNotEmpty()) C.badge(C.NIGHT_FORAGER, "深夜还有 ${late.size} 笔消费，夜宵的快乐谁懂。") else null
    }

    private fun redPacket(outs: List<Txn>): Badge? {
        val sent = outs.filter { it.category == Category.SOCIAL }
        return if (sent.isNotEmpty()) {
            C.badge(C.RED_PACKET_AMBASSADOR, "今天送出 ¥${sent.sumOf { it.amountCents }.yuan()} 的心意，人缘担当。")
        } else null
    }

    private fun learner(outs: List<Txn>): Badge? {
        val l = outs.filter { it.category == Category.LEARNING }
        return if (l.isNotEmpty()) {
            C.badge(C.LEARNING_INVESTOR, "为知识花了 ¥${l.sumOf { it.amountCents }.yuan()}，脑袋会说谢谢。")
        } else null
    }

    private fun metro(outs: List<Txn>): Badge? {
        val n = outs.count { it.category == Category.TRANSPORT }
        return if (n >= 2) C.badge(C.METRO_COMMUTER, "今天出行打卡 $n 次，城市脉搏就是你。") else null
    }

    private fun breakfast(outs: List<Txn>): Badge? {
        val ok = outs.any { it.category == Category.MEAL && isBreakfastHour(it.occurredAt) }
        return if (ok) C.badge(C.BREAKFAST_GUARD, "10 点前吃上了早餐，胃说谢谢。") else null
    }

    private fun walletSleeper(dayStart: Long, outs: List<Txn>, all: List<Txn>): Badge? {
        val hasEarlier = all.any { it.occurredAt < dayStart }
        return if (outs.isEmpty() && hasEarlier) C.badge(C.WALLET_SLEEPER, "今天零消费，钱包睡了个好觉。") else null
    }

    internal fun isLateNight(ms: Long): Boolean = hourOf(ms).let { it >= 22 || it < 4 }
    internal fun isBreakfastHour(ms: Long): Boolean = hourOf(ms) in 5..9
}

internal fun List<Txn>.sumCents(): Cents = sumOf { it.amountCents }
