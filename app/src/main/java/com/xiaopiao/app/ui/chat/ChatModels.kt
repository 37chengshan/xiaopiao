package com.xiaopiao.app.ui.chat

import com.xiaopiao.app.data.DemoSeed
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.insights.SubscriptionDetector
import com.xiaopiao.app.insights.WeeklyReport
import com.xiaopiao.app.insights.WeeklyReportBuilder
import com.xiaopiao.app.insights.daysBetween
import com.xiaopiao.app.insights.normalizeMerchant
import kotlin.math.roundToLong

/**
 * The AI 对话 tab is entirely local: no model calls, no network. Everything here is a pure function of
 * [com.xiaopiao.app.domain.LedgerRepository.observeAll] plus the wall clock, so it is cheap and testable.
 */

/** One row in the chat flow. Assistant text has no bubble; cards are full-width. */
sealed interface ChatItem {
    val id: Long

    data class Assistant(override val id: Long, val text: String) : ChatItem
    data class User(override val id: Long, val text: String) : ChatItem
    data class Confirm(
        override val id: Long,
        val draft: ExpenseDraft,
        val state: ConfirmState = ConfirmState.Pending,
    ) : ChatItem
}

enum class ConfirmState { Pending, Saving, Saved, Duplicate }

/** A locally parsed expense, shown on the confirm card until the user accepts it. */
data class ExpenseDraft(
    val merchant: String,
    val category: Category,
    val amountCents: Cents,
    /** The original sentence, so 「改一下」 can put it back into the input field. */
    val sourceText: String,
) {
    fun toTxn(at: Long): Txn = Txn(
        amountCents = amountCents,
        merchant = merchant,
        category = category,
        occurredAt = at,
        source = Source.MANUAL,
    )
}

sealed interface ChatReply {
    data class Expense(val draft: ExpenseDraft) : ChatReply
    data class Answer(val text: String) : ChatReply
}

/** Everything the greeting, the cards and the template answers need, computed in one pass. */
data class ChatStats(
    val todayOutItems: List<Txn>,
    val yesterdayOutCount: Int,
    val yesterdayOutCents: Cents,
    val week: WeeklyReport,
    val weekPeakDay: Int?,
    val weekPeakCents: Cents,
    val hasAnyTxn: Boolean,
    val hasDemo: Boolean,
) {
    val todayOutCents: Cents get() = todayOutItems.sumOf { it.amountCents }
    val todayOutCount: Int get() = todayOutItems.size
}

data class ChatReminder(val text: String, val demo: Boolean)

/** Suggested prompts shown above the input until the first message is sent. */
val ChatSuggestions = listOf("今天花了多少", "这周哪天花得最多", "帮我记：午饭 28")

fun yuanText(cents: Cents): String = "¥" + cents.yuan()

fun buildChatStats(history: List<Txn>, now: Long): ChatStats {
    val today = startOfDay(now)
    val todayOut = history
        .filter { it.direction == Direction.OUT && it.occurredAt in today until today + DAY_MS }
        .sortedBy { it.occurredAt }
    val yesterday = today - DAY_MS
    val yesterdayOut = history.filter { it.direction == Direction.OUT && it.occurredAt in yesterday until today }
    val week = WeeklyReportBuilder.build(now, history, now)
    val peakIndex = (0 until week.startedDays).maxByOrNull { week.dayTotals[it] }
    val peak = peakIndex?.takeIf { week.dayTotals[it] > 0L }
    return ChatStats(
        todayOutItems = todayOut,
        yesterdayOutCount = yesterdayOut.size,
        yesterdayOutCents = yesterdayOut.sumOf { it.amountCents },
        week = week,
        weekPeakDay = peak,
        weekPeakCents = peak?.let { week.dayTotals[it] } ?: 0L,
        hasAnyTxn = history.isNotEmpty(),
        hasDemo = history.any { it.note == DemoSeed.NOTE },
    )
}

/**
 * One reminder line after the greeting: the nearest auto-renewal, or what the weekly report will say.
 * A subscription detected on demo rows carries the 演示 marker (product bottom line: never pass generated
 * numbers off as real).
 */
fun buildReminder(history: List<Txn>, stats: ChatStats, now: Long): ChatReminder {
    val today = startOfDay(now)
    val soonest = SubscriptionDetector.analyze(history, now)
        .map { it.subscription }
        .mapNotNull { sub -> daysBetween(today, sub.nextChargeAt).takeIf { it in 0..7 }?.let { sub to it } }
        .minByOrNull { it.second }
    if (soonest != null) {
        val (sub, days) = soonest
        val whenText = when (days) {
            0 -> "今天"
            1 -> "明天"
            else -> "$days 天后"
        }
        val demo = history.any {
            normalizeMerchant(it.merchant) == normalizeMerchant(sub.merchant) && it.note == DemoSeed.NOTE
        }
        return ChatReminder(
            "「${sub.merchant}」$whenText 会自动续费 ${yuanText(sub.amountCents)}。不想续的话，" +
                "「洞察 → 订阅侦探」里写了取消步骤，我不代你操作。",
            demo,
        )
    }
    return if (stats.week.txnCount > 0) {
        ChatReminder(
            "本周已经记了 ${stats.week.txnCount} 笔，合计 ${yuanText(stats.week.totalOutCents)}；周日我给你一张周报。",
            stats.hasDemo,
        )
    } else {
        ChatReminder("还没发现固定扣款。记下第一笔之后，我会开始帮你盯着。", false)
    }
}

