package com.xiaopiao.app.ui.today

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn

/** 演示数据: deterministic sample ledger used only by the Today preview mode (never written to the real ledger). */
object SampleData {
    private const val HOUR = 3_600_000L

    private fun t(day: Long, hour: Double, cents: Long, merchant: String, cat: Category, dir: Direction = Direction.OUT) =
        Txn(
            id = -(day / DAY_MS) - (hour * 100).toLong(), amountCents = cents, direction = dir, merchant = merchant,
            category = cat, occurredAt = day + (hour * HOUR).toLong(), source = Source.MANUAL,
        )

    fun build(today: Long): List<Txn> {
        val d = { back: Int -> today - back * DAY_MS }
        return listOf(
            // today: 9 rows so the "还有 1 笔" collapse is visible, plus one income row
            t(d(0), 8.2, 800, "豆浆油条", Category.MEAL),
            t(d(0), 9.1, 1490, "瑞幸咖啡 生椰拿铁", Category.DRINK),
            t(d(0), 12.3, 2800, "黄焖鸡米饭", Category.MEAL),
            t(d(0), 13.5, 450, "地铁", Category.TRANSPORT),
            t(d(0), 15.0, 1250, "便利店 薯片", Category.SNACK),
            t(d(0), 15.8, 1790, "喜茶 多肉葡萄", Category.DRINK),
            t(d(0), 18.4, 3600, "麻辣烫", Category.MEAL),
            t(d(0), 19.2, 2390, "超市 日用品", Category.GROCERY),
            t(d(0), 20.1, 1580, "视频会员", Category.SUBSCRIPTION),
            t(d(0), 20.5, 5000, "朋友转账", Category.OTHER, Direction.IN),
            // previous days
            t(d(1), 12.0, 3200, "午饭 拌饭", Category.MEAL),
            t(d(1), 16.0, 1800, "奶茶", Category.DRINK),
            t(d(2), 8.5, 600, "早餐", Category.MEAL),
            t(d(2), 19.0, 8800, "火锅", Category.MEAL),
            t(d(2), 21.0, 2999, "网购 手机壳", Category.SHOPPING),
            // d(3) intentionally empty: a "钱包休眠" day
            t(d(4), 18.5, 4200, "电影票", Category.FUN),
            t(d(5), 12.5, 2600, "盖饭", Category.MEAL),
            t(d(6), 13.0, 300, "公交", Category.TRANSPORT),
            t(d(6), 17.0, 2100, "咖啡", Category.DRINK),
        )
    }
}
