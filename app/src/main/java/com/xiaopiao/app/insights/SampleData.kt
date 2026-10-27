package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import kotlin.random.Random

/**
 * 演示数据 — a deterministic fake ledger so the insights screens can be previewed on an empty install.
 * Never mixed with real data; the UI always labels it `演示数据`.
 */
object SampleData {
    const val LABEL = "演示数据"
    private const val DAYS = 130

    fun generate(now: Long): List<Txn> {
        val rnd = Random(2026)
        var id = 1L
        val out = ArrayList<Txn>()
        fun add(day: Long, hour: Int, min: Int, cents: Long, merchant: String, cat: Category) {
            val at = addDays(day, 0) + (hour * 60L + min) * 60_000L
            if (at <= now) out += Txn(id++, cents, Direction.OUT, merchant, cat, at, Source.NOTIFICATION, dedupeKey = "demo-$id")
        }
        val today = startOfDay(now)
        for (back in DAYS downTo 0) {
            val day = addDays(today, -back)
            dailyHabits(day, back, rnd, ::add)
            monthlyCharges(day, ::add)
        }
        return out.sortedByDescending { it.occurredAt }
    }

    private fun dailyHabits(day: Long, back: Int, rnd: Random, add: (Long, Int, Int, Long, String, Category) -> Unit) {
        val wd = weekdayIndex(day)
        if (back in 10..11 || back == 24 || back == 31) return // a few zero-spend days
        if (wd < 5) {
            add(day, 8, 20 + rnd.nextInt(20), 800L + rnd.nextInt(600), "楼下早餐铺", Category.MEAL)
            add(day, 8, 55, 400L, "地铁", Category.TRANSPORT)
            add(day, 12, rnd.nextInt(30), 2200L + rnd.nextInt(1800), if (rnd.nextInt(3) == 0) "美团外卖" else "公司楼下小馆", Category.MEAL)
            if (rnd.nextInt(10) < 6) add(day, 15, 10, 1500L + rnd.nextInt(800), "喜茶", Category.DRINK)
            add(day, 18, 40, 400L, "地铁", Category.TRANSPORT)
        } else {
            add(day, 11, 30, 4500L + rnd.nextInt(5000), "周末小馆", Category.MEAL)
            add(day, 15, rnd.nextInt(50), 1800L + rnd.nextInt(900), "奈雪的茶", Category.DRINK)
            if (rnd.nextInt(10) < 4) add(day, 20, 10, 9000L + rnd.nextInt(15000), "商场逛街", Category.SHOPPING)
            if (rnd.nextInt(10) < 3) add(day, 22, 40, 3800L, "烧烤夜宵", Category.MEAL)
        }
    }

    private fun monthlyCharges(day: Long, add: (Long, Int, Int, Long, String, Category) -> Unit) {
        val dom = java.util.Calendar.getInstance().apply { timeInMillis = day }.get(java.util.Calendar.DAY_OF_MONTH)
        if (dom == 5) add(day, 0, 5, 2500L, "爱奇艺会员", Category.SUBSCRIPTION)
        if (dom == 12) add(day, 0, 10, 2500L, "腾讯视频会员", Category.SUBSCRIPTION)
        if (dom == 18) add(day, 3, 0, 1500L, "网易云音乐会员", Category.SUBSCRIPTION)
        if (dom == 21) add(day, 6, 0, 2100L, "iCloud+ 存储", Category.SUBSCRIPTION)
    }
}
