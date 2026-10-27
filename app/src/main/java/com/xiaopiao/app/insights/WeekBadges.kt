package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Badge
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.insights.BadgeCatalog as C
import kotlin.math.sqrt

/** Weekly rules. Evaluated in priority order; the caller usually shows the first one or two. */
internal object WeekBadges {
    private val TAKEOUT_APPS = listOf("美团", "饿了么", "meituan", "eleme")
    private const val SMALL_FEE_CENTS = 1500L

    fun compute(w: WeekSlice): List<Badge> {
        if (w.startedDays == 0) return emptyList()
        return listOfNotNull(
            firstTxn(w),
            milkTea(w),
            walletSleeper(w),
            frugalStar(w),
            noTakeout(w),
            subCleaner(w),
            nightForager(w),
            weekendWild(w),
            steadyDog(w),
            smallFeeSlayer(w),
            redPacket(w),
            learner(w),
            breakfast(w),
            metro(w),
        )
    }

    private fun firstTxn(w: WeekSlice): Badge? {
        val first = w.history.minOfOrNull { it.occurredAt } ?: return null
        return if (first in w.weekStart until w.weekEnd) C.badge(C.FIRST_TXN, "这周记下了人生第一笔账，欢迎开张！") else null
    }

    private fun milkTea(w: WeekSlice): Badge? {
        val drinks = w.outs.count { it.category == Category.DRINK }
        val maxPerDay = w.outs.filter { it.category == Category.DRINK }.groupBy { startOfDay(it.occurredAt) }.maxOfOrNull { it.value.size } ?: 0
        if (drinks < 3 && maxPerDay < 2) return null
        // "most drinks in week": at least as many as every one of the previous 4 weeks.
        val prevMax = (1..4).maxOfOrNull { n ->
            val s = w.prevWeekStart(n)
            val e = addDays(s, 7)
            w.history.count { it.direction == Direction.OUT && it.category == Category.DRINK && it.occurredAt in s until e }
        } ?: 0
        return when {
            drinks >= 3 && drinks >= prevMax -> C.badge(C.MILKTEA_WAR_GOD, "本周 $drinks 杯奶茶咖啡，近 5 周最多。")
            maxPerDay >= 2 -> C.badge(C.MILKTEA_WAR_GOD, "有一天直接喝了 $maxPerDay 杯，战神实锤。")
            else -> null
        }
    }

    private fun walletSleeper(w: WeekSlice): Badge? =
        if (w.zeroSpendDays >= 2 && w.history.any { it.occurredAt < w.weekEnd && it.occurredAt <= w.now }) {
            C.badge(C.WALLET_SLEEPER, "本周有 ${w.zeroSpendDays} 天零消费，钱包睡得很香。")
        } else null

    private fun frugalStar(w: WeekSlice): Badge? {
        if (!w.isComplete) return null
        val prev = (1..4).map { w.totalOutOfWeek(w.prevWeekStart(it)) }.filter { it > 0 }
        if (prev.size < 2 || w.outs.isEmpty()) return null
        val avg = prev.average()
        if (w.totalOut > avg * 0.8) return null
        val pct = ((1 - w.totalOut / avg) * 100).toInt()
        return C.badge(C.FRUGAL_STAR, "本周 ¥${w.totalOut.yuan()}，比近 ${prev.size} 周均值低了 $pct%。")
    }

    private fun noTakeout(w: WeekSlice): Badge? {
        if (!w.isComplete || w.outs.size < 3) return null
        val takeout = w.outs.any { t ->
            t.category == Category.MEAL && TAKEOUT_APPS.any { t.merchant.lowercase().contains(it) }
        }
        return if (!takeout) C.badge(C.NO_TAKEOUT, "这周一单美团/饿了么外卖都没点，灶台感谢你。") else null
    }

    private fun subCleaner(w: WeekSlice): Badge? {
        if (!w.isComplete) return null
        val dropped = SubscriptionDetector.droppedDuring(w.history, w.weekStart, w.weekEnd)
        return dropped.firstOrNull()?.let {
            C.badge(C.SUB_CLEANER, "「${it.merchant}」这周该续费却没扣，吸血鬼少了一只。")
        }
    }

    private fun nightForager(w: WeekSlice): Badge? {
        val late = w.outs.filter { it.category != Category.SUBSCRIPTION && BadgeEngine.isLateNight(it.occurredAt) }
        return if (late.size >= 2) C.badge(C.NIGHT_FORAGER, "本周 ${late.size} 笔深夜消费，夜猫子认证。") else null
    }

    private fun weekendWild(w: WeekSlice): Badge? {
        if (w.startedDays < 6 || w.totalOut < 20000L) return null
        val weekend = w.dayTotals[5] + w.dayTotals[6]
        return if (weekend * 2 >= w.totalOut) {
            C.badge(C.WEEKEND_WILD, "周末花了 ¥${weekend.yuan()}，占全周 ${weekend * 100 / w.totalOut}%，该放飞就放飞。")
        } else null
    }

    private fun steadyDog(w: WeekSlice): Badge? {
        val days = w.dayTotals.take(w.finishedDays.coerceAtLeast(0))
        if (days.size < 5 || days.any { it == 0L }) return null
        val mean = days.average()
        val cv = sqrt(days.sumOf { (it - mean) * (it - mean) } / days.size) / mean
        return if (cv < 0.35) C.badge(C.STEADY_DOG, "每天花费波动不到 ${(cv * 100).toInt().coerceAtLeast(1)}%，稳如老狗。") else null
    }

    private fun smallFeeSlayer(w: WeekSlice): Badge? {
        if (!w.isComplete || w.outs.size < 5) return null
        val small = w.outs.count { it.amountCents < SMALL_FEE_CENTS }
        return if (small <= 1) C.badge(C.SMALL_FEE_SLAYER, "${w.outs.size} 笔里小额(¥15 以下)只有 $small 笔，刺客全被你挡下。") else null
    }

    private fun redPacket(w: WeekSlice): Badge? {
        val sent = w.outs.filter { it.category == Category.SOCIAL }
        return if (sent.size >= 2) C.badge(C.RED_PACKET_AMBASSADOR, "本周送出 ${sent.size} 份心意，共 ¥${sent.sumCents().yuan()}。") else null
    }

    private fun learner(w: WeekSlice): Badge? {
        val l = w.outs.filter { it.category == Category.LEARNING }
        return if (l.isNotEmpty()) C.badge(C.LEARNING_INVESTOR, "本周为学习花了 ¥${l.sumCents().yuan()}。") else null
    }

    private fun breakfast(w: WeekSlice): Badge? {
        val days = w.outs.filter { it.category == Category.MEAL && BadgeEngine.isBreakfastHour(it.occurredAt) }
            .map { startOfDay(it.occurredAt) }.distinct().size
        return if (days >= 3) C.badge(C.BREAKFAST_GUARD, "本周有 $days 天吃上了早餐。") else null
    }

    private fun metro(w: WeekSlice): Badge? {
        val days = w.outs.filter { it.category == Category.TRANSPORT }.map { startOfDay(it.occurredAt) }.distinct().size
        return if (days >= 4) C.badge(C.METRO_COMMUTER, "本周 $days 天都在出行打卡。") else null
    }
}
