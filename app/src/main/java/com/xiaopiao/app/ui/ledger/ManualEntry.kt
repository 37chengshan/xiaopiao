package com.xiaopiao.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import com.xiaopiao.app.capture.MerchantCategorizer
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.Segmented
import com.xiaopiao.app.ui.components.SelectCapsule
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.icon
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

/** Manual entry / edit form: direction, big tabular amount, category grid, merchant, day, keypad. */
@Composable
fun ManualEntryForm(initial: Txn?, now: Long, onSubmit: (Txn) -> Unit) {
    var amount by remember(initial) { mutableStateOf(initial?.let { AmountEntry.fromCents(it.amountCents) } ?: "") }
    var direction by remember(initial) { mutableStateOf(initial?.direction ?: Direction.OUT) }
    var category by remember(initial) { mutableStateOf(initial?.category ?: Category.MEAL) }
    var categoryTouched by remember(initial) { mutableStateOf(initial != null) }
    var merchant by remember(initial) { mutableStateOf(initial?.merchant.orEmpty()) }
    var day by remember(initial) { mutableStateOf(startOfDay(initial?.occurredAt ?: now)) }
    val haptics = rememberXpHaptics()
    val cents = AmountEntry.toCents(amount)

    Column(verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
        Segmented(listOf("支出", "收入"), if (direction == Direction.OUT) 0 else 1, {
            direction = if (it == 0) Direction.OUT else Direction.IN
        })
        MoneyText(
            AmountEntry.display(amount),
            modifier = Modifier.fillMaxWidth().padding(vertical = XpSpace.xs), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displayLarge,
            color = if (cents == 0L) XpColors.InkTertiary else if (direction == Direction.IN) XpColors.Positive else XpColors.Ink,
            prefix = if (direction == Direction.IN) "+" else "",
        )
        CategoryGrid(category) { category = it; categoryTouched = true }
        MerchantField(merchant) {
            merchant = it
            if (!categoryTouched) category = MerchantCategorizer.categorize(it)
        }
        DayChips(day, initial?.occurredAt, now) { day = it }
        Keypad { key -> amount = AmountEntry.press(amount, key) }
        XpButton(
            text = if (initial == null) "记下来" else "保存修改",
            onClick = {
                haptics.confirm()
                onSubmit(buildTxn(initial, now, cents, direction, merchant, category, day))
            },
            modifier = Modifier.fillMaxWidth(), enabled = cents > 0,
        )
    }
}

private fun buildTxn(
    initial: Txn?, now: Long, cents: Long, direction: Direction,
    merchant: String, category: Category, day: Long,
): Txn {
    val name = merchant.trim().ifEmpty { category.label }
    return if (initial == null) {
        Txn(
            amountCents = cents, direction = direction, merchant = name, category = category,
            occurredAt = if (day == startOfDay(now)) now else moveToDay(now, day), source = Source.MANUAL,
        )
    } else {
        val at = if (day == startOfDay(initial.occurredAt)) initial.occurredAt else moveToDay(initial.occurredAt, day)
        initial.copy(amountCents = cents, direction = direction, merchant = name, category = category, occurredAt = at)
    }
}

@Composable
private fun CategoryGrid(selected: Category, onSelect: (Category) -> Unit) {
    val haptics = rememberXpHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        Category.entries.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { c ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).pressableClick(haptic = false, scaleDown = 0.95f) {
                            haptics.select(); onSelect(c)
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconTile(c.icon(), size = 44.dp, selected = c == selected)
                        Text(
                            c.label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                            color = if (c == selected) XpColors.Ink else XpColors.InkSecondary,
                        )
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MerchantField(value: String, onChange: (String) -> Unit) {
    TextField(
        value = value, onValueChange = { onChange(it.take(30)) },
        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
        placeholder = { Text("商家 / 备注（可不填）", color = XpColors.InkTertiary) },
        colors = quietFieldColors(),
    )
}

@Composable
fun quietFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = XpColors.Fill, unfocusedContainerColor = XpColors.Fill,
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    cursorColor = XpColors.Accent, focusedTextColor = XpColors.Ink, unfocusedTextColor = XpColors.Ink,
)

@Composable
private fun DayChips(selected: Long, original: Long?, now: Long, onSelect: (Long) -> Unit) {
    val today = startOfDay(now)
    val days = buildList {
        add(today); add(today - DAY_MS); add(today - 2 * DAY_MS)
        original?.let { startOfDay(it) }?.takeIf { it !in this }?.let { add(it) }
    }.map { startOfDay(it) }.distinct()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        days.forEach { d -> SelectCapsule(LedgerGrouping.dayLabel(d, now), d == selected, { onSelect(d) }) }
    }
}

private val keyRows = listOf(
    listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", AmountEntry.BACKSPACE),
)

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val haptics = rememberXpHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keyRows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(12.dp))
                            .background(XpColors.Fill).pressableClick(haptic = false, scaleDown = 0.95f) { haptics.tick(); onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == AmountEntry.BACKSPACE) {
                            Icon(Icons.Outlined.Backspace, contentDescription = "删除", tint = XpColors.Ink, modifier = Modifier.size(22.dp))
                        } else {
                            Text(
                                key, color = XpColors.Ink,
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                            )
                        }
                    }
                }
            }
        }
    }
}
