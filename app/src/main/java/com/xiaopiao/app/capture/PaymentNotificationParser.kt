package com.xiaopiao.app.capture

import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.Direction

object PaymentApps {
    const val ALIPAY = "com.eg.android.AlipayGphone"
    const val WECHAT = "com.tencent.mm"
    val all = setOf(ALIPAY, WECHAT)

    fun label(pkg: String): String = when (pkg) {
        ALIPAY -> "支付宝"
        WECHAT -> "微信"
        else -> pkg
    }
}

data class ParsedPayment(
    val amountCents: Cents,
    val direction: Direction,
    /** Null when the notification does not name a counterparty. */
    val merchant: String?,
    val ruleId: String,
)

sealed interface ParseResult {
    data class Parsed(val payment: ParsedPayment) : ParseResult
    /** Looked payment-ish but is marketing / not a transaction. */
    data class Ignored(val reason: String) : ParseResult
    /** Payment-ish text that no rule understood. Logged so the user can see misses. */
    data object Unparsed : ParseResult
    /** Ordinary chatter — never stored. */
    data object Irrelevant : ParseResult
}

/**
 * One row of the rule table.
 * Placeholders in [template]: `{AMT}` = named group `amount`, `{YEN}` = optional currency symbol.
 * Optional named group `merchant` captures the counterparty.
 */
data class PaymentRule(
    val id: String,
    val packages: Set<String>,
    val template: String,
    val direction: Direction,
) {
    val pattern: Regex = Regex(template.replace("{AMT}", AMT).replace("{YEN}", YEN))

    companion object {
        const val AMT = """(?<amount>\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)"""
        const val YEN = """[¥￥]?\s*"""
    }
}

/**
 * Pure-Kotlin notification parser. Rules are tried in order; first match wins.
 * To support a new phrasing, append a [rule] line to [rules] (specific rules above generic ones).
 */
object PaymentNotificationParser {
    private val ALI = setOf(PaymentApps.ALIPAY)
    private val WX = setOf(PaymentApps.WECHAT)
    private val BOTH = PaymentApps.all
    private const val NAME = """(?<merchant>[^\s,，。:：¥￥]{1,24}?)"""

    /** Greedy variant for names at the end of a phrase (nothing after it to stop a lazy match). */
    private const val NAME_END = """(?<merchant>[^\s,，。:：¥￥]{1,24})"""

    private fun rule(id: String, pkgs: Set<String>, template: String, dir: Direction = Direction.OUT) =
        PaymentRule(id, pkgs, template, dir)

    val rules: List<PaymentRule> = listOf(
        // ---- Alipay ----
        rule("alipay.count.out", ALI, """你有一笔\s*{YEN}{AMT}\s*元?的支出"""),
        rule("alipay.count.in", ALI, """你有一笔\s*{YEN}{AMT}\s*元?的收入""", Direction.IN),
        rule("alipay.receive.from", ALI, """${NAME}向你(?:转账|付款)\s*{YEN}{AMT}""", Direction.IN),
        rule("alipay.receive", ALI, """(?:成功收款|收款到账|收钱到账)\s*{YEN}{AMT}""", Direction.IN),
        rule("alipay.refund", ALI, """退款\s*{YEN}{AMT}""", Direction.IN),
        rule("alipay.pay.to", ALI, """(?:已|成功)?向${NAME}(?:付款|转账|支付)\s*{YEN}{AMT}"""),
        rule("alipay.pay.at", ALI, """在\s*${NAME}\s*(?:消费|支付)\s*{YEN}{AMT}"""),
        rule("alipay.pay.success.merchant-first", ALI, """付款成功\s+(?!付款|支付|成功)${NAME}\s+[¥￥]{AMT}"""),
        rule("alipay.pay.success", ALI, """(?:你已)?(?:成功付款|付款成功)[,，:：!！\s]*{YEN}{AMT}元?(?:\s+${NAME_END})?"""),
        rule("alipay.pay.out", ALI, """(?:你已)?(?:支付成功|已支付)[,，:：!！\s]*{YEN}{AMT}"""),

        // ---- WeChat ----
        rule("wechat.redpacket.out", WX, """(?:发出|发了|已发)[^\d¥￥]{0,8}红包.{0,12}?[¥￥]\s*{AMT}"""),
        rule("wechat.redpacket.in", WX, """(?:收到|领取)[^\d¥￥]{0,12}红包.{0,12}?{YEN}{AMT}\s*元?""", Direction.IN),
        rule("wechat.redpacket.in.stored", WX, """红包[^\d¥￥]{0,12}(?:已存入零钱|已到账)[^\d¥￥]{0,6}{YEN}{AMT}""", Direction.IN),
        rule("wechat.receive.qr", WX, """(?:二维码)?收款(?:到账)?(?:通知)?[^\d¥￥]{0,14}[¥￥]\s*{AMT}""", Direction.IN),
        rule("wechat.receive.transfer-from", WX, """${NAME}向你转账\s*{YEN}{AMT}""", Direction.IN),
        rule("wechat.receive.transfer", WX, """(?:已收款|收到转账|转账到账)[^\d¥￥]{0,6}{YEN}{AMT}""", Direction.IN),
        rule("wechat.refund", WX, """退款[^\d¥￥]{0,10}[¥￥]\s*{AMT}""", Direction.IN),
        rule("wechat.refund.amount-first", WX, """[¥￥]\s*{AMT}[^\d]{0,10}退款""", Direction.IN),
        rule("wechat.pay.to", WX, """向${NAME}(?:付款|转账)\s*{YEN}{AMT}"""),
        rule("wechat.paid", WX, """(?:已支付|支付成功|支付金额)[:：]?\s*{YEN}{AMT}"""),
        rule("wechat.voucher", WX, """微信支付凭证.{0,30}?[¥￥]\s*{AMT}"""),

        // ---- Generic fallback: explicit pay verb + currency symbol ----
        rule("generic.paid.symbol", BOTH, """(?:已支付|支付成功|付款成功|消费)[^\d¥￥]{0,12}[¥￥]\s*{AMT}"""),
    )