/** Template sentences. No model involved — every number comes from the user's own ledger. */
object ChatCopy {
    val WEEK = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun greeting(stats: ChatStats?, hello: String): String {
        if (stats == null) return "我在数今天的账，稍等一下…"
        return "$hello，我是小票机。\n\n${summary(stats)}"
    }

    fun summary(stats: ChatStats): String = when {
        !stats.hasAnyTxn -> "账簿还是空的。说一句「午饭 28」，我就能记下第一笔。"
        stats.todayOutCount == 0 -> buildString {
            append("今天还没有花钱，钱包在休息。")
            if (stats.yesterdayOutCents > 0L) append("昨天花了 ${yuanText(stats.yesterdayOutCents)}。")
        }
        else -> "今天 ${stats.todayOutCount} 笔，花了 ${yuanText(stats.todayOutCents)}，${delta(stats)}。"
    }

    private fun delta(stats: ChatStats): String = when {
        stats.yesterdayOutCents == 0L -> "昨天没花钱"
        stats.todayOutCents > stats.yesterdayOutCents ->
            "比昨天多 ${yuanText(stats.todayOutCents - stats.yesterdayOutCents)}"
        stats.todayOutCents < stats.yesterdayOutCents ->
            "比昨天少 ${yuanText(stats.yesterdayOutCents - stats.todayOutCents)}"
        else -> "和昨天一样"
    }

    /** Tries the four things the local stats can actually answer, then falls back to a plain overview. */
    fun answer(text: String, stats: ChatStats): String = when {
        text.hasAny("你好", "在吗", "hi", "hello") ->
            "在的。我只看得懂你自己的账本：今天花了多少、这周哪天花得最多、最大一笔、分类占比。"
        text.hasAny("占比", "分类", "花在", "构成") -> categoryAnswer(stats)
        text.hasAny("最大", "最贵") -> biggestAnswer(stats)
        text.hasAny("哪天", "最多", "最高") -> peakAnswer(stats)
        text.hasAny("本周", "这周", "一周", "周报", "上周") -> weekAnswer(stats)
        text.hasAny("昨天", "昨日") -> yesterdayAnswer(stats)
        text.hasAny("今天", "今日", "多少", "花了") -> summary(stats)
        else -> overviewAnswer(stats)
    }

    private fun weekAnswer(stats: ChatStats): String {
        val week = stats.week
        if (week.txnCount == 0) return "这周还没有记账。"
        val vs = week.vsPrevPercent?.let {
            when {
                it > 0 -> "，比上周同期多 $it%"
                it < 0 -> "，比上周同期少 ${-it}%"
                else -> "，和上周同期持平"
            }
        }.orEmpty()
        return "本周 ${week.txnCount} 笔，合计 ${yuanText(week.totalOutCents)}$vs。"
    }

    private fun peakAnswer(stats: ChatStats): String {
        val day = stats.weekPeakDay ?: return "这周还没有花钱记录。"
        return "这周花得最多的是${WEEK[day]}，${yuanText(stats.weekPeakCents)}；" +
            "本周合计 ${yuanText(stats.week.totalOutCents)}。"
    }

    private fun biggestAnswer(stats: ChatStats): String {
        val biggest = stats.week.biggest ?: return "本周还没有支出。"
        return "本周最大一笔是「${biggest.merchant}」${yuanText(biggest.amountCents)}（${biggest.category.label}）。"
    }

    private fun categoryAnswer(stats: ChatStats): String {
        val top = stats.week.topCategories
        if (top.isEmpty()) return "本周还没有可分析的支出。"
        val parts = top.take(3).joinToString("、") {
            "${it.category.label} ${(it.share * 100).roundToLong()}%"
        }
        return "本周支出的分类占比：$parts。"
    }

    private fun yesterdayAnswer(stats: ChatStats): String =
        if (stats.yesterdayOutCount == 0) "昨天没有支出记录。"
        else "昨天 ${stats.yesterdayOutCount} 笔，共 ${yuanText(stats.yesterdayOutCents)}。"

    private fun overviewAnswer(stats: ChatStats): String {
        if (!stats.hasAnyTxn) return "账簿还是空的。说一句「午饭 28」，我就能记下第一笔。"
        return "今天 ${stats.todayOutCount} 笔 ${yuanText(stats.todayOutCents)}；" +
            "本周合计 ${yuanText(stats.week.totalOutCents)}。我只做本机统计，不推荐也不比价。"
    }

    private fun String.hasAny(vararg words: String): Boolean = words.any { contains(it, ignoreCase = true) }
}

