package com.xiaopiao.app.ui.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.merchant.RevenueBook
import com.xiaopiao.app.merchant.RevenueEntry
import com.xiaopiao.app.merchant.RevenueSource
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.record.RecordAmount
import com.xiaopiao.app.ui.record.recordFieldColors
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

private val keyRows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(".", "0", RecordAmount.BACKSPACE),
)

/**
 * One day's revenue: amount keypad, order count, an optional note. Saving replaces that day's row,
 * so a demo day becomes the owner's real number the moment they type it.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RevenueEntrySheet(
    dayStart: Long,
    existing: RevenueEntry?,
    onSave: (RevenueEntry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember(dayStart) { mutableStateOf(existing?.let { revenueText(it.revenueCents) } ?: "") }
    var orders by remember(dayStart) { mutableStateOf(existing?.orders ?: 0) }
    var note by remember(dayStart) { mutableStateOf(existing?.note ?: "") }
    // Editing a demo row makes it the owner's own number; an imported row stays marked as imported.
    val source = if (existing?.source == RevenueSource.CSV) RevenueSource.CSV else RevenueSource.MANUAL
    val cents = RecordAmount.toCents(amountText)
    val haptics = rememberXpHaptics()

    XpSheet(onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = XpSpace.l).padding(bottom = XpSpace.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (existing == null) bi("记营收", "Record revenue") else bi("修改营收", "Edit revenue"),
                        style = MaterialTheme.typography.headlineMedium, color = XpColors.Ink,
                    )
                    Text(
                        RevenueBook.dayLabel(dayStart),
                        style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                    )
                }
            }

            Text(
                "营业额（元）",
                style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                modifier = Modifier.padding(top = XpSpace.l),
            )
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = XpSpace.xs)) {
                Text(
                    "¥", style = MaterialTheme.typography.titleLarge, color = XpColors.InkSecondary,
                    modifier = Modifier.padding(bottom = 6.dp, end = 2.dp),
                )
                Text(
                    RecordAmount.display(amountText),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, fontFeatureSettings = "tnum"),
                    color = if (amountText.isEmpty()) XpColors.InkTertiary else XpColors.Ink,
                    maxLines = 1,
                )
            }
            Box(Modifier.fillMaxWidth().padding(top = XpSpace.s).height(1.dp).background(XpColors.Hairline))

            Row(
                Modifier.fillMaxWidth().padding(top = XpSpace.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("订单数", style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
                    Text(
                        if (orders > 0) "客单价 ${RevenueBook.money((cents + orders / 2) / orders)}" else "不清楚可以留 0",
                        style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary,
                    )
                }
                StepperButton("−") { if (orders > 0) { haptics.tick(); orders -= 1 } }
                Box(Modifier.size(width = 56.dp, height = 36.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "$orders",
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        color = XpColors.Ink,
                    )
                }
                StepperButton("+") { if (orders < 99_999) { haptics.tick(); orders += 1 } }
            }

            Text(
                "备注（可不填）",
                style = MaterialTheme.typography.labelMedium, color = XpColors.InkSecondary,
                modifier = Modifier.padding(top = XpSpace.l, bottom = XpSpace.xs),
            )
            TextField(
                value = note,
                onValueChange = { note = it.take(30) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(XpRadius.m),
                placeholder = { Text("例如：雨天、做了活动", color = XpColors.InkTertiary) },
                colors = recordFieldColors(),
            )

            RevenueKeypad(Modifier.padding(top = XpSpace.l)) { amountText = RecordAmount.press(amountText, it) }

            XpButton(
                text = "保存",
                onClick = {
                    haptics.confirm()
                    onSave(
                        RevenueEntry(
                            dayStart = dayStart,
                            revenueCents = cents,
                            orders = orders,
                            note = note.trim(),
                            source = source,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = XpSpace.l),
                enabled = amountText.isNotEmpty(),
            )
            if (existing != null) {
                // The confirmation is a window over a blurred page, owned by the shell (DESIGN §6).
                Text(
                    bi("删除这天的记录", "Delete this day"),
                    style = MaterialTheme.typography.labelMedium,
                    color = XpColors.Negative,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = XpSpace.l)
                        .pressableClick(scaleDown = 0.97f) { haptics.reject(); onDelete() }
                        .padding(vertical = XpSpace.s),
                )
            }
        }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(XpRadius.s)).background(XpColors.Fill)
            .pressableClick(scaleDown = 0.94f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
    }
}

@Composable
private fun RevenueKeypad(modifier: Modifier = Modifier, onKey: (String) -> Unit) {
    val haptics = rememberXpHaptics()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keyRows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(XpRadius.m))
                            .background(XpColors.Fill)
                            .pressableClick(scaleDown = 0.95f) { haptics.tick(); onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == RecordAmount.BACKSPACE) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Backspace, contentDescription = "删除",
                                tint = XpColors.Ink, modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Text(
                                key, color = XpColors.Ink,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 22.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum",
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "1280.5" for an edited row — keeps the amount exact without a float. */
private fun revenueText(cents: Long): String = "${cents / 100}" + centsTail(cents)

/** ".5" when the cents are not whole yuan, "" otherwise. */
private fun centsTail(cents: Long): String {
    val tail = cents % 100
    return when {
        tail == 0L -> ""
        tail % 10 == 0L -> ".${tail / 10}"
        else -> ".%02d".format(tail)
    }
}
