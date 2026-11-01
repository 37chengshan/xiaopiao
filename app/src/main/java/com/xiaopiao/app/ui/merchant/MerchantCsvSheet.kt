package com.xiaopiao.app.ui.merchant

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.merchant.RevenueBook
import com.xiaopiao.app.merchant.RevenueCsv
import com.xiaopiao.app.merchant.RevenueCsvPreview
import com.xiaopiao.app.merchant.RevenueEntry
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonSize
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.tr
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val mimeTypes = arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")
private const val MAX_BYTES = 2 * 1024 * 1024

/**
 * CSV import for the revenue book: pick a file, see exactly what would land, then import. Days that
 * already have a row are listed as duplicates and skipped — a second import never doubles a day.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RevenueCsvSheet(
    entries: List<RevenueEntry>,
    today: Long,
    onImport: (List<RevenueEntry>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Demo rows are placeholders: a real import for the same day replaces them instead of being skipped.
    val existingDays = remember(entries) {
        RevenueBook.normalize(entries).filterNot { it.seeded }.map { it.dayStart }.toSet()
    }
    var preview by remember { mutableStateOf<RevenueCsvPreview?>(null) }
    var fileName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    suspend fun read(uri: Uri) {
        loading = true
        error = null
        preview = null
        runCatching {
            withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error(tr("读不到这个文件", "This file could not be read"))
                require(bytes.size <= MAX_BYTES) { tr("文件太大了（上限 2MB）", "File is too large (2MB max)") }
                val utf8 = String(bytes, Charsets.UTF_8)
                // Excel on Chinese Windows still exports GBK; fall back only when UTF-8 clearly failed.
                if (utf8.contains('\uFFFD')) String(bytes, charset("GB18030")) else utf8
            }
        }.onSuccess { text ->
            preview = RevenueCsv.parse(text, today, existingDays)
            loading = false
        }.onFailure {
            error = it.message ?: tr("这个文件读不了", "This file cannot be opened")
            loading = false
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileName = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
            scope.launch { read(uri) }
        }
    }

    XpSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = XpSpace.l).padding(bottom = XpSpace.xl),
        ) {
            Text(tr("导入营收 CSV", "Import revenue CSV"), style = MaterialTheme.typography.headlineMedium, color = XpColors.Ink)
            Text(
                tr("每天一行，列顺序：日期、营收、订单（订单可不填）。", "One row per day: date, revenue, orders (orders optional)."),
                style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Box(
                Modifier.fillMaxWidth().padding(top = XpSpace.m).clip(RoundedCornerShape(XpRadius.m))
                    .background(XpColors.Fill).padding(XpSpace.m),
            ) {
                Text(
                    "2026-10-27,1280.5,86\n2026/10/28,\"1,380.00\",92",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = XpColors.InkSecondary,
                )
            }

            val current = preview
            if (current == null) {
                Column(
                    Modifier.fillMaxWidth().padding(top = XpSpace.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(XpRadius.l)).background(XpColors.Fill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.UploadFile, contentDescription = null, tint = XpColors.Ink, modifier = Modifier.size(26.dp))
                    }
                    Text(
                        if (loading) tr("正在读文件…", "Reading the file…") else error ?: tr("选一个 CSV 文件，先看预览再导入", "Pick a CSV file, check the preview, then import"),
                        style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary,
                        modifier = Modifier.padding(top = XpSpace.m),
                    )
                    XpButton(
                        tr("选择 CSV 文件", "Choose a CSV file"),
                        { launcher.launch(mimeTypes) },
                        modifier = Modifier.padding(top = XpSpace.m),
                        enabled = !loading,
                    )
                }
            } else {
                if (fileName.isNotEmpty()) {
                    Text(
                        fileName, style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary,
                        modifier = Modifier.padding(top = XpSpace.l),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = XpSpace.s),
                    horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
                ) {
                    PreviewCount(tr("可导入", "Importable"), tr("${current.importDays} 天", "${current.importDays} d"), XpColors.Positive, Modifier.weight(1f))
                    PreviewCount(tr("重复", "Duplicate"), tr("${current.duplicates.size} 天", "${current.duplicates.size} d"), XpColors.Warning, Modifier.weight(1f))
                    PreviewCount(tr("认不出", "Unreadable"), tr("${current.invalid.size} 行", "${current.invalid.size} rows"), XpColors.Negative, Modifier.weight(1f))
                }
                Text(
                    tr("可导入合计 ${RevenueBook.money(current.importTotalCents)}", "Importable total ${RevenueBook.money(current.importTotalCents)}"),
                    style = MaterialTheme.typography.labelMedium, color = XpColors.Ink,
                    modifier = Modifier.padding(top = XpSpace.m),
                )
                current.importable.take(6).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(top = XpSpace.s), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            RevenueBook.shortDate(row.dayStart!!), style = MaterialTheme.typography.bodyMedium,
                            color = XpColors.Ink,
                        )
                        Text(
                            "  ${RevenueBook.weekdayLabel(row.dayStart)}",
                            style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (row.orders > 0) tr("${row.orders} 单", "${row.orders} orders") else tr("订单未填", "No orders"),
                            style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
                            modifier = Modifier.padding(end = XpSpace.s),
                        )
                        Text(
                            RevenueBook.money(row.revenueCents!!),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = XpColors.Ink,
                        )
                    }
                }
                if (current.importDays > 6) {
                    Text(
                        tr("还有 ${current.importDays - 6} 天没列出来", "…and ${current.importDays - 6} more days not listed"),
                        style = MaterialTheme.typography.labelSmall, color = XpColors.InkTertiary,
                        modifier = Modifier.padding(top = XpSpace.xs),
                    )
                }
                if (current.duplicates.isNotEmpty()) {
                    Text(
                        tr(
                            "重复的 ${current.duplicates.size} 天你已经记过，会跳过，不会覆盖。",
                            "${current.duplicates.size} of these days already have entries; they are skipped, never overwritten.",
                        ),
                        style = MaterialTheme.typography.labelSmall, color = XpColors.Warning,
                        modifier = Modifier.padding(top = XpSpace.s),
                    )
                }
                current.invalid.take(5).forEach { row ->
                    Text(
                        tr("第 ${row.line} 行 · ${row.error}", "Line ${row.line} · ${row.error}"),
                        style = MaterialTheme.typography.labelSmall, color = XpColors.Negative,
                        modifier = Modifier.padding(top = XpSpace.xs),
                    )
                }
                if (current.invalid.size > 5) {
                    Text(
                        tr("还有 ${current.invalid.size - 5} 行认不出", "…and ${current.invalid.size - 5} more lines could not be read"),
                        style = MaterialTheme.typography.labelSmall, color = XpColors.Negative,
                        modifier = Modifier.padding(top = XpSpace.xs),
                    )
                }

                XpButton(
                    tr("导入 ${current.importDays} 天", "Import ${current.importDays} days"),
                    { onImport(RevenueCsv.entries(current)) },
                    modifier = Modifier.fillMaxWidth().padding(top = XpSpace.l),
                    enabled = current.importDays > 0,
                )
                Row(Modifier.fillMaxWidth().padding(top = XpSpace.s), horizontalArrangement = Arrangement.Center) {
                    XpButton(
                        tr("换一个文件", "Choose another file"), { launcher.launch(mimeTypes) },
                        style = XpButtonStyle.Secondary, size = XpButtonSize.Small,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCount(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(XpRadius.m)).background(XpColors.Card).padding(XpSpace.m),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = color)
    }
}
