package com.xiaopiao.app.ui.record

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.ui.components.icon
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import java.util.Calendar

/**
 * 记一笔 (v6, docs/APP-DESIGN.md §4.1 + the wallet reference): direction + date row, big amount with a
 * cursor, 5-column category grid, note pill, and a 4×4 keypad whose last row is 再记一笔 · 0 · . · 保存.
 *
 * [onSubmit] gets `keepOpen = true` from 再记一笔 (the sheet stays and clears) and `false` from 保存.
 */
@Composable
internal fun ManualRecordPane(onSubmit: (Txn, Boolean) -> Unit) {
    val openedAt = remember { System.currentTimeMillis() }
    var draft by remember { mutableStateOf(RecordDraft()) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var direction by remember { mutableStateOf(Direction.OUT) }
    var daysAgo by rememberSaveable { mutableIntStateOf(0) }
    var category by remember { mutableStateOf(Category.MEAL) }
    val haptics = rememberXpHaptics()
    val cents = RecordDraftMath.cents(draft)
    val canSave = cents > 0L

    fun save(keepOpen: Boolean) {
        val txn = RecordComposer.txn(
            draft = draft,
            direction = direction,
            category = category,
            merchant = merchant,
            at = offsetDayStart(openedAt, daysAgo),
        ) ?: return
        if (keepOpen) haptics.select() else haptics.confirm()
        onSubmit(txn, keepOpen)
        if (keepOpen) {
            draft = RecordDraft()
            merchant = ""
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = XpSpace.l),
        verticalArrangement = Arrangement.spacedBy(XpSpace.s),
    ) {
        DirectionAndDateRow(
            direction = direction,
            daysAgo = daysAgo,
            onDirection = { direction = it; haptics.select() },
            onDaysAgo = { daysAgo = it; haptics.select() },
        )

        AmountRow(draft = draft, cents = cents)

        Box(Modifier.fillMaxWidth().height(1.dp).background(XpColors.Hairline))

        CategoryGrid(category) { category = it; haptics.select() }

        NoteField(merchant) { merchant = it.take(30) }

        Keypad(
            canSave = canSave,
            onKey = { key -> draft = RecordDraftMath.press(draft, key) },
            onRepeat = { save(keepOpen = true) },
            onSave = { save(keepOpen = false) },
        )
    }
}

/** 支出 / 收入 on the left, the date pill on the right — one row, 32dp tall. */
@Composable
private fun DirectionAndDateRow(
    direction: Direction,
    daysAgo: Int,
    onDirection: (Direction) -> Unit,
    onDaysAgo: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = XpSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DirectionChip("支出", direction == Direction.OUT) { onDirection(Direction.OUT) }
        Spacer(Modifier.width(XpSpace.s))
        DirectionChip("收入", direction == Direction.IN) { onDirection(Direction.IN) }
        Spacer(Modifier.weight(1f))
        val label = when (daysAgo) {
            0 -> "今天"
            1 -> "昨天"
            else -> "前天"
        }
        Box(
            Modifier.height(32.dp).clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.Fill)
                .pressableClick(scaleDown = 0.96f) { onDaysAgo((daysAgo + 1) % 3) }
                .padding(horizontal = XpSpace.m),
            contentAlignment = Alignment.Center,
        ) {
            Text("$label ▾", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = XpColors.Ink)
        }
    }
}

@Composable
private fun DirectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) XpColors.Fill else XpColors.Canvas, tween(160, easing = XpEase.out), label = "dirBg")
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(XpRadius.pill)).background(bg)
            .pressableClick(scaleDown = 0.96f, onClick = onClick)
            .padding(horizontal = XpSpace.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) XpColors.Ink else XpColors.InkTertiary,
        )
    }
}

/** ¥ + the running expression + a blinking caret, over a hairline. */
@Composable
private fun AmountRow(draft: RecordDraft, cents: Long) {
    val caret = remember { Animatable(1f) }
    LaunchedEffect(draft.expression) {
        caret.snapTo(1f)
        while (true) {
            kotlinx.coroutines.delay(520)
            caret.animateTo(0f, tween(160))
            caret.animateTo(1f, tween(160))
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = XpSpace.m, bottom = XpSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("¥", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = XpColors.InkSecondary)
        Text(
            draft.expression,
            modifier = Modifier.padding(start = XpSpace.xs),
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (cents == 0L) XpColors.InkTertiary else XpColors.Ink,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
        Box(
            Modifier.padding(start = 2.dp).width(2.dp).height(26.dp)
                .graphicsLayer { alpha = caret.value }
                .background(XpColors.Accent),
        )
    }
}

/** 5 columns: 34dp tile + 12sp label; selection fills the tile with ink and flips the icon to canvas. */
@Composable
private fun CategoryGrid(selected: Category, onSelect: (Category) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(XpSpace.xs)) {
        Category.entries.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { category ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(XpRadius.s))
                            .pressableClick(haptic = false, scaleDown = 0.94f) { onSelect(category) }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CategoryTile(category, selected = category == selected)
                        Text(
                            category.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
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
private fun CategoryTile(category: Category, selected: Boolean) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0.9f)
            pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
        }
    }
    val bg by animateColorAsState(if (selected) XpColors.Primary else XpColors.Fill, tween(160, easing = XpEase.out), label = "tileBg")
    val fg by animateColorAsState(if (selected) XpColors.OnPrimary else XpColors.InkSecondary, tween(160, easing = XpEase.out), label = "tileFg")
    Box(
        Modifier.size(34.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .clip(RoundedCornerShape(10.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon(), contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
    }
}

/** Note pill: one line, no border, camera stays out until OCR exists. */
@Composable
private fun NoteField(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(XpRadius.pill))
            .background(XpColors.Fill).padding(horizontal = XpSpace.m),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = XpColors.Ink),
        cursorBrush = SolidColor(XpColors.Accent),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { }),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        "点击填写备注",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = XpColors.InkTertiary,
                    )
                }
                inner()
            }
        },
    )
}

