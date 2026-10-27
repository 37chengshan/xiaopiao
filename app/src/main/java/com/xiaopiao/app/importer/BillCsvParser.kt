package com.xiaopiao.app.importer

import com.xiaopiao.app.capture.Amounts
import com.xiaopiao.app.capture.MerchantCategorizer
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class BillFormat(val label: String) { ALIPAY("支付宝"), WECHAT("微信"), UNKNOWN("未识别") }

data class CsvParseResult(
    val format: BillFormat,
    val txns: List<Txn>,
    /** Rows that looked like data but could not be read (bad amount / time). */
    val failed: Int,
    /** Rows deliberately skipped: 不计收支, closed/failed payments, refunded expenses. */
    val skipped: Int,
)

/**
 * Parses exported Alipay / WeChat bill CSVs.
 * Column semantics follow deb-sig/double-entry-generator (Apache-2.0):
 *  - WeChat:  交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,...
 *  - Alipay:  交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,...
 *  - Only 收入/支出 rows are kept; "/" or 不计收支 are internal transfers and skipped.
 * Input must already be decoded text (UTF-8, BOM tolerated).
 */
object BillCsvParser {
    private val timeFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/M/d H:mm"),
    )
    private val badStatus = listOf("失败", "关闭", "取消", "等待", "未支付")

    fun parse(rawText: String, zone: ZoneId = ZoneId.systemDefault()): CsvParseResult {
        val rows = CsvReader.read(rawText.removePrefix("\uFEFF"))
        val headerIdx = rows.indexOfFirst { r -> r.any { cleanCell(it) == "交易时间" } && r.size >= 6 }
        if (headerIdx < 0) return CsvParseResult(BillFormat.UNKNOWN, emptyList(), 0, 0)
        val header = rows[headerIdx].map(::cleanCell)
        val format = when {
            "交易类型" in header -> BillFormat.WECHAT
            "交易分类" in header -> BillFormat.ALIPAY
            else -> return CsvParseResult(BillFormat.UNKNOWN, emptyList(), 0, 0)
        }
        val cols = Columns(header, format)
        var failed = 0
        var skipped = 0
        val txns = mutableListOf<Txn>()
        for (row in rows.drop(headerIdx + 1)) {
            if (row.size < 6) continue // footer / notes
            when (val r = convert(row.map(::cleanCell), cols, format, zone)) {
                is RowResult.Ok -> txns.add(r.txn)
                RowResult.Skip -> skipped++
                RowResult.Fail -> failed++
            }
        }
        return CsvParseResult(format, txns, failed, skipped)
    }

    private sealed interface RowResult {
        data class Ok(val txn: Txn) : RowResult
        data object Skip : RowResult
        data object Fail : RowResult
    }

    private class Columns(private val header: List<String>, format: BillFormat) {
        private fun find(vararg names: String): Int =
            names.firstNotNullOfOrNull { n -> header.indexOfFirst { it.startsWith(n) }.takeIf { it >= 0 } } ?: -1
        val time = find("交易时间")
        val kind = if (format == BillFormat.WECHAT) find("交易类型") else find("交易分类")
        val counterparty = find("交易对方")
        val goods = if (format == BillFormat.WECHAT) find("商品") else find("商品说明")
        val direction = find("收/支")
        val amount = find("金额")
        val status = if (format == BillFormat.WECHAT) find("当前状态") else find("交易状态")
        val order = if (format == BillFormat.WECHAT) find("交易单号") else find("交易订单号")
    }

    private fun convert(row: List<String>, c: Columns, format: BillFormat, zone: ZoneId): RowResult {
        fun cell(i: Int) = row.getOrNull(i).orEmpty()
        val dir = when (cell(c.direction)) {
            "支出" -> Direction.OUT
            "收入" -> Direction.IN
            else -> return RowResult.Skip
        }
        val status = cell(c.status)
        if (badStatus.any { status.contains(it) }) return RowResult.Skip
        if (dir == Direction.OUT && status.contains("退款")) return RowResult.Skip
        val cents = Amounts.toCents(cell(c.amount))?.takeIf { it > 0 } ?: return RowResult.Fail
        val at = parseTime(cell(c.time), zone) ?: return RowResult.Fail

        val counterparty = cell(c.counterparty).takeUnless { it.isEmpty() || it == "/" }
        val goods = cell(c.goods).takeUnless { it.isEmpty() || it == "/" }
        val merchant = (counterparty ?: goods ?: "未知商户").take(24)
        val note = goods?.takeIf { it != merchant }.orEmpty().take(60)
        val order = cell(c.order).takeUnless { it.isEmpty() || it == "/" }
        val key = "csv|${format.name}|" + (order ?: "$at|$cents|$merchant")
        return RowResult.Ok(
            Txn(
                amountCents = cents, direction = dir, merchant = merchant,
                category = categorize(format, cell(c.kind), merchant, note),
                occurredAt = at, source = Source.CSV, note = note, dedupeKey = key,
            ),
        )
    }

    private fun categorize(format: BillFormat, kind: String, merchant: String, note: String): Category {
        val byName = MerchantCategorizer.categorize(merchant, note)
        if (byName != Category.OTHER) return byName
        return when {
            kind.contains("红包") || kind.contains("转账") || kind.contains("人情") -> Category.SOCIAL
            format == BillFormat.ALIPAY -> alipayCategories.entries.firstOrNull { kind.contains(it.key) }?.value ?: Category.OTHER
            else -> Category.OTHER
        }
    }

    private val alipayCategories = linkedMapOf(
        "餐饮" to Category.MEAL, "交通" to Category.TRANSPORT, "出行" to Category.TRANSPORT, "爱车" to Category.TRANSPORT,
        "日用" to Category.GROCERY, "超市" to Category.GROCERY, "数码" to Category.SHOPPING, "服饰" to Category.SHOPPING,
        "充值缴费" to Category.HOUSING, "住房" to Category.HOUSING, "酒店旅游" to Category.FUN, "文化休闲" to Category.FUN,
        "医疗" to Category.HEALTH, "教育" to Category.LEARNING, "培训" to Category.LEARNING,
    )

    private fun parseTime(s: String, zone: ZoneId): Long? {
        for (f in timeFormats) {
            try { return LocalDateTime.parse(s, f).atZone(zone).toInstant().toEpochMilli() } catch (_: Exception) { }
        }
        return null
    }

    /** Removes BOM, padding spaces (Alipay pads every cell) and the tab prefix some exports add. */
    private fun cleanCell(s: String): String = s.replace("\uFEFF", "").trim().trim('\t').trim()
}
