package com.xiaopiao.app.ui.merchant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.merchant.MerchantAi
import com.xiaopiao.app.merchant.RevenueBook
import com.xiaopiao.app.merchant.RevenueEntry
import com.xiaopiao.app.ui.components.NavScaffold
import com.xiaopiao.app.ui.components.NavScrollNone
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.ReceiptHandwriting
import com.xiaopiao.app.ui.theme.ReceiptRow
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * 经营日报小票: a static paper report for one chosen day (not the user edition's print ceremony).
 * Same paper grammar — monospaced rows, dashed rules, a stamp when the numbers are demo — but it
 * appears as a page, is shareable as text, and never animates through a printer.
 */
@Composable
fun MerchantReceiptScreen(
    entries: List<RevenueEntry>,
    today: Long,
    initialDay: Long,
    onBack: () -> Unit,
    onRecordDay: (Long) -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberXpHaptics()
    var day by rememberSaveable { mutableStateOf(initialDay) }
    var copied by remember { mutableStateOf(false) }
    val entry = remember(entries, day) { RevenueBook.entryFor(entries, day) }
    val shareText = remember(entries, day) { receiptText(entries, day) }
    // A month of days, newest at the right — the receipt is for a chosen day, not only today.
    val days = remember(today) { (0..27).map { RevenueBook.plusDays(today, -27 + it) } }
    val scroll = rememberScrollState()
    val strip = rememberScrollState()

    LaunchedEffect(strip) { strip.scrollTo(strip.maxValue) }

    LaunchedEffect(copied) {
        if (copied) { delay(1800); copied = false }
    }

    NavScaffold(
        title = "经营小票",
        scroll = NavScrollNone,
        onBack = onBack,
        backLabel = "返回",
        demo = entry?.seeded == true,
    ) { top ->
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(top = top, bottom = XpSpace.xxl),
            verticalArrangement = Arrangement.spacedBy(XpSpace.l),
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(strip).padding(horizontal = XpSpace.l),
                horizontalArrangement = Arrangement.spacedBy(XpSpace.xs),
            ) {
                days.forEach { d ->
                    DayChip(
                        dayStart = d,
                        selected = d == day,
                        isToday = d == today,
                        modifier = Modifier.width(46.dp),
                    ) { day = d }
                }
            }

            Column(Modifier.padding(horizontal = XpSpace.l)) {
                PaperCard(entries = entries, day = day)
            }

            Column(Modifier.padding(horizontal = XpSpace.l)) {
                Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    XpButton(
                        "分享日报",
                        {
                            haptics.confirm()
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "小店经营日报 ${RevenueBook.isoDate(day)}")
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "分享经营日报").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    XpButton(
                        if (copied) "已复制" else "复制文字",
                        {
                            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            manager.setPrimaryClip(ClipData.newPlainText("经营日报", shareText))
                            haptics.select()
                            copied = true
                        },
                        modifier = Modifier.weight(1f),
                        style = XpButtonStyle.Secondary,
                    )
                }
                if (entry == null) {
                    Text(
                        "这天还没有记录。",
                        style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                        modifier = Modifier.padding(top = XpSpace.s),
                    )
                    XpButton(
                        "补记这一天",
                        { onRecordDay(day) },
                        modifier = Modifier.padding(top = XpSpace.s),
                        style = XpButtonStyle.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayChip(dayStart: Long, selected: Boolean, isToday: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = dayStart }
    Column(
        modifier.height(50.dp)
            .clip(RoundedCornerShape(XpRadius.m))
            .background(if (selected) XpColors.Accent else XpColors.Card)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${c.get(Calendar.DAY_OF_MONTH)}",
            fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) XpColors.OnAccent else XpColors.Ink,
        )
        Text(
            if (isToday) "今天" else RevenueBook.weekdayLabel(dayStart).removePrefix("周"),
            fontSize = 9.sp, lineHeight = 11.sp,
            color = if (selected) XpColors.OnAccent.copy(alpha = 0.85f) else XpColors.InkSecondary,
        )
    }
}

@Composable
private fun PaperCard(entries: List<RevenueEntry>, day: Long) {
    val entry = RevenueBook.entryFor(entries, day)
    val paper = XpColors.PaperInk
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.l)).background(XpColors.Paper)
            .padding(horizontal = XpSpace.xl, vertical = 22.dp),
    ) {
        Text(
            "小店经营日报",
            style = ReceiptRow.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
            color = paper,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            "${RevenueBook.isoDate(day)} ${RevenueBook.weekdayLabel(day)}",
            style = ReceiptRow.copy(fontSize = 11.sp, letterSpacing = 1.sp),
            color = paper.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        DashedRule()
        if (entry == null) {
            Text(
                "这天没有记录",
                style = ReceiptRow.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                color = paper,
                modifier = Modifier.padding(vertical = XpSpace.m),
            )
        } else {
            Text(
                RevenueBook.money(entry.revenueCents),
                style = ReceiptRow.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                color = paper,
                modifier = Modifier.padding(top = XpSpace.s),
            )
            Text("当天营业额", style = ReceiptRow.copy(fontSize = 11.sp), color = paper.copy(alpha = 0.55f))
            Spacer(Modifier.height(XpSpace.m))
            PaperRow("订单", if (entry.orders > 0) "${entry.orders} 单" else "未填")
            PaperRow("客单价", if (entry.averageTicketCents > 0) RevenueBook.money(entry.averageTicketCents) else "订单未填，算不出")
            PaperRow("本周", RevenueBook.money(RevenueBook.windowTotal(entries, RevenueBook.weekStart(day), day)))
        }
        DashedRule()
        Text(
            MerchantAi.receiptLine(entries, day),
            style = ReceiptHandwriting, color = paper,
            modifier = Modifier.padding(vertical = XpSpace.s),
        )
        Text(
            "数字来自本机营收记录",
            style = ReceiptRow.copy(fontSize = 10.sp, letterSpacing = 0.5.sp),
            color = paper.copy(alpha = 0.5f),
        )
        DashedRule()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("本机生成 · 不上传", style = ReceiptRow.copy(fontSize = 10.sp, letterSpacing = 0.5.sp), color = paper.copy(alpha = 0.45f))
            Text("非正式票据", style = ReceiptRow.copy(fontSize = 10.sp, letterSpacing = 0.5.sp), color = paper.copy(alpha = 0.45f))
        }
        if (entry?.seeded == true) {
            Box(
                Modifier.fillMaxWidth().padding(top = XpSpace.l),
                contentAlignment = Alignment.CenterEnd,
            ) { DemoStamp() }
        }
    }
}

