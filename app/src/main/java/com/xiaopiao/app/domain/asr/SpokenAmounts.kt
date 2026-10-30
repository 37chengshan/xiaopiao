package com.xiaopiao.app.domain.asr

/**
 * Spoken Chinese amounts → the digits the parser already understands.
 *
 * Speech models return 「三十五块五」/「三块五」 with Chinese numerals far more often than digits, and long
 * forms like 「三千五」 (3500, not 3005) that a literal numeral parse gets wrong. This rewrites only the
 * money-ish spans and leaves everything else alone; [com.xiaopiao.app.domain.parse.QuickParser] then reads
 * Arabic digits exactly as it does for typed text. Money never becomes a `Double`.
 */
object SpokenAmounts {

    private const val DIGITS = "零〇一二两三四五六七八九"

    /** Traditional and formal variants the recogniser may emit, folded to the characters we parse. */
    private val FOLD = mapOf(
        '塊' to '块', '圓' to '元', '兩' to '两', '〇' to '零',
        '壹' to '一', '贰' to '二', '貳' to '二', '叁' to '三', '參' to '三', '肆' to '四',
        '伍' to '五', '陆' to '六', '陸' to '六', '柒' to '七', '捌' to '八', '玖' to '九',
        '拾' to '十', '佰' to '百', '仟' to '千',
    )

    private val NUMERAL = DIGITS.toSet() + setOf('十', '百', '千', '万', '亿', '点')
    private val UNITS = mapOf('十' to 10L, '百' to 100L, '千' to 1_000L, '万' to 10_000L, '亿' to 100_000_000L)
    private val YUAN = setOf('块', '元')
    private val JIAO = setOf('毛', '角')

    fun normalize(input: String): String {
        if (input.isBlank()) return input
        val folded = fold(input)
        val out = StringBuilder(folded.length)
        var i = 0
        while (i < folded.length) {
            if (!isNumeral(folded[i])) {
                out.append(folded[i])
                i++
                continue
            }
            val runEnd = numeralRunEnd(folded, i)
            val run = folded.substring(i, runEnd)
            val rewritten = rewrite(folded, run, runEnd)
            if (rewritten == null) {
                out.append(run)
                i = runEnd
            } else {
                out.append(rewritten.amount)
                i = rewritten.end
            }
        }
        return out.toString()
    }

    private fun fold(text: String): String = buildString(text.length) {
        for (ch in text) append(FOLD[ch] ?: ch)
    }

    private fun isNumeral(ch: Char): Boolean = ch in NUMERAL

    private fun numeralRunEnd(text: String, from: Int): Int {
        var i = from
        while (i < text.length && isNumeral(text[i])) i++
        return i
    }

    private data class Rewrite(val amount: String, val end: Int)

    /**
     * `三十块五` → `30.5`, `一百零五块` → `105`, `五毛` → `0.5`, `三千五` → `3500`.
     * Returns null when the span is not an amount and should be left as it came in.
     */
    private fun rewrite(text: String, run: String, runEnd: Int): Rewrite? {
        // 「十二点」 is a clock, not a decimal: leave the whole run alone.
        if (text.getOrNull(runEnd) == '点' || run.endsWith("点")) return null

        var p = runEnd
        while (p < text.length && text[p] == ' ') p++
        val unit = text.getOrNull(p)

        if (unit != null && unit in YUAN) {
            val afterUnit = if (text.startsWith("钱", p + 1)) p + 2 else p + 1
            val major = if (run == "半") 5_00L else chineseCents(run) ?: return null
            val tail = tailAfterYuan(text, afterUnit)
            val cents = major + (tail?.cents ?: 0L)
            return Rewrite(format(cents), tail?.end ?: afterUnit)
        }
        if (unit != null && (unit in JIAO || unit == '分')) {
            if (run == "十" && unit == '分') return null // 「十分」 the adverb
            val value = if (run == "半") 5L else chineseNumber(run) ?: return null
            val cents = if (unit == '分') value * 1 else value * 10
            var end = p + 1
            if (text.getOrNull(end) == '钱') end++
            return Rewrite(format(cents), end)
        }

        // A bare long number: 「三千五」 must not become 3005, and 「三十五」 reads better as 35 to the parser.
        if (run.length < 2 || run.any { it == '点' }) return null
        val looksCompound = run.any { it in setOf('十', '百', '千', '万', '亿') }
        if (!looksCompound) return null
        val value = chineseNumber(run) ?: return null
        return Rewrite(value.toString(), runEnd)
    }

