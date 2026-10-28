package com.xiaopiao.app.data

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import java.util.Calendar
import kotlin.random.Random

/** Realistic, clearly-labelled demo ledger: ~3 weeks of daily spending plus monthly subscriptions. */
object DemoSeed {
    const val NOTE = "演示数据"
    private const val HOUR = 60 * 60 * 1000L
    private const val WINDOW_DAYS = 21

    /** Day offsets (0 = today) with deliberately no spending, to exercise 零消费日. */
    private val zeroSpendDays = setOf(-9, -16)

    private data class Sub(val merchant: String, val cents: Long, val offsets: List<Int>)

    private val subscriptions = listOf(
        Sub("爱奇艺", 2500, listOf(-58, -28, -2)),
        Sub("网易云音乐", 1500, listOf(-61, -31, -1)),
        Sub("iCloud", 600, listOf(-75, -45, -15)),
    )

    private val lunches = listOf("美团外卖" to 28L..42L, "饿了么" to 25L..38L, "沙县小吃" to 14L..22L, "公司食堂" to 12L..18L, "老乡鸡" to 24L..35L)
    private val drinks = listOf("喜茶" to 24L..32L, "瑞幸咖啡" to 9L..16L, "蜜雪冰城" to 6L..10L, "古茗" to 14L..20L, "霸王茶姬" to 15L..22L)

    fun generate(now: Long): List<Txn> {
        val out = mutableListOf<Txn>()
        for (offset in -WINDOW_DAYS..0) {
            val dayStart = startOfDay(now + offset * DAY_MS)
            if (offset !in zeroSpendDays) out += spendingFor(offset, dayStart, now)
        }
        subscriptions.forEach { sub ->
            sub.offsets.forEach { offset ->
                val dayStart = startOfDay(now + offset * DAY_MS)
                out += txn(sub.cents, sub.merchant, Category.SUBSCRIPTION, minOf(dayStart + 8 * HOUR, now - 60_000), "sub|${sub.merchant}", dayStart)
            }
        }
        return out.sortedBy { it.occurredAt }
    }

    private fun spendingFor(offset: Int, dayStart: Long, now: Long): List<Txn> {
        val rnd = Random(dayStart / DAY_MS) // deterministic per calendar day
        val weekend = Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_WEEK).let { it == Calendar.SATURDAY || it == Calendar.SUNDAY }
        val items = mutableListOf<Txn>()
        var n = 0
        fun add(cents: Long, merchant: String, cat: Category, hour: Double) {
            val at = minOf(dayStart + (hour * HOUR).toLong(), now - 60_000 * (1 + n))
            items += txn(cents, merchant, cat, at, "d$n", dayStart); n++
        }
        if (!weekend) {
            add(400, "地铁", Category.TRANSPORT, 8.4); add(rnd.range(400..600L), "地铁", Category.TRANSPORT, 18.9)
        }
        if (rnd.nextInt(100) < 35) add(rnd.range(800..1800L), "便利店早餐", Category.MEAL, 8.7)
        val (lm, lr) = lunches[rnd.nextInt(lunches.size)]
        add(rnd.range(lr.first * 100..lr.last * 100), lm, Category.MEAL, 12.4 + rnd.nextDouble())
        if (rnd.nextInt(100) < 70) {
            val (dm, dr) = drinks[rnd.nextInt(drinks.size)]
            add(rnd.range(dr.first * 100..dr.last * 100), dm, Category.DRINK, 15.2 + rnd.nextDouble())
        }
        if (rnd.nextInt(100) < 40) add(rnd.range(2200..5200L), "美团外卖", Category.MEAL, 19.5 + rnd.nextDouble())
        if (rnd.nextInt(100) < 25) add(rnd.range(1500..3500L), "滴滴出行", Category.TRANSPORT, 22.3)
        if (weekend && rnd.nextInt(100) < 60) add(rnd.range(4000..12000L), "盒马鲜生", Category.GROCERY, 11.0)
        if (weekend && rnd.nextInt(100) < 40) add(rnd.range(3500..9900L), "淘宝", Category.SHOPPING, 21.0)
        if (rnd.nextInt(100) < 20) add(rnd.range(2800..4500L), "万达影城", Category.FUN, 20.0)
        return items
    }

    private fun Random.range(r: LongRange): Long = nextLong(r.first, r.last + 1)

    private fun txn(cents: Long, merchant: String, cat: Category, at: Long, key: String, dayStart: Long) = Txn(
        amountCents = cents, direction = Direction.OUT, merchant = merchant, category = cat,
        occurredAt = at, source = Source.MANUAL, note = NOTE, dedupeKey = "demo|$dayStart|$key",
    )
}
