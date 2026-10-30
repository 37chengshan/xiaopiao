package com.xiaopiao.app.ui.record

import com.xiaopiao.app.ui.components.LocalSheetGlass
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.domain.CategoryDefaults
import com.xiaopiao.app.domain.CategoryDef
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.ui.category.CategoryTile
import com.xiaopiao.app.ui.components.XpToastHost
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberXpToast
import com.xiaopiao.app.ui.ledger.LedgerCalendarSheet
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpMotion
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import java.util.Calendar

/**
 * 记一笔, 1:1 with the wallet reference `041431` (docs/APP-DESIGN.md §4.1): × close, 支出/收入 segmented
 * on the left with 今天 ▾ / 19:14 ▾ on the right, ¥ + 30sp amount over a hairline, a 5-column category
 * grid (coloured icons, ＋ 自定义 at the end of the grid), the note pill with the camera slot, and a
 * 4×4 keypad whose last row is 再记一笔 · 0 · . · 保存.
 *
 * [onSubmit] gets `keepOpen = true` from 再记一笔 (the sheet stays and clears) and `false` from 保存.
 *
 * Editing a row is the same pane (docs/DESIGN.md §7): [editing] pre-fills every field, 再记一笔 becomes
 * 删除 ([onDelete]) and 保存 submits the row with its id kept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualRecordPane(
    defs: List<CategoryDef>,
    onSubmit: (Txn, Boolean) -> Unit,
    onClose: () -> Unit = {},
    onOpenCategoryManager: () -> Unit = {},
    editing: Txn? = null,
    onDelete: () -> Unit = {},
    /** A new row pre-filled from voice: same fields as [editing], but saving adds instead of updating. */
    prefill: Txn? = null,
) {
    val seed = editing ?: prefill
    val openedAt = remember { System.currentTimeMillis() }
    val opened = remember(openedAt) {
        Calendar.getInstance().apply { timeInMillis = seed?.occurredAt ?: openedAt }
    }
    var draft by remember {
        mutableStateOf(RecordDraft(amount = seed?.takeIf { it.amountCents > 0 }?.let { draftAmountOf(it.amountCents) }.orEmpty()))
    }
    var merchant by rememberSaveable { mutableStateOf(seed?.merchant.orEmpty()) }
    var direction by remember { mutableStateOf(seed?.direction ?: Direction.OUT) }
    var dayStart by remember { mutableStateOf(startOfDay(seed?.occurredAt ?: openedAt)) }
    var hour by rememberSaveable { mutableIntStateOf(opened.get(Calendar.HOUR_OF_DAY)) }
    var minute by rememberSaveable { mutableIntStateOf(opened.get(Calendar.MINUTE)) }
    var chosenId by rememberSaveable {
        // A voice line without an amount carries no real category guess: leave the grid on its default.
        mutableStateOf(seed?.takeIf { editing != null || it.amountCents > 0 }?.let { it.categoryId.ifBlank { CategoryDefaults.idFor(it.category) } }.orEmpty())
    }
    var calendarOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }
    val toast = rememberXpToast()
    val haptics = rememberXpHaptics()

    // The grid only offers what fits the current direction; hidden categories never show here.
    val offered = remember(defs, direction) {
        defs.filter { !it.hidden && it.fits(direction) }.ifEmpty { CategoryDefaults.all }
    }
    val category = offered.firstOrNull { it.id == chosenId } ?: offered.first()
    val cents = RecordDraftMath.cents(draft)
    val canSave = cents > 0L

    fun save(keepOpen: Boolean) {
        val txn = RecordComposer.txn(
            draft = draft,
            direction = direction,
            category = category,
            merchant = merchant,
            at = atDayAndTime(dayStart, hour, minute),
        ) ?: return
        if (keepOpen) haptics.select() else haptics.confirm()
        // An edit keeps the row's identity and capture metadata; only what the pane shows changes.
        onSubmit(
            editing?.copy(
                amountCents = txn.amountCents, direction = txn.direction, merchant = txn.merchant,
                category = txn.category, categoryId = txn.categoryId, occurredAt = txn.occurredAt,
            ) ?: txn,
            keepOpen,
        )
        if (keepOpen) {
            draft = RecordDraft()
            merchant = ""
        }
    }

    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page)) {
            // × on its own line, top right (reference: 24dp icon, 16dp from the sheet top).
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(32.dp).clip(CircleShape).pressableClick(haptic = false, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = XpColors.Ink, modifier = Modifier.size(22.dp))
                }
            }

            DirectionAndTimeRow(
                direction = direction,
                onDirection = { direction = it; haptics.select() },
                dayLabel = dayLabelOf(dayStart, openedAt),
                timeLabel = "%02d:%02d".format(hour, minute),
                onDate = { calendarOpen = true },
                onTime = { timeOpen = true },
            )

            AmountRow(draft = draft, cents = cents)

            // Reference: a 4dp rounded grey bar under the amount, not a hairline.
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.Fill))

            CategoryArea(
                offered = offered,
                selected = category.id,
                onSelect = { chosenId = it; haptics.select() },
                onCustom = onOpenCategoryManager,
            )
        }
        // Reference: from the note row down, the sheet turns grey edge to edge.
        Column(
            // Over a blurred page the grey keeps the sheet's glass rather than going solid (DESIGN §3).
            Modifier.fillMaxWidth().background(XpColors.CanvasGrouped.copy(alpha = if (LocalSheetGlass.current) 0.55f else 1f))
                .padding(horizontal = XpSpace.page).padding(bottom = XpSpace.xs),
        ) {
            NoteRow(
                value = merchant,
                onValueChange = { merchant = it.take(30) },
                onCamera = { toast.show("拍照记账即将支持") },
            )

            Keypad(
                canSave = canSave,
                onKey = { key -> draft = RecordDraftMath.press(draft, key) },
                onRepeat = { if (editing != null) onDelete() else save(keepOpen = true) },
                onSave = { save(keepOpen = false) },
                editing = editing != null,
            )
        }
        }
        // Feedback sits under the × so it never covers the keys (§7.7 in-sheet placement).
        XpToastHost(toast, Modifier.align(Alignment.TopCenter).padding(top = 40.dp))
    }

    if (calendarOpen) {
        LedgerCalendarSheet(
            anchor = dayStart,
            selected = dayStart,
            today = startOfDay(openedAt),
            onPick = { dayStart = startOfDay(it); calendarOpen = false },
            onDismiss = { calendarOpen = false },
        )
    }
    if (timeOpen) TimePickerSheet(
        hour = hour,
        minute = minute,
        onPick = { h, m -> hour = h; minute = m; timeOpen = false },
        onDismiss = { timeOpen = false },
    )
}

