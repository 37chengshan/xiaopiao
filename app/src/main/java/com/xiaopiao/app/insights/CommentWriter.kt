package com.xiaopiao.app.insights

import com.xiaopiao.app.domain.Badge
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.yuan
import kotlin.random.Random

data class DayStats(
    val dayStart: Long,
    val totalOutCents: Cents,
    val txnCount: Int,
    val topCategory: Category?,
    val biggestCents: Cents,
    /** Average daily spend over the previous 7 days, if there is history. */
    val recentDailyAvgCents: Cents?,
    val badge: Badge?,
)

data class WeekStats(
    val weekStart: Long,
    val totalOutCents: Cents,
    /** Change vs. the comparable span of the previous week, in percent. Null when there is no baseline. */
    val vsPrevPercent: Int?,
    val topCategory: Category?,
    val zeroSpendDays: Int,
    val txnCount: Int,
    val badges: List<Badge>,
)

/**
 * Produces the one-line receipt comment. The seam for a future LLM-backed writer:
 * implement this interface and swap it in; the rest of the app does not change.
 */
// TODO LLM: provide an LLM-backed CommentWriter (structured stats in, one playful line out; never raw notification text).
interface CommentWriter {
    fun dayComment(stats: DayStats): String
    fun weekComment(stats: WeekStats): String
}

/** Playful, never-shaming templates. Picks are seeded by the date so a reprint reads the same. */
class TemplateCommentWriter : CommentWriter {

    override fun dayComment(stats: DayStats): String {
        val rnd = Random(stats.dayStart / DAY_MS)
        val total = "¥${stats.totalOutCents.yuan()}"
        val cat = stats.topCategory?.label ?: "日常"
        val pool = when {
            stats.txnCount == 0 -> EMPTY_DAY
            stats.totalOutCents == 0L -> ZERO_DAY
            isHeavy(stats) -> HEAVY_DAY
            stats.totalOutCents < LIGHT_LIMIT -> LIGHT_DAY
            else -> NORMAL_DAY
        }
        val line = pool.pick(rnd).fill(total, cat, stats.txnCount.toString())
        val nod = stats.badge?.takeIf { rnd.nextInt(100) < 60 }?.let { " ${NODS.pick(rnd).replace("{t}", "「${it.title}」")}" }
        return line + nod.orEmpty()
    }

    override fun weekComment(stats: WeekStats): String {
        val rnd = Random(stats.weekStart / DAY_MS)
        val total = "¥${stats.totalOutCents.yuan()}"
        val cat = stats.topCategory?.label ?: "日常"
        val pct = stats.vsPrevPercent
        val pool = when {
            stats.txnCount == 0 -> EMPTY_WEEK
            pct != null && pct <= -10 -> WEEK_DOWN
            pct != null && pct >= 10 -> WEEK_UP
            else -> WEEK_FLAT
        }
        val line = pool.pick(rnd).fill(total, cat, stats.txnCount.toString()).replace("{pct}", kotlin.math.abs(pct ?: 0).toString())
        val tail = if (stats.zeroSpendDays >= 2 && rnd.nextBoolean()) " 还有 ${stats.zeroSpendDays} 天钱包在睡觉。" else ""
        return line + tail
    }

    private fun isHeavy(s: DayStats): Boolean {
        val avg = s.recentDailyAvgCents
        return s.totalOutCents >= HEAVY_FLOOR && avg != null && avg > 0 && s.totalOutCents >= avg * 18 / 10
    }

    private fun List<String>.pick(rnd: Random): String = this[rnd.nextInt(size)]

    private fun String.fill(total: String, cat: String, count: String): String =
        replace("{total}", total).replace("{cat}", cat).replace("{n}", count)

    private companion object {
        const val LIGHT_LIMIT = 3000L
        const val HEAVY_FLOOR = 15000L

        val EMPTY_DAY = listOf(
            "今天还没有小票，是在认真生活还是忘了记？都可爱 🌱",
            "空白的一天，像一张还没打印的纸，留给你自己。",
            "今天的小票机在摸鱼，等你来补账～",
            "一笔都没记，今天的神秘度 100%。",
        )
        val ZERO_DAY = listOf(
            "零花费！钱包今天在冬眠，真乖 😴",
            "今天一分钱没花，小票机都不好意思出纸。",
            "安静的一天，钱包和你都被温柔对待了。",
            "零消费日达成，明天想吃点好的也完全合理。",
        )
        val LIGHT_DAY = listOf(
            "今天只花了 {total}，轻盈得像片羽毛 🪶",
            "{n} 笔小确幸，合计 {total}，刚刚好。",
            "{total} 的一天，温柔又克制，给自己点个赞。",
            "{cat}小小开销，{total} 收工，钱包面带微笑。",
        )
        val NORMAL_DAY = listOf(
            "今天 {n} 笔、{total}，{cat}担当出战，辛苦啦。",
            "{total} 换来一天的{cat}快乐，值不值你说了算 ✨",
            "平稳的一天，{total}，日子在好好过。",
            "{cat}是今天的主角，一共 {total}，剧情很温馨。",
            "{n} 笔小票整整齐齐，合计 {total}。",
        )
        val HEAVY_DAY = listOf(
            "今天花得挺痛快，{total}！开心值也该算进账里 😌",
            "大手笔的一天，{total}。花在哪、乐在哪，只有你懂。",
            "{cat}今天格外闪耀，{total}，偶尔放飞也是生活。",
            "{total}！今天的你很有行动力，明天再慢慢收心。",
        )
        val NODS = listOf(
            "顺手给你盖个章：{t}。",
            "头衔 {t} 已送达～",
            "今日称号：{t}，请收好。",
        )

        val EMPTY_WEEK = listOf(
            "这一周还没有小票，等你来补写故事 📝",
            "空白周报也是一种留白，下周见～",
        )
        val WEEK_DOWN = listOf(
            "这周 {total}，比上周轻盈了 {pct}%，{cat}依然是主角。",
            "{n} 笔合计 {total}，比上周少了 {pct}%，钱包悄悄松了口气。",
            "整体比上周少 {pct}%，{total} 收官，节奏很舒服 🌿",
        )
        val WEEK_UP = listOf(
            "这周 {total}，比上周多了 {pct}%，生活有滋有味 🍰",
            "{cat}撑起了这一周，{total}，比上周热闹 {pct}%。",
            "{n} 笔合计 {total}，热闹的一周，下周再看新剧情。",
        )
        val WEEK_FLAT = listOf(
            "这周 {total}，和上周几乎一个节奏，稳稳的幸福。",
            "{n} 笔、{total}，{cat}依旧是常驻嘉宾 ✨",
            "平平稳稳的一周，{total}，日子有自己的韵律。",
        )
    }
}