private val keypadRows = listOf(
    listOf("7", "8", "9", RecordAmount.BACKSPACE),
    listOf("4", "5", "6", RecordDraftMath.MINUS),
    listOf("1", "2", "3", RecordDraftMath.PLUS),
)

/** 4×4 keypad, 49dp rows. Flat keys (no fills), one ink save button, one quiet 再记一笔. */
@Composable
private fun Keypad(
    canSave: Boolean,
    onKey: (String) -> Unit,
    onRepeat: () -> Unit,
    onSave: () -> Unit,
) {
    val haptics = rememberXpHaptics()
    Column(Modifier.fillMaxWidth().padding(top = XpSpace.xs)) {
        keypadRows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(49.dp), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { key ->
                    KeyCell(Modifier.weight(1f)) {
                        val isSymbol = key == RecordAmount.BACKSPACE ||
                            key == RecordDraftMath.PLUS || key == RecordDraftMath.MINUS
                        if (isSymbol) {
                            KeyGlyph(key, 20) { haptics.tick(); onKey(key) }
                        } else {
                            KeyLabel(key, 22, FontWeight.Medium) { haptics.tick(); onKey(key) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(49.dp), verticalAlignment = Alignment.CenterVertically) {
            KeyCell(Modifier.weight(1f)) {
                Box(
                    Modifier.fillMaxWidth().height(49.dp).pressableClick(enabled = canSave, scaleDown = 0.94f) {
                        haptics.select()
                        onRepeat()
                    },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        "再记一笔",
                        fontSize = 14.sp,
                        color = if (canSave) XpColors.Ink else XpColors.InkTertiary,
                    )
                }
            }
            KeyCell(Modifier.weight(1f)) { KeyLabel("0", 22, FontWeight.Medium) { haptics.tick(); onKey("0") } }
            KeyCell(Modifier.weight(1f)) { KeyLabel(".", 22, FontWeight.Medium) { haptics.tick(); onKey(".") } }
            Box(Modifier.weight(1f).padding(start = XpSpace.s), contentAlignment = Alignment.CenterEnd) {
                SaveButton(enabled = canSave, onSave = onSave)
            }
        }
    }
}

@Composable
private fun KeyCell(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.height(49.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun KeyLabel(text: String, size: Int, weight: FontWeight, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(49.dp).pressableClick(scaleDown = 0.94f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = size.sp, fontWeight = weight, color = XpColors.Ink, style = TextStyle(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun KeyGlyph(key: String, size: Int, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(49.dp).pressableClick(scaleDown = 0.94f, onClick = onClick),
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
            Text(key, fontSize = size.sp, color = XpColors.Ink)
        }
    }
}

/** 86×48, radius 12: ink solid when there is an amount, grey fill when there is not. */
@Composable
private fun SaveButton(enabled: Boolean, onSave: () -> Unit) {
    val bg by animateColorAsState(if (enabled) XpColors.Primary else XpColors.Fill, tween(160, easing = XpEase.out), label = "saveBg")
    val fg by animateColorAsState(if (enabled) XpColors.OnPrimary else XpColors.InkTertiary, tween(160, easing = XpEase.out), label = "saveFg")
    Box(
        Modifier.size(width = 86.dp, height = 48.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .pressableClick(enabled = enabled, scaleDown = 0.95f, haptic = true) { onSave() },
        contentAlignment = Alignment.Center,
    ) {
        Text("保存", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

/** Start of the day [daysAgo] days before [now], DST-safe through Calendar. */
internal fun offsetDayStart(now: Long, daysAgo: Int): Long {
    if (daysAgo <= 0) return now
    return startOfDay(
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_MONTH, -daysAgo)
        }.timeInMillis,
    )
}

/** Shared text-field colours for the record sheets (no underline, fill background, accent caret). */
@Composable
internal fun recordFieldColors(): androidx.compose.material3.TextFieldColors =
    androidx.compose.material3.TextFieldDefaults.colors(
        focusedContainerColor = XpColors.Fill,
        unfocusedContainerColor = XpColors.Fill,
        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        cursorColor = XpColors.Accent,
        focusedTextColor = XpColors.Ink,
        unfocusedTextColor = XpColors.Ink,
    )
