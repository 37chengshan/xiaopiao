package com.xiaopiao.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.components.Segmented
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import java.util.Calendar

/** Manual form: keypad amount, category grid, optional merchant, 今天/昨天, then 「记下」. */
@Composable
internal fun ManualRecordPane(onSubmit: (Txn) -> Unit) {
    val openedAt = remember { System.currentTimeMillis() }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.MEAL) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var yesterday by rememberSaveable { mutableStateOf(false) }
    val cents = RecordAmount.toCents(amount)
    val haptics = rememberXpHaptics()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = XpSpace.l, end = XpSpace.l, bottom = XpSpace.xl),
        verticalArrangement = Arrangement.spacedBy(XpSpace.m),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = XpSpace.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("记一笔", style = MaterialTheme.typography.headlineSmall, color = XpColors.Ink)
            Pill("手动")
        }

        Segmented(
            options = listOf("今天", "昨天"),
            selected = if (yesterday) 1 else 0,
            onSelect = { yesterday = it == 1 },
        )

        MoneyText(
            RecordAmount.display(amount),
            modifier = Modifier.fillMaxWidth().padding(vertical = XpSpace.xs),
            style = MaterialTheme.typography.displayLarge,
            color = if (cents == 0L) XpColors.InkTertiary else XpColors.Ink,
            textAlign = TextAlign.Center,
        )

        Keypad { key -> amount = RecordAmount.press(amount, key) }

        Text(
            "分类",
            style = MaterialTheme.typography.bodySmall,
            color = XpColors.InkSecondary,
            modifier = Modifier.padding(top = XpSpace.xs),
        )
        CategoryGrid(category) {
            category = it
            haptics.select()
        }

        TextField(
            value = merchant,
            onValueChange = { merchant = it.take(30) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(XpRadius.m),
            placeholder = { Text("商户 / 备注（可不填）", color = XpColors.InkTertiary) },
            colors = recordFieldColors(),
        )

        XpButton(
            text = "记下",
            onClick = {
                haptics.confirm()
                onSubmit(
                    Txn(
                        amountCents = cents,
                        direction = Direction.OUT,
                        merchant = merchant.trim().ifBlank { category.label },
                        category = category,
                        occurredAt = if (yesterday) yesterdayStart(openedAt) else openedAt,
                        source = Source.MANUAL,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = cents > 0L,
        )
    }
}

@Composable
private fun CategoryGrid(selected: Category, onSelect: (Category) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        Category.entries.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { category ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(XpRadius.s))
                            .pressableClick(haptic = false, scaleDown = 0.95f) { onSelect(category) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CategoryEmojiTile(category, selected = category == selected)
                        Text(
                            category.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 3.dp),
                            color = if (category == selected) XpColors.Ink else XpColors.InkSecondary,
                        )
                    }
                }
                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryEmojiTile(category: Category, selected: Boolean) {
    val shape = RoundedCornerShape(XpRadius.m)
    // Selection is a fill change + a spring pop, never a border (no 框框).
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(selected) {
        if (selected) { pop.snapTo(0.9f); pop.animateTo(1f, androidx.compose.animation.core.spring(0.5f, 420f)) }
    }
    Box(
        Modifier
            .size(42.dp)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(shape)
            .background(if (selected) XpColors.Primary else XpColors.Fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(category.emoji, fontSize = 20.sp)
    }
}

private val keyRows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(".", "0", RecordAmount.BACKSPACE),
)

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val haptics = rememberXpHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keyRows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(XpRadius.m))
                            .background(XpColors.Fill)
                            .pressableClick(scaleDown = 0.95f) { haptics.tick(); onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == RecordAmount.BACKSPACE) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Backspace,
                                contentDescription = "删除",
                                tint = XpColors.Ink,
                                modifier = Modifier.size(22.dp),
                            )
                        } else {
                            Text(
                                key,
                                color = XpColors.Ink,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFeatureSettings = "tnum",
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun recordFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = XpColors.Fill,
    unfocusedContainerColor = XpColors.Fill,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = XpColors.Accent,
    focusedTextColor = XpColors.Ink,
    unfocusedTextColor = XpColors.Ink,
)

/** Start of yesterday without DST-sensitive millisecond arithmetic. */
private fun yesterdayStart(now: Long): Long = startOfDay(
    Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_MONTH, -1)
    }.timeInMillis,
)
