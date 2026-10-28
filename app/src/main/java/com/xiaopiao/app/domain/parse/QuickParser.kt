package com.xiaopiao.app.domain.parse

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.startOfDay
import java.util.Calendar

/** One line the L0 parser understood: money, direction, category, merchant and when it happened. */
data class ParsedEntry(
    val amountCents: Cents,
    val direction: Direction,
    val category: Category,
    val merchant: String,
    val note: String,
    val occurredAt: Long,
)

/**
 * Local, deterministic "一句话" parser (no model, no network).
 *
 * Examples: `午饭32 骑车2` -> two entries; `昨天打车18` -> yesterday; `工资8000` -> income.
 * Amounts are parsed from their written form into [Cents]; no Double rounding is involved.
 * Sentences without an amount produce an empty list.
 */
object QuickParser {

    fun parse(input: String, now: Long): List<ParsedEntry> {
        val text = normalize(input)
        if (text.isBlank()) return emptyList()

        val dates = findDates(text, now)
        val masked = maskDates(text, dates)
        val amounts = scanAmounts(masked)
        if (amounts.isEmpty()) return emptyList()

        return amounts.mapIndexedNotNull { index, hit ->
            val previousEnd = if (index == 0) 0 else amounts[index - 1].end
            val nextStart = if (index == amounts.lastIndex) masked.length else amounts[index + 1].start
            val prefix = masked.substring(previousEnd, hit.start)
            val suffix = masked.substring(hit.end, nextStart)
            // Chinese says the label before the number; only accept a trailing label when it is glued on
            // ("32午饭"), so "午饭32 骑车2" never steals 骑车 from the next entry.
            val attachedSuffix = if (suffix.isNotEmpty() && !suffix[0].isWhitespace()) suffix else ""
            val context = prefix.ifBlank { attachedSuffix }
            val category = categoryOf(context)
            val cents = milliToCents(hit.milliYuan)
            if (cents <= 0) return@mapIndexedNotNull null
            ParsedEntry(
                amountCents = cents,
                direction = if (isIncome(context)) Direction.IN else Direction.OUT,
                category = category,
                merchant = merchantOf(prefix, attachedSuffix, category),
                note = "",
                occurredAt = dates.lastOrNull { it.start < hit.start }?.dayStart ?: now,
            )
        }
    }

    // ---------------------------------------------------------------- dates

    private data class DateHit(val start: Int, val end: Int, val dayStart: Long)

    private val dateWords = Regex(
        "大前天|今天|今日|昨天|昨日|前天" +
            "|上周[一二三四五六日天]|上星期[一二三四五六日天]" +
            "|本周[一二三四五六日天]|这周[一二三四五六日天]" +
            "|周[一二三四五六日天]|星期[一二三四五六日天]" +
            "|上周|上星期|本周|这周",
    )

    private fun findDates(text: String, now: Long): List<DateHit> =
        dateWords.findAll(text)
            .map { DateHit(it.range.first, it.range.last + 1, resolveDate(it.value, now)) }
            .sortedBy { it.start }
            .toList()

    private fun resolveDate(word: String, now: Long): Long = when (word) {
        "今天", "今日" -> now
        "昨天", "昨日" -> startOfDay(daysFrom(now, -1))
        "前天" -> startOfDay(daysFrom(now, -2))
        "大前天" -> startOfDay(daysFrom(now, -3))
        "上周", "上星期" -> startOfDay(daysFrom(now, -7))
        "本周", "这周" -> startOfWeek(now)
        else -> {
            val weekday = weekdayIndex(word.last())
            val monday = startOfWeek(now)
            when {
                word.startsWith("上") -> startOfDay(daysFrom(monday, weekday - 7))
                word.startsWith("本") || word.startsWith("这") -> startOfDay(daysFrom(monday, weekday))
                else -> {
                    // "周三" = the most recent Wednesday, today included.
                    val todayIndex = weekdayIndex(now)
                    val back = (todayIndex - weekday + 7) % 7
                    startOfDay(daysFrom(now, -back))
                }
            }
        }
    }

    private fun weekdayIndex(char: Char): Int = when (char) {
        '一' -> 0
        '二' -> 1
        '三' -> 2
        '四' -> 3
        '五' -> 4
        '六' -> 5
        else -> 6 // 日 / 天
    }

    private fun weekdayIndex(epochMillis: Long): Int {
        val dow = Calendar.getInstance().apply { timeInMillis = epochMillis }.get(Calendar.DAY_OF_WEEK)
        return (dow + 5) % 7 // Monday = 0 ... Sunday = 6
    }

