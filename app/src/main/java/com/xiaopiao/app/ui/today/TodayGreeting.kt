package com.xiaopiao.app.ui.today

import com.xiaopiao.app.domain.Cents

/** What the "AI speaks first" header says. */
data class Greeting(val hello: String, val line: String)

/** Pure greeting logic: adapts to hour of day and what has (not) been recorded today. Never shaming. */
object TodayGreeting {
    fun hello(hour: Int): String = when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深啦"
    }

    fun build(hour: Int, hasAnyData: Boolean, todayCount: Int, todaySpendCents: Cents): Greeting {
        val hello = hello(hour)
        val line = when {
            !hasAnyData -> "我是你的小票机。记下第一笔，我就开始印小票啦～"
            todayCount == 0 && hour < 12 -> "今天还没记账哦，早饭吃了吗？"
            todayCount == 0 && hour < 18 -> "今天还没记账哦～有花销随时告诉我。"
            todayCount == 0 -> "今天暂时零消费！要不要印一张「钱包休眠」小票？"
            todaySpendCents == 0L -> "今天记了 $todayCount 笔，花销还是 ¥0，厉害。"
            else -> "今天已经花了 ${ReceiptLayout.money(todaySpendCents)}，共 $todayCount 笔。小票随时可印。"
        }
        return Greeting(hello, line)
    }
}
