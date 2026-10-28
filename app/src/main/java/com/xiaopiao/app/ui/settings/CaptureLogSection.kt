package com.xiaopiao.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.capture.PaymentApps
import com.xiaopiao.app.data.CaptureLogEntry
import com.xiaopiao.app.ui.ledger.LedgerGrouping
import com.xiaopiao.app.ui.theme.ReceiptText
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/** One captured notification and what the parser made of it (on device only). */
@Composable
fun LogRow(e: CaptureLogEntry) {
    val label = CaptureLogFormat.describe(e.result)
    val toneColor: Color = when (label.tone) {
        LogTone.GOOD -> XpColors.Positive
        LogTone.WARN -> XpColors.Warning
        LogTone.NEUTRAL -> XpColors.InkSecondary
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.m)) {
        Text(
            "${PaymentApps.label(e.packageName)} · ${LedgerGrouping.shortDate(e.postedAt)} ${LedgerGrouping.clock(e.postedAt)}",
            style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
        )
        Text(
            listOf(e.title, e.text).filter { it.isNotBlank() }.joinToString(" | "),
            style = ReceiptText.copy(fontSize = 12.sp, color = XpColors.Ink), maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth().background(XpColors.Fill, RoundedCornerShape(8.dp)).padding(8.dp),
        )
        Text(label.text, style = MaterialTheme.typography.labelMedium, color = toneColor)
    }
}