    private fun startOfWeek(now: Long): Long =
        startOfDay(daysFrom(now, -weekdayIndex(now)))

    private fun daysFrom(epochMillis: Long, days: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = epochMillis
            add(Calendar.DAY_OF_MONTH, days)
        }.timeInMillis

    private fun maskDates(text: String, dates: List<DateHit>): String {
        if (dates.isEmpty()) return text
        val chars = text.toCharArray()
        dates.forEach { hit ->
            for (i in hit.start until hit.end.coerceAtMost(chars.size)) chars[i] = ' '
        }
        return String(chars)
    }

    // --------------------------------------------------------------- amounts

    private data class AmountHit(val start: Int, val end: Int, val milliYuan: Long)

    private fun scanAmounts(text: String): List<AmountHit> {
        val out = mutableListOf<AmountHit>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val hit = when {
                c.isDigit() || c == '¥' || c == '￥' -> matchArabic(text, i)
                isNumeral(c) -> matchChinese(text, i)
                else -> null
            }
            if (hit != null && hit.end > hit.start) {
                out += hit
                i = hit.end
            } else if (c.isDigit() || c == '¥' || c == '￥') {
                // Skip the whole token so a rejected "2026年" never re-parses as "026".
                i = skipNumberToken(text, i)
            } else if (isNumeral(c)) {
                while (i < text.length && isNumeral(text[i])) i++
            } else {
                i++
            }
        }
        return out
    }

    private fun skipNumberToken(text: String, from: Int): Int {
        var i = from
        if (text[i] == '¥' || text[i] == '￥') i++
        while (i < text.length && text[i].isDigit()) i++
        if (i < text.length && text[i] == '.' && i + 1 < text.length && text[i + 1].isDigit()) {
            i++
            while (i < text.length && text[i].isDigit()) i++
        }
        return if (i == from) from + 1 else i
    }

    private fun matchArabic(text: String, at: Int): AmountHit? {
        var p = at
        if (text[p] == '¥' || text[p] == '￥') p++
        if (p >= text.length || !text[p].isDigit()) return null
        val numberStart = p
        while (p < text.length && text[p].isDigit()) p++
        if (p + 1 < text.length && text[p] == '.' && text[p + 1].isDigit()) {
            p++
            while (p < text.length && text[p].isDigit()) p++
        }
        val numberText = text.substring(numberStart, p)
        if (numberText.length > 9) return null

        var j = p
        while (j < text.length && text[j] == ' ') j++

        // 1.2k / 2w / 3千 / 1万
        if (j < text.length) {
            val multiplier = when (text[j]) {
                'k', 'K' -> 1_000L
                'w', 'W' -> 10_000L
                '千' -> 1_000L
                '万' -> 10_000L
                else -> 0L
            }
            if (multiplier > 0L) {
                val next = text.getOrNull(j + 1)
                val looksLikeUnit = (text[j] == 'k' || text[j] == 'K') && next != null && isAsciiLetter(next)
                if (!looksLikeUnit) {
                    val base = parseDecimalMilli(numberText) ?: return null
                    return AmountHit(at, j + 1, base * multiplier)
                }
            }
        }

        // 32元 / 2块 / 3块钱
        val unit = yuanUnitAt(text, j)
        if (unit != null) {
            val major = parseDecimalMilli(numberText) ?: return null
            val afterUnit = j + unit.length
            val tail = subTailAt(text, afterUnit)
            return if (tail != null) AmountHit(at, tail.end, major + tail.milliYuan)
            else AmountHit(at, afterUnit, major)
        }

        // 5毛 / 5角 / 50分 (no yuan part)
        if (j < text.length && text[j] in "毛角分") {
            val value = parseDecimalMilli(numberText) ?: return null
            if (text[j] == '分' && text.getOrNull(j + 1) == '钟') return null
            val milli = if (text[j] == '分') value / 100 else value / 10
            var end = j + 1
            if (end < text.length && text[end] == '钱') end++
            return AmountHit(at, end, milli)
        }

        // Bare number: 32, 18.5. Reject counts ("2杯"), dates ("第2"), years ("2026年").
        if (isQuantityAt(text, j)) return null
        if (at > 0) {
            val prev = text[at - 1]
            if (prev.isDigit() || prev == '.' || prev in "第周星期月日号点岁%") return null
        }
        val milli = parseDecimalMilli(numberText) ?: return null
        return AmountHit(at, p, milli)
    }

    private fun matchChinese(text: String, at: Int): AmountHit? {
        var p = at
        while (p < text.length && isNumeral(text[p])) p++
        val run = text.substring(at, p)
        if (run.isEmpty() || run.length > 12) return null

        var j = p
        while (j < text.length && text[j] == ' ') j++

        // 十二块 / 三十五元 / 一块五
        val unit = yuanUnitAt(text, j)
        if (unit != null) {
            val major = if (run == "半") 500L else (parseChineseNumber(run) ?: return null) * 1_000L
            val afterUnit = j + unit.length
            val tail = subTailAt(text, afterUnit)
            return if (tail != null) AmountHit(at, tail.end, major + tail.milliYuan)
            else AmountHit(at, afterUnit, major)
        }

        // 五毛 / 五角 / 五分
        if (j < text.length && text[j] in "毛角分") {
            if (run == "十" && text[j] == '分') return null // "十分" the adverb
            if (text.getOrNull(j + 1) == '钟' || text.getOrNull(j + 1) == '之') return null
            val value = if (run == "半") 5L else parseChineseNumber(run) ?: return null
            val milli = if (text[j] == '分') value * 10 else value * 100
            var end = j + 1
            if (end < text.length && text[end] == '钱') end++
            return AmountHit(at, end, milli)
        }

        // Bare Chinese number ("午饭十二", "三十五"). Requires a real numeral phrase and no counter.
        if (run.length < 2 || run in NON_AMOUNT_RUNS) return null
        if (isQuantityAt(text, j)) return null
        if (at > 0) {
            val prev = text[at - 1]
            if (prev.isDigit() || prev in "第周星期月日号点岁%") return null
        }
        val value = parseChineseNumber(run) ?: return null
        return AmountHit(at, p, value * 1_000L)
    }

    /** "3块5" tail: [SubTail] is in milli-yuan, rounded to cents at the end. */
    private data class SubTail(val milliYuan: Long, val end: Int)

    private fun subTailAt(text: String, from: Int): SubTail? {
        if (from >= text.length) return null
        var p = from
        val start = p
        var isHalf = false
        var number: Long? = null
        if (text[p] == '半') {
            isHalf = true
            p++
        } else if (text[p].isDigit()) {
            while (p < text.length && text[p].isDigit() && p - start < 2) p++
            number = text.substring(start, p).toLongOrNull()
        } else if (isNumeral(text[p])) {
            while (p < text.length && isNumeral(text[p])) p++
            number = parseChineseNumber(text.substring(start, p))
        }
        if (!isHalf && number == null) return null
        val digits = text.substring(start, p)
        if (text.getOrNull(p) == '分' && text.getOrNull(p + 1) == '钟') return null

        return when (text.getOrNull(p)) {
            '毛', '角' -> {
                var end = p + 1
                if (end < text.length && text[end] == '钱') end++
                var milli = (number ?: 5L) * 100L
                // "3块5毛5分" — the fen part belongs to the same amount.
                val fenStart = end
                var q = fenStart
                var fen: Long? = null
                if (q < text.length && text[q].isDigit()) {
                    q++
                    fen = text.substring(fenStart, q).toLongOrNull()
                } else if (q < text.length && isNumeral(text[q])) {
                    while (q < text.length && isNumeral(text[q])) q++
                    fen = parseChineseNumber(text.substring(fenStart, q))
                }
                if (fen != null && text.getOrNull(q) == '分') {
                    milli += fen * 10L
                    end = q + 1
                    if (end < text.length && text[end] == '钱') end++
                }
                SubTail(milli, end)
            }
            '分' -> {
                var end = p + 1
                if (end < text.length && text[end] == '钱') end++
                SubTail((number ?: 5L) * 10L, end)
            }
            else -> {
                // No unit: 3块5 = 5角, 3块50 = 50分; "半" = 5角.
                val milli = when {
                    isHalf -> 500L
                    digits.length <= 1 -> (number ?: 5L) * 100L
                    else -> (number ?: 5L) * 10L
                }
                SubTail(milli, p)
            }
        }
    }

    private fun yuanUnitAt(text: String, j: Int): String? = when {
        j >= text.length -> null
        text.startsWith("块钱", j) -> "块钱"
        text[j] == '块' -> "块"
        text[j] == '元' -> "元"
        text[j] == '圆' -> "圆"
        else -> null
    }

    private val quantityChars =
        "杯个份碗件瓶张位人次斤公斤克米里站岁度天小时分秒年月日号点折倍层楼页条款项包袋盒只双套台部本支串把篇片罐箱颗粒根口亩升吨周星期更笔顿辆间家所门首副顶"

    private fun isQuantityAt(text: String, j: Int): Boolean =
        j < text.length && text[j] in quantityChars

    private fun isNumeral(c: Char): Boolean = c in "零〇一二两三四五六七八九十百千万亿半"

    private fun isAsciiLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

    private val NON_AMOUNT_RUNS = setOf(
        "千万", "十分", "万分", "万一", "一半", "两半", "百分", "千分", "百十", "千百",
    )

    private fun milliToCents(milliYuan: Long): Cents = (milliYuan + 5L) / 10L

    /** Exact decimal parsing: "18.5" -> 18_500 milli-yuan (thousandths), no Double in the path. */
    private fun parseDecimalMilli(raw: String): Long? {
        val dot = raw.indexOf('.')
        val majorText = (if (dot >= 0) raw.substring(0, dot) else raw).ifEmpty { "0" }
        if (majorText.length > 9) return null
        val major = majorText.toLongOrNull() ?: return null
        val fraction = if (dot >= 0) raw.substring(dot + 1) else ""
        val milli = (fraction + "000").take(3).toLongOrNull() ?: 0L
        return major * 1_000L + milli
    }

    private fun parseChineseNumber(raw: String): Long? {
        if (raw.isEmpty()) return null
        var total = 0L
        var section = 0L
        var number = 0L
        var lastUnit = 0L
        var pendingZero = false
        for (ch in raw) {
            val digit = when (ch) {
                '零', '〇' -> -1
                '一' -> 1
                '二', '两' -> 2
                '三' -> 3
                '四' -> 4
                '五' -> 5
                '六' -> 6
                '七' -> 7
                '八' -> 8
                '九' -> 9
                else -> 0
            }
            if (digit > 0) {
                number = digit.toLong()
                continue
            }
            if (digit == -1) {
                pendingZero = true
                continue
            }
            when (ch) {
                '十', '百', '千' -> {
                    val unit = when (ch) {
                        '十' -> 10L
                        '百' -> 100L
                        else -> 1_000L
                    }
                    if (number == 0L) number = 1L
                    section += number * unit
                    number = 0L
                    lastUnit = unit
                    pendingZero = false
                }
                '万' -> {
                    total += (section + number) * 10_000L
                    section = 0L
                    number = 0L
                    lastUnit = 10_000L
                    pendingZero = false
                }
                '亿' -> {
                    total = (total + section + number) * 100_000_000L
                    section = 0L
                    number = 0L
                    lastUnit = 100_000_000L
                    pendingZero = false
                }
                '半' -> return null
                else -> return null
            }
        }
        // "一百二" = 120, "一千二" = 1200, "一万二" = 12000. "一百零二" stays 102.
        val shorthand = number != 0L && lastUnit >= 100L && !pendingZero && number < 10L
        val tail = if (shorthand) number * (lastUnit / 10L) else number
        return total + section + tail
    }

    // ------------------------------------------------------------- semantics

    private class Rule(val category: Category, val keywords: List<String>)

    /**
     * Order matters: specific words before generic ones. GROCERY precedes the generic 买, DRINK
     * precedes 买, "面包" is matched here before MEAL sees 面.
     */
    private val rules = listOf(
        Rule(
            Category.DRINK,
            listOf("咖啡", "奶茶", "喜茶", "瑞幸", "星巴克", "拿铁", "美式", "茶颜", "coffee", "latte", "starbucks", "luckin"),
        ),
        Rule(Category.SNACK, listOf("零食", "薯片", "面包", "饼干", "巧克力", "蛋糕", "snack", "chips")),
        Rule(Category.GROCERY, listOf("超市", "菜市场", "买菜", "菜", "日用", "水果", "生鲜", "grocery", "supermarket", "market")),
        Rule(
            Category.TRANSPORT,
            listOf("地铁", "公交", "打车", "滴滴", "骑车", "单车", "加油", "高铁", "火车", "机票", "停车", "taxi", "uber", "subway", "metro", "bus", "bike", "fuel", "gas"),
        ),
        Rule(Category.SUBSCRIPTION, listOf("会员", "订阅", "月费", "年费", "subscription", "membership", "netflix", "spotify", "爱奇艺", "腾讯视频")),
        Rule(Category.HOUSING, listOf("房租", "水电", "物业", "燃气", "rent", "utilities", "electricity")),
        Rule(Category.HEALTH, listOf("药", "医院", "挂号", "体检", "看病", "牙医", "pharmacy", "medicine", "hospital", "doctor")),
        Rule(Category.LEARNING, listOf("书", "课程", "上课", "学费", "培训", "考试", "book", "course", "class", "tuition")),
        Rule(Category.FUN, listOf("电影", "游戏", "ktv", "演唱会", "剧本杀", "桌游", "movie", "cinema", "game", "concert")),
        Rule(Category.MEAL, listOf("早饭", "午饭", "晚饭", "早餐", "午餐", "晚餐", "吃饭", "饭", "面", "外卖", "食堂", "火锅", "烧烤", "夜宵", "快餐", "lunch", "dinner", "breakfast", "meal", "food", "noodles")),
        Rule(Category.SOCIAL, listOf("红包", "礼物", "份子", "随礼", "请客", "gift", "red packet")),
        Rule(Category.SHOPPING, listOf("淘宝", "京东", "拼多多", "买", "衣服", "鞋", "化妆品", "包包", "shopping", "taobao", "clothes", "shoes")),
    )

    private val incomeWords = listOf(
        "工资", "薪水", "收入", "红包收", "收红包", "收到红包", "收款", "到账", "入账", "进账",
        "发了", "奖金", "报销", "退款", "返现", "赚了", "兼职", "稿费", "利息", "分红",
        "salary", "income", "refund", "bonus",
    )

    private fun categoryOf(context: String): Category {
        if (context.isBlank()) return Category.OTHER
        val haystack = context.lowercase()
        return rules.firstOrNull { rule -> rule.keywords.any { containsKeyword(haystack, it) } }?.category
            ?: Category.OTHER
    }

    private fun isIncome(context: String): Boolean {
        if (context.isBlank()) return false
        val haystack = context.lowercase()
        return incomeWords.any { containsKeyword(haystack, it) }
    }

    /** Word-boundary match for ASCII keywords; plain substring for Chinese. */
    private fun containsKeyword(haystack: String, keyword: String): Boolean {
        if (keyword.isEmpty()) return false
        if (keyword[0].code >= 128) return haystack.contains(keyword)
        var from = 0
        while (true) {
            val index = haystack.indexOf(keyword, from)
            if (index < 0) return false
            val before = index == 0 || !haystack[index - 1].isLetter()
            val afterIndex = index + keyword.length
            val after = afterIndex >= haystack.length || !haystack[afterIndex].isLetter()
            if (before && after) return true
            from = index + 1
        }
    }

    private val leadingFillers = listOf(
        "花了", "花费", "付了", "支付了", "支付", "消费了", "消费", "用了", "用掉",
        "买了", "买", "收到", "收了", "赚了", "发了", "一共", "总共", "大概", "大约",
        "差不多", "还有", "然后", "另外", "再", "又", "在", "去", "我", "给",
    )
    private val trailingJunk = listOf("左右", "一共", "总共", "花了", "付了", "用了", "了", "的", "吧", "呢", "啊", "哦")

    private fun merchantOf(prefix: String, attachedSuffix: String, category: Category): String {
        var merchant = cleanPhrase(prefix)
        if (merchant.isEmpty()) merchant = cleanPhrase(attachedSuffix)
        if (merchant.isEmpty()) merchant = category.label
        return merchant.take(24)
    }

    private fun cleanPhrase(raw: String): String {
        var s = raw.replace(Regex("""[¥￥，,。.!！?？、;；:：""''（）()\[\]【】《》~～\-—_/\\|]"""), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        // Conjunctions are stripped only when what follows is a recognisable merchant phrase,
        // so a real name like "和平饭店" survives.
        for (conjunction in listOf("和", "跟", "与", "及")) {
            if (s.startsWith(conjunction) && s.length > 1) {
                val rest = s.substring(1).trimStart()
                if (rest.isNotEmpty() && categoryOf(rest) != Category.OTHER) s = rest
            }
        }
        var changed = true
        while (changed) {
            changed = false
            for (filler in leadingFillers) {
                if (s.startsWith(filler)) {
                    s = s.removePrefix(filler).trimStart()
                    changed = true
                }
            }
        }
        var trailingChanged = true
        while (trailingChanged) {
            trailingChanged = false
            for (junk in trailingJunk) {
                if (s.endsWith(junk)) {
                    s = s.removeSuffix(junk).trimEnd()
                    trailingChanged = true
                }
            }
        }
        return s.trim()
    }

    private fun normalize(input: String): String = buildString(input.length) {
        for (ch in input) {
            append(
                when (ch) {
                    in '０'..'９' -> '0' + (ch - '０')
                    '．' -> '.'
                    '，' -> ','
                    '　' -> ' '
                    '\n', '\t' -> ' '
                    else -> ch
                },
            )
        }
    }
}
