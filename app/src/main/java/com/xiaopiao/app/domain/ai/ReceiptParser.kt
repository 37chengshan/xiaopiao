package com.xiaopiao.app.domain.ai

import com.xiaopiao.app.domain.Cents
import java.util.Calendar
import java.util.TimeZone

/**
 * Reads a payment screenshot / paper receipt that was OCR'd **on this device** into the few numbers we
 * actually store: merchant, amount, time. Nothing here guesses categories, and text that carries no amount
 * produces no candidate at all — a wrong row is worse than no row.
 *
 * Written against real notification wording (微信支付 / 支付宝 / 超市小票). The UI shows the result as an
 * editable confirm card, so the user has the last word on every field.
 */
object ReceiptParser {

    data class Candidate(
        val merchant: String,
        val amountCents: Cents,
        /** Epoch millis when the text carried a timestamp; null means "use now". */
        val occurredAt: Long?,
        val note: String? = null,
    )

    private val amountWithLabel = Regex(
        "(合计|总计|应付|实付|实收|付款金额|支付金额|交易金额|订单金额|金额)\\s*[:：]?\\s*[¥￥]?\\s*(-?[0-9]{1,7}(?:\\.[0-9]{1,2})?)",
    )
    private val amountCurrency = Regex("[¥￥]\\s*(-?[0-9]{1,7}(?:\\.[0-9]{1,2})?)")
    private val amountMinus = Regex("(?:^|\\s)-([0-9]{1,7}\\.[0-9]{2})(?:\\s|$)")
    private val merchantWithLabel = Regex(
        // OCR puts the value after a colon, a space, or on the next line; a label glued to more text (商户单号) is not one.
        "(商户全称|商户名称|收款方全称|收款方|交易对方|对方名称|商户|店铺名称|店铺)(?:[ \\t]*[:：][ \\t]*|[ \\t]*\\n[ \\t]*|[ \\t]+)([^\\n]{1,24})",
    )
    private val transferMerchant = Regex("转账\\s*[-—]\\s*(?:来自|给)?\\s*([^\\s，,。]{1,20})")
    private val timePattern = Regex(
        "([0-9]{4})[-/年]([0-9]{1,2})[-/月]([0-9]{1,2})日?\\s*([0-9]{1,2}):([0-9]{2})(?::([0-9]{2}))?",
    )
    private val refundWords = listOf("已退款", "退款成功", "交易关闭", "已关闭", "支付失败", "未支付", "撤销")

    /** Splits a multi-page OCR string on payment markers; falls back to the whole text as one block. */
    private fun blocks(text: String): List<String> {
        val markers = listOf("支付成功", "交易成功", "付款成功", "转账", "账单详情", "订单号", "合计", "总计")
        if (markers.none { text.contains(it) } && text.count { it == '\n' } < 2) return listOf(text)
        val lines = text.lines()
        val starts = mutableListOf(0)
        lines.forEachIndexed { index, line ->
            if (index > 0 && (line.contains("账单详情") || line.contains("订单号") ||
                    (line.contains("支付成功") && index > 3))
            ) {
                starts += index
            }
        }
        if (starts.size == 1) return listOf(text)
        return starts.mapIndexed { i, start ->
            val end = starts.getOrNull(i + 1) ?: lines.size
            lines.subList(start, end).joinToString("\n")
        }
    }

    fun parse(text: String, now: Long = 0L, zone: TimeZone = TimeZone.getDefault()): List<Candidate> {
        if (text.isBlank()) return emptyList()
        val cleaned = normalize(text)
        return blocks(cleaned)
            .mapNotNull { parseBlock(it, now, zone) }
            .distinctBy { it.amountCents to it.merchant }
            .take(6)
    }

    private fun parseBlock(block: String, now: Long, zone: TimeZone): Candidate? {
        if (refundWords.any { block.contains(it) }) return null
        val amount = amountOf(block) ?: return null
        if (amount <= 0L) return null
        val merchant = merchantOf(block) ?: return null
        return Candidate(
            merchant = merchant,
            amountCents = amount,
            occurredAt = timeOf(block, zone) ?: now.takeIf { it > 0L },
        )
    }

    /** Priority: an explicit 合计/付款金额 label, then any ¥, then a bare "-18.50" (支付宝 rows). */
    private fun amountOf(block: String): Cents? {
        amountWithLabel.find(block)?.groupValues?.get(2)?.let(::toCents)?.let { return it }
        amountCurrency.find(block)?.groupValues?.get(1)?.let(::toCents)?.let { return it }
        amountMinus.find(block)?.groupValues?.get(1)?.let(::toCents)?.let { return it }
        return null
    }

    private fun merchantOf(block: String): String? {
        merchantWithLabel.find(block)?.groupValues?.get(2)?.let { cleanMerchant(it) }?.let { return it }
        transferMerchant.find(block)?.groupValues?.get(1)?.let { cleanMerchant(it) }?.let { return it }
        // Fall back to the first line that looks like a shop name rather than a label or a number.
        val skip = listOf("支付成功", "交易成功", "付款成功", "账单详情", "微信支付", "支付宝", "订单号", "交易号", "金额", "合计", "总计")
        return block.lines()
            .map { it.trim() }
            .firstOrNull { line ->
                line.length in 2..20 && skip.none { line.contains(it) } &&
                    line.none { it.isDigit() || it == '¥' || it == '￥' } && !line.isEmpty()
            }
            ?.let(::cleanMerchant)
    }

    private fun cleanMerchant(raw: String): String? {
        val cut = raw.substringBefore('，').substringBefore(',').substringBefore('（').trim()
        if (cut.isEmpty()) return null
        if (cut.length > 20) return cut.take(20)
        return cut
    }

    private fun timeOf(block: String, zone: TimeZone): Long? {
        val m = timePattern.find(block) ?: return null
        val parts = (1..6).map { m.groupValues[it].ifEmpty { "0" }.toInt() }
        return Calendar.getInstance(zone).apply {
            clear()
            set(parts[0], parts[1] - 1, parts[2], parts[3], parts[4], parts[5])
        }.timeInMillis
    }

    /** "18.50" → 1850, "18" → 1800, "18.5" → 1850. Never through a Double. */
    internal fun toCents(text: String): Cents? {
        val raw = text.trim().removePrefix("+")
        val negative = raw.startsWith("-")
        val value = raw.removePrefix("-")
        if (value.isEmpty()) return null
        val dot = value.indexOf('.')
        val major = (if (dot >= 0) value.substring(0, dot) else value).toLongOrNull() ?: return null
        val fraction = if (dot >= 0) (value.substring(dot + 1) + "00").take(2).toLongOrNull() ?: 0L else 0L
        val cents = major * 100L + fraction
        return if (negative) -cents else cents
    }

    /** Full-width digits and separators are common in screenshots; fold them so one pattern covers both. */
    private fun normalize(text: String): String = buildString(text.length) {
        text.forEach { ch ->
            val folded = when (ch) {
                in '０'..'９' -> '0' + (ch - '０')
                '：' -> ':'
                '．' -> '.'
                else -> ch
            }
            append(folded)
        }
    }
}
