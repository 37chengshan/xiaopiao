package com.xiaopiao.app.domain.asr

import com.xiaopiao.app.domain.CategoryDef
import java.util.Locale

/**
 * The words the recogniser should lean towards: how people say money in a ledger, plus the names this
 * particular user actually types (merchants, custom categories).
 *
 * sherpa-onnx takes a plain text file, one phrase per line, and uses it with `modified_beam_search` and a
 * small `hotwordsScore`. The list is capped and de-duplicated so a long ledger cannot slow decoding down.
 */
object AsrHotwords {

    /** 100 phrases: enough to cover money words and a user's own shops without biasing everything else. */
    const val LIMIT = 100
    private const val MAX_PHRASE = 24

    /** Amounts, units and the verbs people use when they tell the app what they spent (docs/APP-DESIGN.md §4). */
    val presetZh: List<String> = listOf(
        "花了", "付了", "收到了", "退款", "转账", "报销", "AA", "一共", "总共", "零钱",
        "块钱", "块钱五", "元", "毛", "角分", "块五", "块三", "块八",
        "十块", "二十", "三十", "五十", "一百", "两百", "五百", "一千", "两千", "三千", "五千", "一万",
        "餐饮", "吃饭", "午饭", "晚饭", "早饭", "奶茶", "咖啡", "外卖", "零食", "买菜", "日用",
        "打车", "网约车", "地铁", "公交", "加油", "停车", "超市", "便利店",
        "房租", "水电", "物业", "话费", "宽带", "医药", "挂号",
        "电影", "演出", "会员", "订阅", "游戏", "礼物", "衣服", "鞋子",
        "工资", "奖金", "红包", "利息", "借给", "还了",
    )

    val presetEn: List<String> = listOf(
        "spent", "paid", "received", "refund", "transfer", "reimburse", "split", "total",
        "dollars", "bucks", "yuan", "cents",
        "lunch", "dinner", "breakfast", "brunch", "coffee", "bubble tea", "snack", "delivery",
        "taxi", "ride", "metro", "bus", "fuel", "parking", "groceries", "supermarket", "convenience store",
        "rent", "utilities", "internet", "phone bill", "pharmacy", "doctor",
        "cinema", "concert", "membership", "subscription", "game", "gift", "clothes", "shoes",
        "salary", "bonus", "red packet", "interest", "lent", "repaid",
    )

    /**
     * Fixed presets first, then the user's own names; first occurrence wins so a preset is never displaced
     * by a merchant that happens to share its spelling.
     */
    fun build(language: AsrLanguage, dynamic: List<String> = emptyList(), limit: Int = LIMIT): List<String> {
        val seen = HashSet<String>(limit * 2)
        val out = ArrayList<String>(limit)
        val source = if (language == AsrLanguage.EN) presetEn else presetZh
        for (candidate in source + dynamic) {
            if (out.size >= limit) break
            val phrase = clean(candidate) ?: continue
            val key = phrase.lowercase(Locale.ROOT)
            if (!seen.add(key)) continue
            out += phrase
        }
        return out
    }

    /** Merchants people actually paid recently, then custom category names, deduplicated and capped. */
    fun dynamicFor(defs: List<CategoryDef>, merchants: List<String>): List<String> {
        val custom = defs.filter { it.isCustom }.map { it.label }
        return merchants.asSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(60).toList() + custom
    }

    fun serialize(words: List<String>): String = words.joinToString(separator = "\n", postfix = if (words.isEmpty()) "" else "\n")

    private fun clean(raw: String): String? {
        val phrase = raw.replace(Regex("\\s+"), " ").trim()
        if (phrase.isEmpty() || phrase.length > MAX_PHRASE) return null
        if (phrase.none { it.isLetterOrDigit() || it in '一'..'鿿' }) return null
        return phrase
    }
}
