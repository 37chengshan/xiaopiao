package com.xiaopiao.app.importer

import android.content.ContentResolver
import android.net.Uri
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.LedgerRepository
import com.xiaopiao.app.domain.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class ImportSummary(
    val format: BillFormat,
    val imported: Int,
    val duplicates: Int,
    val failed: Int,
    val skipped: Int,
    val error: String? = null,
)

/** Reads a bill file chosen through the Storage Access Framework and feeds it into the ledger. */
class CsvImporter(private val resolver: ContentResolver, private val ledger: LedgerRepository) {

    suspend fun import(uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        val bytes = try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            return@withContext failure("读取文件失败：${e.message ?: "未知错误"}")
        } ?: return@withContext failure("读取文件失败")
        importText(decode(bytes))
    }

    suspend fun importText(text: String): ImportSummary {
        val parsed = BillCsvParser.parse(text)
        if (parsed.format == BillFormat.UNKNOWN) return failure("没认出这份账单。请导入支付宝或微信导出的 CSV 账单")
        val notifKeys = ledger.observeAll().first()
            .filter { it.source == Source.NOTIFICATION }
            .mapTo(HashSet()) { nearKey(it.amountCents, it.direction, it.occurredAt) }
        var imported = 0
        var dup = 0
        for (t in parsed.txns) {
            val nearbyNotification = (-1..1).any { notifKeys.contains(nearKey(t.amountCents, t.direction, t.occurredAt + it * 60_000L)) }
            if (nearbyNotification || ledger.add(t) == null) dup++ else imported++
        }
        return ImportSummary(parsed.format, imported, dup, parsed.failed, parsed.skipped)
    }

    private fun nearKey(cents: Long, dir: Direction, at: Long) = "$cents|$dir|${at / 60_000L}"

    private fun failure(msg: String) = ImportSummary(BillFormat.UNKNOWN, 0, 0, 0, 0, msg)

    /** Bills are UTF-8 by spec, but older Alipay exports are GBK — fall back when UTF-8 is invalid. */
    private fun decode(bytes: ByteArray): String {
        val strict = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            strict.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("GBK"))
        }
    }
}