    private data class Tail(val cents: Long, val end: Int)

    /** The part after 块/元: 「五」= 5 角, 「五毛」, 「半」= 5 角, 「三分」= 3 分. */
    private fun tailAfterYuan(text: String, from: Int): Tail? {
        var p = from
        while (p < text.length && text[p] == ' ') p++
        if (p >= text.length) return null
        val ch = text[p]
        if (ch == '半') return Tail(50L, p + 1)
        if (!isNumeral(ch)) return null
        val end = numeralRunEnd(text, p)
        val run = text.substring(p, end)
        val value = chineseNumber(run) ?: return null
        val unit = text.getOrNull(end)
        return when (unit) {
            in JIAO -> Tail(value * 10, if (text.getOrNull(end + 1) == '钱') end + 2 else end + 1)
            '分' -> Tail(value * 1, end + 1)
            else -> if (run.length == 1) Tail(value * 10, end) else null
        }
    }

    private fun format(cents: Long): String {
        val major = cents / 100
        val minor = (cents % 100).toInt()
        return when {
            minor == 0 -> major.toString()
            minor % 10 == 0 -> "$major.${minor / 10}"
            else -> "$major.${minor.toString().padStart(2, '0')}"
        }
    }

    /** Cents of a 元 part: 「三十五」 → 3500, 「半」 handled by the caller. */
    private fun chineseCents(run: String): Long? = chineseNumber(run)?.times(100)

    /**
     * Chinese numerals to a whole number, including the spoken ellipsis: 「三千五」 = 3500, 「两百五」 = 250,
     * 「一万二」 = 12000. Returns null for anything that is not a plain numeral phrase.
     */
    fun chineseNumber(run: String): Long? {
        if (run.isEmpty()) return null
        if (run.all { it in DIGITS }) {
            // A string of bare digits is unusual in speech; treat it as digits only when short.
            if (run.length > 2) return null
            return run.fold(0L) { acc, ch -> acc * 10 + digit(ch)!! }
        }
        var total = 0L
        var section = 0L
        var number = -1L
        for (ch in run) {
            val d = digit(ch)
            if (d != null) {
                number = d
                continue
            }
            val unit = UNITS[ch] ?: return null
            val n = if (number < 0) 1L else number
            when (unit) {
                10_000L, 100_000_000L -> {
                    section = (section + n) * unit
                    total += section
                    section = 0
                }
                else -> section += n * unit
            }
            number = -1L
        }
        var value = total + section + (if (number < 0) 0 else number)

        // Ellipsis: a trailing digit right after 百/千/万/亿 means that digit of the next lower unit.
        val last = run.last()
        val prev = run.getOrNull(run.length - 2)
        if (run.length >= 2 && last in DIGITS && prev != null && prev in UNITS && UNITS.getValue(prev) >= 100L && prev != '十') {
            val base = run.dropLast(1)
            val baseValue = chineseNumber(base) ?: return value
            value = baseValue + (digit(last) ?: 0L) * (UNITS.getValue(prev) / 10)
        }
        return value
    }

    private fun digit(ch: Char): Long? = when (ch) {
        '零', '〇' -> 0L
        '一' -> 1L
        '二', '两' -> 2L
        '三' -> 3L
        '四' -> 4L
        '五' -> 5L
        '六' -> 6L
        '七' -> 7L
        '八' -> 8L
        '九' -> 9L
        else -> null
    }
}
