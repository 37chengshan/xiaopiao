package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Badge

/** Every title the app can hand out. Codes are stable ids; titles are the funny part. */
object BadgeCatalog {
    const val FIRST_TXN = "FIRST_TXN"
    const val MILKTEA_WAR_GOD = "MILKTEA_WAR_GOD"
    const val NO_TAKEOUT = "NO_TAKEOUT"
    const val WALLET_SLEEPER = "WALLET_SLEEPER"
    const val BREAKFAST_GUARD = "BREAKFAST_GUARD"
    const val NIGHT_FORAGER = "NIGHT_FORAGER"
    const val METRO_COMMUTER = "METRO_COMMUTER"
    const val SUB_CLEANER = "SUB_CLEANER"
    const val FRUGAL_STAR = "FRUGAL_STAR"
    const val RED_PACKET_AMBASSADOR = "RED_PACKET_AMBASSADOR"
    const val WEEKEND_WILD = "WEEKEND_WILD"
    const val STEADY_DOG = "STEADY_DOG"
    const val SMALL_FEE_SLAYER = "SMALL_FEE_SLAYER"
    const val LEARNING_INVESTOR = "LEARNING_INVESTOR"
    const val EVERYDAY_JOY = "EVERYDAY_JOY"

    data class Info(val code: String, val title: String, val emoji: String, val how: String)

    val all: List<Info> = listOf(
        Info(FIRST_TXN, "首单达成", "🎉", "记下人生第一笔账"),
        Info(MILKTEA_WAR_GOD, "奶茶战神", "🧋", "一天 2 杯以上，或一周奶茶咖啡最多"),
        Info(NO_TAKEOUT, "外卖绝缘体", "🍳", "一周没点美团/饿了么外卖"),
        Info(WALLET_SLEEPER, "钱包休眠者", "😴", "零消费日"),
        Info(BREAKFAST_GUARD, "早餐守护者", "🥐", "10 点前好好吃了早餐"),
        Info(NIGHT_FORAGER, "深夜觅食者", "🌙", "22 点以后还在下单"),
        Info(METRO_COMMUTER, "地铁通勤侠", "🚇", "出行打卡达人"),
        Info(SUB_CLEANER, "订阅清道夫", "🧹", "本该续费的订阅这周没再扣"),
        Info(FRUGAL_STAR, "精打细算星", "⭐", "本周花费比近 4 周均值低 20%"),
        Info(RED_PACKET_AMBASSADOR, "人情大使", "🧧", "发了红包、送了心意"),
        Info(WEEKEND_WILD, "周末放飞者", "🎈", "周末花费占了一周大头"),
        Info(STEADY_DOG, "稳如老狗", "🐶", "每天花费几乎一样稳"),
        Info(SMALL_FEE_SLAYER, "小额刺客克星", "🛡️", "一周几乎没被小额刺客偷袭"),
        Info(LEARNING_INVESTOR, "学习投资人", "📚", "为自己的脑袋花了钱"),
    )

    private val byCode = all.associateBy { it.code }

    fun badge(code: String, reason: String): Badge {
        val info = byCode.getValue(code)
        return Badge(code, info.title, info.emoji, reason)
    }

    /** Handed to non-empty weeks that earned nothing special, so nobody leaves empty-handed. */
    fun everydayJoy(reason: String): Badge = Badge(EVERYDAY_JOY, "平凡小确幸", "🌤️", reason)
}
