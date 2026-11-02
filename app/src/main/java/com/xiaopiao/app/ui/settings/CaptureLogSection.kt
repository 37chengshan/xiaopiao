package com.xiaopiao.app.ui.settings

import com.xiaopiao.app.ui.tr
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.components.pressableClick
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.text.substringBefore("（").substringBefore(" ("), style = MaterialTheme.typography.labelMedium, color = toneColor, modifier = Modifier.weight(1f))
            // A miss can go to the developer in one tap — after the user sees exactly what will be sent.
            val account = com.xiaopiao.app.ui.account.LocalAccount.current
            if (e.result == "UNPARSED" && account != null) {
                var open by remember { mutableStateOf(false) }
                Text(
                    tr("一键反馈", "Report"),
                    style = MaterialTheme.typography.labelMedium, color = XpColors.Accent,
                    modifier = Modifier.clip(RoundedCornerShape(XpRadius.pill)).pressableClick { open = true }
                        .padding(horizontal = XpSpace.s, vertical = 4.dp),
                )
                if (open) FeedbackSheet(account, "${PaymentApps.label(e.packageName)} | ${e.title} | ${e.text}") { open = false }
            }
        }
    }
}
