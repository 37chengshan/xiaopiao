package com.xiaopiao.app.wallet

/**
 * 本机 BIN 小表（docs/APP-DESIGN.md §5.5）：只用于把卡号归到银行与卡种，不联网。
 * 前缀按长度降序匹配；命中不了就给一个中性的「银行卡 + 墨黑」，绝不猜错成别家。
 */
object CardBin {
    data class Rule(val prefix: String, val bank: String, val type: BankCardType, val brand: String, val face: CardFace)

    /** `brand` 只是卡面右下角那四个字母的候选，不做权益/等级判断。 */
    val rules: List<Rule> = listOf(
        Rule("622202", "中国工商银行", BankCardType.Debit, "银联", CardFace.Ink),
        Rule("622200", "中国工商银行", BankCardType.Debit, "银联", CardFace.Ink),
        Rule("621226", "中国工商银行", BankCardType.Debit, "银联", CardFace.Ink),
        Rule("622848", "中国农业银行", BankCardType.Debit, "银联", CardFace.Mist),
        Rule("622845", "中国农业银行", BankCardType.Debit, "银联", CardFace.Mist),
        Rule("622700", "中国建设银行", BankCardType.Debit, "银联", CardFace.Mist),
        Rule("621700", "中国建设银行", BankCardType.Debit, "银联", CardFace.Mist),
        Rule("436742", "中国建设银行", BankCardType.Debit, "VISA", CardFace.Ink),
        Rule("622588", "招商银行", BankCardType.Debit, "银联", CardFace.Coral),
        Rule("439188", "招商银行", BankCardType.Credit, "VISA", CardFace.Coral),
        Rule("622521", "上海浦东发展银行", BankCardType.Debit, "银联", CardFace.Mist),
        Rule("622155", "平安银行", BankCardType.Credit, "银联", CardFace.Coral),
        Rule("622690", "中信银行", BankCardType.Debit, "银联", CardFace.Ink),
        Rule("622260", "交通银行", BankCardType.Debit, "银联", CardFace.Mist),
        Rule("622908", "兴业银行", BankCardType.Debit, "银联", CardFace.Ink),
        Rule("622666", "中国光大银行", BankCardType.Debit, "银联", CardFace.Mist),
        Rule("622622", "中国民生银行", BankCardType.Debit, "银联", CardFace.Ink),
        Rule("622568", "广发银行", BankCardType.Debit, "银联", CardFace.Coral),
        Rule("621661", "中国银行", BankCardType.Debit, "银联", CardFace.Ink),
    ).sortedByDescending { it.prefix.length }

    /** 命中的银行信息；未知前缀返回「银行卡」中性信息（银行名留空由调用方决定怎么显示）。 */
    fun identify(pan: String): BinInfo {
        val digits = digitsOnly(pan)
        val hit = rules.firstOrNull { digits.startsWith(it.prefix) }
        if (hit != null) {
            return BinInfo(
                bankName = hit.bank, type = hit.type, brand = hit.brand,
                face = hit.face, known = true,
            )
        }
        val brand = when {
            digits.startsWith("4") -> "VISA"
            digits.startsWith("5") -> "Mastercard"
            digits.startsWith("35") -> "JCB"
            digits.startsWith("62") -> "银联"
            else -> "银联"
        }
        return BinInfo("银行卡", BankCardType.Debit, brand, CardFace.Ink, known = false)
    }

    data class BinInfo(
        val bankName: String,
        val type: BankCardType,
        val brand: String,
        val face: CardFace,
        val known: Boolean,
    )

    fun digitsOnly(input: String): String = input.filter { it.isDigit() }

    /** 4-4-4-4（19 位的卡把多余的数字接在最后一组）。 */
    fun groups(pan: String): String {
        val digits = digitsOnly(pan)
        if (digits.isEmpty()) return ""
        return digits.chunked(4).joinToString(" ")
    }

    fun last4(pan: String): String = digitsOnly(pan).takeLast(4)

    /** 默认展示：`•••• •••• •••• 1234`。 */
    fun mask(last4: String): String {
        val tail = last4.filter { it.isDigit() }.takeLast(4)
        return "•••• •••• •••• ${tail.ifEmpty { "••••" }}"
    }

    /** 卡号是否像一个真卡号：15–19 位且过 Luhn。表外的银行也照样能存，这里只挡明显的手误。 */
    fun looksValid(pan: String): Boolean {
        val digits = digitsOnly(pan)
        if (digits.length !in 15..19) return false
        var sum = 0
        digits.reversed().forEachIndexed { index, c ->
            var d = c - '0'
            if (index % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }
}
