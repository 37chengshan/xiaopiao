package com.xiaopiao.app.capture

import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.Direction

data class QuickDraft(
    val amountCents: Cents,
    val merchant: String,
    val category: Category,
    /** 0 = 今天, -1 = 昨天, -2 = 前天 */
    val dayOffset: Int,
    val direction: Direction,
) {
    val dayLabel: String
        get() = when (dayOffset) {
            0 -> "今天"
            -1 -> "昨天"
            -2 -> "前天"
            else -> "${-dayOffset}天前"
        }
}

/**
 * "昨天打车 18 和奶茶 15" -> [打车 18 昨天, 奶茶 15 昨天].
 * Strategy: every number is an amount; the text in the gap before it is its label;
 * day words carry forward until another day word appears.
 */
object QuickAddParser {
    // Number not followed by a counter word (2杯奶茶 -> quantity, not money).
    private val amount = Regex("""[¥￥]?\s*(\d+(?:\.\d{1,2})?)(?!\d)(?![杯个份次张件只瓶碗支袋盒顿天])\s*(?:块钱|块|元|圆)?""")
    private val dayWord = Regex("""今天|今早|今晚|今日|昨天|昨晚|昨日|昨儿|前天|前晚""")
    private val leadingJunk = Regex("""^(?:[\s,，、;；.。+和跟与及]|还有|然后|再|又|另外)+""")
    private val leadingVerb = Regex("""^(?:花了|买了|吃了|喝了|点了|付了|用了|充了|交了)""")
    private val trailingJunk = Regex("""(?:[\s,，、;；.。]|花了|用了|付了|一共|总共|共|花|用)+$""")
    private val incomeLead = Regex("""^(?:收到|收入|入账|到账|进账|收了|赚了|赚)""")
    private val incomeWords = Regex("""工资|奖金|退款|报销|兼职|稿费|利息|分红|返现|生活费""")

    fun parse(input: String): List<QuickDraft> {
        val matches = amount.findAll(input).toList()
        if (matches.isEmpty()) return emptyList()
        val gaps = buildList {
            var prev = 0
            matches.forEach { m -> add(input.substring(prev, m.range.first)); prev = m.range.last + 1 }
            add(input.substring(prev))
        }
        val trailing = gaps.last()
        var carryDay = 0
        val drafts = matches.mapIndexedNotNull { i, m ->
            val cents = Amounts.toCents(m.groupValues[1]) ?: return@mapIndexedNotNull null
            if (cents <= 0) return@mapIndexedNotNull null
            var gap = gaps[i]
            val day = dayWord.find(gap)?.let { dayOffsetOf(it.value) }
            if (day != null) carryDay = day
            gap = dayWord.replace(gap, " ")
            if (clean(gap).isEmpty() && i == matches.lastIndex) {
                dayWord.find(trailing)?.let { carryDay = dayOffsetOf(it.value) }
                gap = dayWord.replace(trailing, " ")
            }
            buildDraft(cents, clean(gap), carryDay)
        }
        return drafts
    }

    private fun buildDraft(cents: Cents, label: String, day: Int): QuickDraft {
        val income = incomeLead.containsMatchIn(label) || incomeWords.containsMatchIn(label)
        val name = clean(incomeLead.replace(label, "")).ifEmpty { "随手记" }.take(20)
        val direction = if (income) Direction.IN else Direction.OUT
        return QuickDraft(cents, name, MerchantCategorizer.categorize(name), day, direction)
    }

    private fun clean(raw: String): String {
        var s = raw
        s = leadingJunk.replace(s, "")
        s = leadingVerb.replace(s, "")
        s = leadingJunk.replace(s, "")
        return trailingJunk.replace(s, "").trim()
    }

    private fun dayOffsetOf(word: String): Int = when {
        word.startsWith("昨") -> -1
        word.startsWith("前") -> -2
        else -> 0
    }
}