/**
 * Tiny on-device intent router. A sentence with a money amount becomes an expense draft; everything else is
 * answered from [ChatStats]. This deliberately understands only the shapes it can do well — no model fallback.
 */
object ChatIntentRouter {
    private val amountRegex = Regex("""(\d{1,7}(?:\.\d{1,2})?)""")
    private val countSuffix = Regex("""^\s*(笔|天|号|次|个|人|小时|分钟|件|月|日|年|折|%|％|多岁)""")
    private val clockPattern = Regex("""\d{1,2}[:：]\d{2}""")

    private val questionMarkers = listOf(
        "多少", "几笔", "几天", "几号", "几次", "哪", "什么", "怎么", "为啥", "为什么",
        "吗", "呢", "？", "?", "最大", "最贵", "占比", "合计", "统计", "平均", "还剩",
    )

    fun route(text: String, stats: ChatStats): ChatReply {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ChatReply.Answer("说一句话就行，比如「今天花了多少」。")

        val asksForRecord = trimmed.hasAny("帮我记", "记一笔", "记一下")
        val amount = amountRegex.findAll(trimmed)
            .takeUnless { clockPattern.containsMatchIn(trimmed) }
            ?.firstOrNull { !countSuffix.containsMatchIn(trimmed.substring(it.range.last + 1)) }
            ?.let { match -> parseCents(match.groupValues[1])?.let { match.value to it } }

        return when {
            amount != null && !looksLikeQuestion(trimmed) ->
                ChatReply.Expense(draftOf(trimmed, amount.first, amount.second))
            asksForRecord ->
                ChatReply.Answer("好，说清楚金额就行，比如「午饭 28」。")
            else -> ChatReply.Answer(ChatCopy.answer(trimmed, stats))
        }
    }

    private fun looksLikeQuestion(text: String): Boolean =
        questionMarkers.any { text.contains(it, ignoreCase = true) }

    private fun parseCents(raw: String): Cents? {
        val value = raw.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value <= 0.0) return null
        return (value * 100).roundToLong().coerceIn(1L, 99_999_999L)
    }

    private fun draftOf(text: String, amountText: String, cents: Cents): ExpenseDraft {
        val category = categoryOf(text)
        return ExpenseDraft(
            merchant = merchantOf(text, amountText, category),
            category = category,
            amountCents = cents,
            sourceText = text,
        )
    }

    private fun merchantOf(text: String, amountText: String, category: Category): String {
        var s = text.replaceFirst(amountText, " ")
        s = prefixRegex.replace(s, "")
        s = s.replace(currencyRegex, "")
        s = trailingUnitRegex.replace(s, "")
        s = s.replace(spaceRegex, " ")
        s = s.trim(*punctuation)
        return s.take(16).ifBlank { category.label }
    }

    private val prefixRegex = Regex("""^(帮我记一笔|帮我记一下|帮我记|记一笔|记一下|记账|花了|付了|买了)\s*[:：,，、]?\s*""")
    private val currencyRegex = Regex("""[¥￥]""")
    private val trailingUnitRegex = Regex("""\s*(块钱|块|元整|元|钱)\s*$""")
    private val spaceRegex = Regex("""\s+""")
    private val punctuation = charArrayOf(' ', ':', '：', ',', '，', '、', '。', '.', '!', '！', '~', '～', '-', '—')

    private val categoryHints: List<Pair<Category, List<String>>> = listOf(
        Category.SUBSCRIPTION to listOf("会员", "订阅", "续费", "vip"),
        Category.DRINK to listOf("奶茶", "咖啡", "拿铁", "星巴克", "瑞幸", "喜茶", "霸王茶姬", "蜜雪", "古茗"),
        Category.MEAL to listOf("饭", "早餐", "午餐", "中饭", "晚饭", "外卖", "食堂", "面", "米线", "汉堡", "烧烤", "火锅", "沙县", "老乡鸡"),
        Category.SNACK to listOf("零食", "薯片", "巧克力", "水果", "蛋糕"),
        Category.GROCERY to listOf("超市", "买菜", "日用", "纸巾", "洗衣"),
        Category.TRANSPORT to listOf("地铁", "公交", "打车", "滴滴", "高铁", "火车", "加油", "停车", "单车"),
        Category.SHOPPING to listOf("淘宝", "京东", "拼多多", "衣服", "鞋", "包"),
        Category.FUN to listOf("电影", "游戏", "演出", "唱歌", "剧本杀"),
        Category.HOUSING to listOf("房租", "水电", "物业", "燃气"),
        Category.HEALTH to listOf("药", "医院", "体检", "挂号"),
        Category.SOCIAL to listOf("红包", "份子", "送礼", "请客"),
        Category.LEARNING to listOf("书", "课", "学习", "网课", "考试"),
    )

    private fun categoryOf(text: String): Category {
        val lower = text.lowercase()
        return categoryHints.firstOrNull { (_, words) -> words.any { lower.contains(it) } }?.first ?: Category.OTHER
    }

    private fun String.hasAny(vararg words: String): Boolean = words.any { contains(it, ignoreCase = true) }
}