/** 支出 / 收入 segmented on the left, 今天 ▾ and 19:14 ▾ on the right (reference: 30dp pills). */
@Composable
private fun DirectionAndTimeRow(
    direction: Direction,
    onDirection: (Direction) -> Unit,
    dayLabel: String,
    timeLabel: String,
    onDate: () -> Unit,
    onTime: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = XpSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.s), verticalAlignment = Alignment.CenterVertically) {
            DirectionChip("支出", direction == Direction.OUT) { onDirection(Direction.OUT) }
            DirectionChip("收入", direction == Direction.IN) { onDirection(Direction.IN) }
        }
        Spacer(Modifier.weight(1f))
        PillButton(dayLabel, onDate)
        Spacer(Modifier.width(XpSpace.xs))
        PillButton(timeLabel, onTime)
    }
}

@Composable
private fun DirectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) XpColors.AccentSoft else XpColors.Fill, tween(160, easing = XpEase.out), label = "dirBg")
    Box(
        Modifier.height(30.dp).clip(RoundedCornerShape(XpRadius.pill)).background(bg)
            .pressableClick(scaleDown = 0.96f, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = if (selected) XpColors.Accent else XpColors.InkSecondary,
            maxLines = 1,
        )
    }
}

/** 30dp grey pill with a caret — the reference's 今天 ▾ / 19:14 ▾. */
@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.height(30.dp).clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.Fill)
            .pressableClick(haptic = false, scaleDown = 0.96f, onClick = onClick)
            .padding(horizontal = XpSpace.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$text ▾",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
            color = XpColors.Ink,
            maxLines = 1,
        )
    }
}

