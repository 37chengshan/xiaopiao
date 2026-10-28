package com.xiaopiao.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.capture.PaymentApps
import com.xiaopiao.app.data.CaptureLogEntry
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.ledger.GhostButton
import com.xiaopiao.app.ui.ledger.LedgerGrouping
import com.xiaopiao.app.ui.theme.ReceiptText
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/** Collapsible viewer for raw captured notifications and what the parser made of them (on device only). */
@Composable
fun CaptureLogSection(entries: List<CaptureLogEntry>, onClear: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    XpCard(modifier.animateContentSize(spring(dampingRatio = 0.8f))) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("通知记录", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                Text(
                    "看看小票读到了什么、哪些没认出来（最近 ${entries.size} 条，只存在本机）",
                    style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                )
            }
            Text(if (open) "收起" else "展开", style = MaterialTheme.typography.labelLarge, color = XpColors.Accent)
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = XpSpace.m)) {
                if (entries.isEmpty()) {
                    Text(
                        "还没有记录～付一笔款，这里就会出现啦。",
                        style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary,
                    )
                } else {
                    entries.take(40).forEach { LogRow(it) }
                    GhostButton("清空记录", color = XpColors.Stamp, onClick = onClear)
                }
            }
        }
    }
}

@Composable
private fun LogRow(e: CaptureLogEntry) {
    val label = CaptureLogFormat.describe(e.result)
    val toneColor: Color = when (label.tone) {
        LogTone.GOOD -> XpColors.Positive
        LogTone.WARN -> XpColors.Warning
        LogTone.NEUTRAL -> XpColors.InkSecondary
    }
    Column(Modifier.fillMaxWidth().padding(vertical = XpSpace.s)) {
        Text(
            "${PaymentApps.label(e.packageName)} · ${LedgerGrouping.shortDate(e.postedAt)} ${LedgerGrouping.clock(e.postedAt)}",
            style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
        )
        Text(
            listOf(e.title, e.text).filter { it.isNotBlank() }.joinToString(" | "),
            style = ReceiptText.copy(fontSize = 12.sp), maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 2.dp).background(XpColors.Canvas, RoundedCornerShape(6.dp)).padding(6.dp).fillMaxWidth(),
        )
        Text(label.text, style = MaterialTheme.typography.labelMedium, color = toneColor)
        HorizontalDivider(Modifier.padding(top = XpSpace.s), thickness = 0.5.dp, color = XpColors.Hairline)
    }
}
