package com.xiaopiao.app.ui.today

import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.ui.tr

/** What the "AI speaks first" header says. */
data class Greeting(val hello: String, val line: String)

/** Pure greeting logic: adapts to hour of day and what has (not) been recorded today. Never shaming. */
object TodayGreeting {
    fun hello(hour: Int): String = when (hour) {
        in 5..10 -> tr("早上好", "Good morning")
        in 11..13 -> tr("中午好", "Good afternoon")
        in 14..17 -> tr("下午好", "Good afternoon")
        in 18..22 -> tr("晚上好", "Good evening")
        else -> tr("夜深啦", "Late night")
    }

    fun build(hour: Int, hasAnyData: Boolean, todayCount: Int, todaySpendCents: Cents): Greeting {
        val hello = hello(hour)
        val line = when {
            !hasAnyData -> tr("我是你的小票机。记下第一笔，我就开始印小票啦～", "I am your receipt printer. Log the first entry and I start printing.")
            todayCount == 0 && hour < 12 -> tr("今天还没记账哦，早饭吃了吗？", "Nothing logged yet today — had breakfast?")
            todayCount == 0 && hour < 18 -> tr("今天还没记账哦～有花销随时告诉我。", "Nothing logged yet today. Tell me whenever you spend.")
            todayCount == 0 -> tr("今天暂时零消费！要不要印一张「钱包休眠」小票？", "No spending today. Print a wallet-resting receipt?")
            todaySpendCents == 0L -> tr("今天记了 $todayCount 笔，花销还是 ¥0，厉害。", "$todayCount entries today and still ¥0 spent.")
            else -> tr("今天已经花了 ${ReceiptLayout.money(todaySpendCents)}，共 $todayCount 笔。小票随时可印。", "${ReceiptLayout.money(todaySpendCents)} across $todayCount entries today. The receipt is ready when you are.")
        }
        return Greeting(hello, line)
    }
}