/** ¥ + the running expression + a blinking caret, over a hairline. */
@Composable
private fun AmountRow(draft: RecordDraft, cents: Long) {
    val reduced = rememberReducedMotion()
    val caret = remember { Animatable(1f) }
    // Every key that lands settles the digits into place (3dp rise + a 3% growth), so the keypad has an answer
    // on the amount itself, not only on the key that was pressed.
    val land = remember { Animatable(1f) }
    LaunchedEffect(draft.expression) {
        caret.snapTo(1f)
        if (!reduced) {
            land.snapTo(0f)
            land.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 900f))
        }
        while (true) {
            kotlinx.coroutines.delay(520)
            caret.animateTo(0f, tween(160))
            caret.animateTo(1f, tween(160))
        }
    }
    Row(
        Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("¥", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = XpColors.Ink)
        Text(
            // Reference: an untouched amount is just ¥ and the caret, no grey 0.
            if (draft.isEmpty) "" else draft.expression,
            modifier = Modifier.padding(start = 6.dp).graphicsLayer {
                val k = land.value
                if (k < 1f) {
                    scaleX = 0.97f + 0.03f * k
                    scaleY = 0.97f + 0.03f * k
                    translationY = (1f - k) * 3.dp.toPx()
                    alpha = 0.72f + 0.28f * k
                }
            },
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (cents == 0L) XpColors.InkTertiary else XpColors.Ink,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
        Box(
            Modifier.padding(start = 2.dp).width(2.dp).height(28.dp)
                .graphicsLayer { alpha = caret.value }
                .background(XpColors.Accent),
        )
    }
}

/**
 * A category cell that has just become the pick pops once (0.9 → 1 on the bouncy spring), so the eye follows the
 * selection: press feedback only shrinks, this is the "you chose this" answer.
 */
private fun Modifier.selectionPop(selected: Boolean): Modifier = composed {
    val reduced = rememberReducedMotion()
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && !reduced) {
            pop.snapTo(0.9f)
            pop.animateTo(1f, XpMotion.bouncy())
        }
    }
    graphicsLayer {
        scaleX = pop.value
        scaleY = pop.value
    }
}

/**
 * Five columns of coloured cells, 65dp each (reference), scrolling once the user has more categories.
 * The fixed height keeps the keypad on screen at every font scale; the last cell opens 分类管理.
 */
@Composable
private fun CategoryArea(
    offered: List<CategoryDef>,
    selected: String,
    onSelect: (String) -> Unit,
    onCustom: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier.fillMaxWidth().height(64.dp * 3 + 8.dp).padding(top = XpSpace.xs),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        userScrollEnabled = true,
    ) {
        items(offered, key = { it.id }) { def ->
            Column(
                Modifier.clip(RoundedCornerShape(XpRadius.s))
                    .selectionPop(def.id == selected)
                    .pressableClick(haptic = false, scaleDown = 0.94f) { onSelect(def.id) }
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CategoryTile(def, size = 36.dp, selected = def.id == selected)
                // Five columns at 360dp × 1.3 font: shrink the label to fit before ever ellipsizing it.
                BasicText(
                    def.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = if (def.id == selected) XpColors.Ink else XpColors.InkSecondary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp, stepSize = 0.5.sp),
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
                )
            }
        }
        item(key = "custom") {
            Column(
                Modifier.pressableClick(haptic = false, scaleDown = 0.94f, onClick = onCustom)
                    .padding(vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(36.dp * 0.28f))
                        .background(XpColors.Fill),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        tint = XpColors.InkSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    "自定义",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                    color = XpColors.InkSecondary,
                )
            }
        }
    }
}

/** Note pill + the camera slot (36dp each, reference). The camera is an OCR entry that is not wired yet. */
@Composable
private fun NoteRow(value: String, onValueChange: (String) -> Unit, onCamera: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = XpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(XpRadius.pill))
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
        Spacer(Modifier.width(XpSpace.s))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(XpColors.Fill)
                .pressableClick(haptic = false, onClick = onCamera),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.PhotoCamera,
                contentDescription = "拍照记账",
                tint = XpColors.InkSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

