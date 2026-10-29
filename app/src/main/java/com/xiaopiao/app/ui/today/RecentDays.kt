package com.xiaopiao.app.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.ReceiptRow
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import java.util.Calendar

/** Horizontal carousel of mini receipts; tapping one reprints that day. */
@Composable
fun RecentDays(days: List<DaySummary>, selected: Long, today: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = XpSpace.l, vertical = XpSpace.s),
        horizontalArrangement = Arrangement.spacedBy(XpSpace.m),
    ) {
        items(days, key = { it.dayStart }) { d ->
            val idx = days.indexOf(d)
            MiniReceipt(d, d.dayStart == selected, d.dayStart == today, if (idx % 2 == 0) -1.2f else 1.2f) { onSelect(d.dayStart) }
        }
    }
}

@Composable
private fun MiniReceipt(d: DaySummary, selected: Boolean, isToday: Boolean, tilt: Float, onClick: () -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = d.dayStart }
    val label = if (isToday) "今天" else "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
    val empty = d.count == 0
    Box(
        Modifier
            .rotate(if (selected) 0f else tilt)
            .width(92.dp).height(128.dp)
            .pressableClick(onClick = onClick)
            .drawBehind {
                val path = paperPath(size.width, size.height, 9.dp.toPx(), 5.dp.toPx(), false)
                drawPath(path, XpColors.Paper)
                drawPath(path, if (selected) XpColors.Accent else XpColors.Hairline, style = Stroke(if (selected) 2.dp.toPx() else 0.8.dp.toPx()))
                val y = 44.dp.toPx()
                drawLine(
                    XpColors.InkTertiary, Offset(10.dp.toPx(), y), Offset(size.width - 10.dp.toPx(), y), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = ReceiptRow.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
            Text(if (empty) "💤" else d.topEmoji ?: "🧾", fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (empty) "无消费" else ReceiptLayout.money(d.totalOutCents),
                style = ReceiptRow.copy(fontSize = if (empty) 12.sp else 14.sp, fontWeight = FontWeight.Bold, color = if (empty) XpColors.InkSecondary else XpColors.PaperInk),
                maxLines = 1,
            )
            Text(if (empty) "钱包休眠" else "${d.count} 笔", style = ReceiptRow.copy(fontSize = 10.sp, color = XpColors.InkSecondary))
        }
    }
}