@Composable
private fun PaperRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = ReceiptRow.copy(fontSize = 12.sp), color = XpColors.PaperInk.copy(alpha = 0.55f))
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = ReceiptRow.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
            color = XpColors.PaperInk,
        )
    }
}

@Composable
private fun DashedRule() {
    val color = XpColors.PaperInk.copy(alpha = 0.28f)
    Canvas(Modifier.fillMaxWidth().padding(vertical = XpSpace.m).height(1.dp)) {
        drawLine(
            color, androidx.compose.ui.geometry.Offset(0f, 0f),
            androidx.compose.ui.geometry.Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
    }
}

@Composable
private fun DemoStamp() {
    val ink = XpColors.Stamp.copy(alpha = 0.85f)
    Box(Modifier.graphicsLayer { rotationZ = -8f }.padding(4.dp)) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                ink, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
            )
        }
        Text(
            "演示数据",
            style = ReceiptRow.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp),
            color = ink,
            modifier = Modifier.padding(horizontal = XpSpace.m + 2.dp, vertical = 3.dp),
        )
    }
}

/** The shareable text version of the paper — what a shop owner would paste into a group chat. */
private fun receiptText(entries: List<RevenueEntry>, day: Long): String {
    val entry = RevenueBook.entryFor(entries, day)
    val sb = StringBuilder()
    sb.appendLine("小店经营日报 · ${RevenueBook.isoDate(day)} ${RevenueBook.weekdayLabel(day)}")
    if (entry == null) {
        sb.appendLine("这天没有记录")
    } else {
        sb.appendLine("营业额 ${RevenueBook.money(entry.revenueCents)}")
        if (entry.orders > 0) {
            sb.appendLine("订单 ${entry.orders} 单 · 客单价 ${RevenueBook.money(entry.averageTicketCents)}")
        }
        if (entry.note.isNotBlank()) sb.appendLine("备注：${entry.note}")
    }
    sb.appendLine(MerchantAi.receiptLine(entries, day))
    sb.append("本机生成 · 仅记录（非正式票据）")
    if (entry?.seeded == true) sb.append("\n【演示数据】")
    return sb.toString()
}