private val keypadRows = listOf(
    listOf("7", "8", "9", RecordAmount.BACKSPACE),
    listOf("4", "5", "6", RecordDraftMath.MINUS),
    listOf("1", "2", "3", RecordDraftMath.PLUS),
)

/** 4×4 keypad, 49dp rows (reference). Flat keys, one accent save button, one quiet 再记一笔. */
@Composable
private fun Keypad(
    canSave: Boolean,
    onKey: (String) -> Unit,
    onRepeat: () -> Unit,
    onSave: () -> Unit,
    /** Edit mode: the bottom-left key deletes the row instead of 再记一笔, and is always enabled. */
    editing: Boolean = false,
) {
    val haptics = rememberXpHaptics()
    Column(Modifier.fillMaxWidth().padding(top = XpSpace.xs, bottom = XpSpace.m)) {
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
                    Modifier.fillMaxWidth().height(49.dp).pressableClick(enabled = editing || canSave, scaleDown = 0.94f) {
                        haptics.select()
                        onRepeat()
                    },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        if (editing) "删除" else "再记一笔",
                        fontSize = 14.sp,
                        color = when {
                            editing -> XpColors.Negative
                            canSave -> XpColors.InkSecondary
                            else -> XpColors.InkTertiary
                        },
                        maxLines = 1,
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
                contentDescription = "退格",
                tint = XpColors.Ink,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Text(key, fontSize = size.sp, color = XpColors.Ink)
        }
    }
}

/** 86×48, radius 12: accent solid when there is an amount, grey fill when there is not (reference). */
@Composable
private fun SaveButton(enabled: Boolean, onSave: () -> Unit) {
    // Disabled used to be accent at 35% under OnAccent, which is white in light (≈1.9:1) and near-black in dark
    // (≈1.4:1): the label disappeared in both. A tinted fill with secondary ink reads as "not yet" everywhere.
    val bg by animateColorAsState(
        if (enabled) XpColors.Accent else XpColors.Accent.copy(alpha = 0.18f),
        tween(160, easing = XpEase.out),
        label = "saveBg",
    )
    val fg by animateColorAsState(if (enabled) XpColors.OnAccent else XpColors.InkSecondary, tween(160, easing = XpEase.out), label = "saveFg")
    Box(
        Modifier.size(width = 86.dp, height = 48.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .pressableClick(enabled = enabled, scaleDown = 0.95f, haptic = true) { onSave() },
        contentAlignment = Alignment.Center,
    ) {
        Text("保存", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

/** Hour/minute picker for the 19:14 pill; 24h dial, defaults to the current minute. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(
    hour: Int,
    minute: Int,
    onPick: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.hour, state.minute) }) { Text("确定", color = XpColors.Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = XpColors.InkSecondary) }
        },
        text = { TimePicker(state = state) },
        containerColor = XpColors.Card,
    )
}

/** 「今天 / 昨天 / 10月26日」 for the date pill. */
/** Cents back into what the keypad would have typed: 1250 → "12.5", 3000 → "30". */
internal fun draftAmountOf(cents: Long): String =
    if (cents % 100 == 0L) "${cents / 100}" else "%d.%02d".format(cents / 100, cents % 100).trimEnd('0')

internal fun dayLabelOf(dayStart: Long, now: Long): String {
    val today = startOfDay(now)
    val diff = Math.round((today - dayStart) / 86_400_000.0).toInt()
    return when (diff) {
        0 -> "今天"
        1 -> "昨天"
        2 -> "前天"
        else -> Calendar.getInstance().apply { timeInMillis = dayStart }
            .let { "${it.get(Calendar.MONTH) + 1}月${it.get(Calendar.DAY_OF_MONTH)}日" }
    }
}

/** Combines a day (midnight) with the picked clock time; DST-safe through Calendar. */
internal fun atDayAndTime(dayStart: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = dayStart
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

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