    /** Fills in the merchant when the main rule did not capture one. */
    private val merchantExtractors: List<Regex> = listOf(
        Regex("""(?:商户|商家|收款方|付款方|交易对象|对方)[:：]\s*(?<merchant>[^\s,，。]{1,24})"""),
        Regex("""向(?<merchant>[^\s,，。:：¥￥]{1,24}?)(?:付款|转账|支付)"""),
        Regex("""在(?<merchant>[^\s,，。:：¥￥]{1,24}?)(?:消费|支付)"""),
        Regex("""[¥￥]\d[\d,]*(?:\.\d{1,2})?\s*[·•|｜-]\s*(?<merchant>[^\s,，。]{1,24})"""),
    )

    private val adKeywords = listOf(
        "优惠", "立减", "满减", "抽奖", "限时", "福利", "领取", "领券", "红包雨", "集福", "补贴", "活动", "特价", "折扣",
        "推荐", "邀请", "试用", "免费", "月度账单", "账单已出", "年度账单", "可用额度", "提额", "奖励", "签到", "到店", "抢购",
    )

    /** Phrases that prove a real transaction even when marketing words are also present. */
    private val strongPayment = Regex("""付款成功|成功付款|已支付|支付成功|的支出|的收入|收款到账|已收款|成功收款|向你转账""")

    private val relatedWords = listOf("支付", "付款", "收款", "红包", "转账", "到账", "支出", "收入", "退款", "消费", "¥", "￥", "元")

    fun normalize(title: String, text: String): String =
        "$title $text".replace(Regex("[\\u200b-\\u200f\\u00a0\\ufeff]"), " ").replace(Regex("\\s+"), " ").trim()

    fun isPaymentRelated(title: String, text: String): Boolean {
        val t = normalize(title, text)
        return relatedWords.any { t.contains(it) }
    }

    fun parse(pkg: String, title: String, text: String): ParseResult {
        if (pkg !in PaymentApps.all) return ParseResult.Irrelevant
        val t = normalize(title, text)
        if (relatedWords.none { t.contains(it) }) return ParseResult.Irrelevant
        if (!strongPayment.containsMatchIn(t)) {
            adKeywords.firstOrNull { t.contains(it) }?.let { return ParseResult.Ignored("营销通知（$it）") }
        }
        for (rule in rules) {
            if (pkg !in rule.packages) continue
            val m = rule.pattern.find(t) ?: continue
            val cents = Amounts.toCents(m.groups["amount"]?.value ?: continue) ?: continue
            if (cents <= 0) continue
            val merchant = groupOrNull(m, "merchant") ?: extractMerchant(t)
            return ParseResult.Parsed(ParsedPayment(cents, rule.direction, cleanMerchant(merchant), rule.id))
        }
        return ParseResult.Unparsed
    }

    private fun groupOrNull(m: MatchResult, name: String): String? =
        try { m.groups[name]?.value } catch (_: IllegalArgumentException) { null }

    private fun extractMerchant(t: String): String? =
        merchantExtractors.firstNotNullOfOrNull { it.find(t)?.groups?.get("merchant")?.value }

    private val noMerchant = setOf("支付宝", "微信", "微信支付", "你", "我")
    private val noMerchantPrefix = listOf("点击", "查看", "详情", "请", "成功", "已")

    private fun cleanMerchant(raw: String?): String? {
        val s = raw?.trim()?.trim('，', ',', '。', '.', '、', '！', '!', '（', '）', '(', ')', ' ')
        return s?.takeIf { m -> m.isNotEmpty() && m !in noMerchant && noMerchantPrefix.none { m.startsWith(it) } }?.take(24)
    }
}
