package com.xiaopiao.app.ui.today

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.ui.tr

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
            t(d(0), 8.2, 800, tr("豆浆油条", "Soy milk & fried dough"), Category.MEAL),
            t(d(0), 9.1, 1490, tr("瑞幸咖啡 生椰拿铁", "Luckin coconut latte"), Category.DRINK),
            t(d(0), 12.3, 2800, tr("黄焖鸡米饭", "Braised chicken rice"), Category.MEAL),
            t(d(0), 13.5, 450, tr("地铁", "Metro"), Category.TRANSPORT),
            t(d(0), 15.0, 1250, tr("便利店 薯片", "Convenience store chips"), Category.SNACK),
            t(d(0), 15.8, 1790, tr("喜茶 多肉葡萄", "Heytea grape tea"), Category.DRINK),
            t(d(0), 18.4, 3600, tr("麻辣烫", "Spicy hot pot"), Category.MEAL),
            t(d(0), 19.2, 2390, tr("超市 日用品", "Supermarket household goods"), Category.GROCERY),
            t(d(0), 20.1, 1580, tr("视频会员", "Video membership"), Category.SUBSCRIPTION),
            t(d(0), 20.5, 5000, tr("朋友转账", "Transfer from a friend"), Category.OTHER, Direction.IN),
            // previous days
            t(d(1), 12.0, 3200, tr("午饭 拌饭", "Lunch rice bowl"), Category.MEAL),
            t(d(1), 16.0, 1800, tr("奶茶", "Milk tea"), Category.DRINK),
            t(d(2), 8.5, 600, tr("早餐", "Breakfast"), Category.MEAL),
            t(d(2), 19.0, 8800, tr("火锅", "Hot pot"), Category.MEAL),
            t(d(2), 21.0, 2999, tr("网购 手机壳", "Online phone case"), Category.SHOPPING),
            // d(3) intentionally empty: a "钱包休眠" day
            t(d(4), 18.5, 4200, tr("电影票", "Movie ticket"), Category.FUN),
            t(d(5), 12.5, 2600, tr("盖饭", "Rice bowl"), Category.MEAL),
            t(d(6), 13.0, 300, tr("公交", "Bus"), Category.TRANSPORT),
            t(d(6), 17.0, 2100, tr("咖啡", "Coffee"), Category.DRINK),
        )
    }
}
