package com.xiaopiao.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Cents
import com.xiaopiao.app.domain.DAY_MS
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace

/** Time window of the ledger list (docs/APP-DESIGN.md §3.2, 采纳 §3.7 的 chip 组). */
enum class LedgerRange(val label: String) { Month("本月"), LastMonth("上月"), Year("今年"), Custom("自定义") }

/**
 * Everything the 筛选 sheet can narrow. Kept as a value class so it is testable without Compose and
 * so the screen can hold it in a single `remember`.
 */
data class LedgerFilter(
    val direction: Direction? = null,
    val range: LedgerRange = LedgerRange.Month,
    val categories: Set<Category> = emptySet(),
    val sources: Set<Source> = emptySet(),
    val minCents: Cents? = null,
    val maxCents: Cents? = null,
    val customFrom: Long? = null,
    val customTo: Long? = null,
) {
    val isDefault: Boolean
        get() = direction == null && range == LedgerRange.Month && categories.isEmpty() &&
            sources.isEmpty() && minCents == null && maxCents == null

    /** Number shown next to 「筛选」; 0 hides it. */
    val activeCount: Int
        get() = (if (direction != null) 1 else 0) +
            (if (range != LedgerRange.Month) 1 else 0) +
            (if (categories.isNotEmpty()) 1 else 0) +
            (if (sources.isNotEmpty()) 1 else 0) +
            (if (minCents != null || maxCents != null) 1 else 0)

    /** A reversed custom range is stored the other way round, never as an empty result. */
    fun normalized(): LedgerFilter {
        val min = minCents
        val max = maxCents
        val swapped = if (min != null && max != null && min > max) copy(minCents = max, maxCents = min) else this
        val from = swapped.customFrom
        val to = swapped.customTo
        return if (from != null && to != null && from > to) swapped.copy(customFrom = to, customTo = from) else swapped
    }

    /** Half-open window `[from, to)` the filter applies to. */
    fun window(now: Long): Pair<Long, Long> = when (range) {
        LedgerRange.Month -> LedgerRanges.monthStart(now) to LedgerRanges.nextMonthStart(now)
        LedgerRange.LastMonth -> LedgerRanges.lastMonthStart(now) to LedgerRanges.monthStart(now)
        LedgerRange.Year -> LedgerRanges.yearStart(now) to Long.MAX_VALUE
        LedgerRange.Custom -> {
            val from = customFrom?.let { startOfDay(it) } ?: Long.MIN_VALUE
            val to = customTo?.let { startOfDay(it) + DAY_MS } ?: Long.MAX_VALUE
            from to to
        }
    }

    fun apply(list: List<Txn>, now: Long, query: String = ""): List<Txn> {
        val (from, to) = window(now)
        val q = query.trim()
        return list.filter { t ->
            t.occurredAt >= from && t.occurredAt < to &&
                (direction == null || t.direction == direction) &&
                (categories.isEmpty() || t.category in categories) &&
                (sources.isEmpty() || t.source in sources) &&
                (minCents == null || t.amountCents >= minCents) &&
                (maxCents == null || t.amountCents <= maxCents) &&
                (q.isEmpty() || t.matchesQuery(q))
        }
    }
}

/** 36dp selectable capsule: fill when idle, accent-soft when picked. No border, no accent fill. */
@Composable
fun LedgerChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(XpRadius.pill))
            .background(if (selected) XpColors.AccentSoft else XpColors.Fill)
            .clickable(onClick = onClick)
            .padding(horizontal = XpSpace.l),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) XpColors.Accent else XpColors.Ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = XpColors.InkSecondary,
        modifier = Modifier.padding(top = XpSpace.xl, bottom = XpSpace.s),
    )
}

/**
 * 筛选 sheet: 收支 / 时间 / 分类 / 金额 / 来源, applied only on 确认 (docs/APP-DESIGN.md §3.2).
 * The custom range's two capsules expand an inline month grid — no nested sheet.
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LedgerFilterSheet(
    initial: LedgerFilter,
    now: Long,
    onApply: (LedgerFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = 起始日期, false = 结束日期
    var anchor by remember { mutableStateOf(LedgerRanges.monthStart(draft.customFrom ?: now)) }
    val today = startOfDay(now)

    XpSheet(onDismiss = onDismiss) {
        Box(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page, vertical = XpSpace.s)) {
            Text("筛选", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink, modifier = Modifier.align(Alignment.Center))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = XpSpace.page)) {
            Column(Modifier.fillMaxWidth().heightIn(max = 470.dp).verticalScroll(rememberScrollState())) {
                GroupLabel("收支")
                Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                    LedgerChip("支出", draft.direction == Direction.OUT) {
                        draft = draft.copy(direction = if (draft.direction == Direction.OUT) null else Direction.OUT)
                    }
                    LedgerChip("收入", draft.direction == Direction.IN) {
                        draft = draft.copy(direction = if (draft.direction == Direction.IN) null else Direction.IN)
                    }
                }

                GroupLabel("时间")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(XpSpace.m), verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                    LedgerRange.entries.forEach { range ->
                        LedgerChip(range.label, draft.range == range) {
                            draft = draft.copy(
                                range = range,
                                customFrom = draft.customFrom ?: LedgerRanges.monthStart(now),
                                customTo = draft.customTo ?: today,
                            )
                            picking = null
                        }
                    }
                }
                if (draft.range == LedgerRange.Custom) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = XpSpace.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
                    ) {
                        DateCapsule(
                            label = draft.customFrom?.let { dateLabel(it, today) } ?: "起始日期",
                            active = picking == true,
                            modifier = Modifier.weight(1f),
                        ) {
                            picking = if (picking == true) null else true
                            anchor = LedgerRanges.monthStart(draft.customFrom ?: now)
                        }
                        Text("—", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkTertiary)
                        DateCapsule(
                            label = draft.customTo?.let { dateLabel(it, today) } ?: "结束日期",
                            active = picking == false,
                            modifier = Modifier.weight(1f),
                        ) {
                            picking = if (picking == false) null else false
                            anchor = LedgerRanges.monthStart(draft.customTo ?: now)
                        }
                    }
                    if (picking != null) {
                        MonthGrid(
                            anchor = anchor,
                            selected = if (picking == true) draft.customFrom else draft.customTo,
                            today = today,
                            onPick = { day ->
                                draft = if (picking == true) draft.copy(customFrom = day) else draft.copy(customTo = day)
                                picking = null
                            },
                            onShift = { anchor = LedgerCalendar.shiftMonths(anchor, it) },
                            modifier = Modifier.padding(top = XpSpace.m),
                        )
                    }
                }

                GroupLabel("分类")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(XpSpace.s), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    Category.entries.forEach { category ->
                        LedgerChip(category.label, category in draft.categories) {
                            draft = draft.copy(
                                categories = if (category in draft.categories) draft.categories - category else draft.categories + category,
                            )
                        }
                    }
                }

                GroupLabel("金额")
                Row(
                    Modifier.fillMaxWidth().padding(top = XpSpace.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
                ) {
                    AmountCapsule(
                        cents = draft.minCents,
                        placeholder = "最低金额",
                        modifier = Modifier.weight(1f),
                    ) { draft = draft.copy(minCents = it) }
                    Text("—", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkTertiary)
                    AmountCapsule(
                        cents = draft.maxCents,
                        placeholder = "最高金额",
                        modifier = Modifier.weight(1f),
                    ) { draft = draft.copy(maxCents = it) }
                }

                GroupLabel("来源")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(XpSpace.s), verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
                    Source.entries.forEach { source ->
                        LedgerChip(source.label, source in draft.sources) {
                            draft = draft.copy(sources = if (source in draft.sources) draft.sources - source else draft.sources + source)
                        }
                    }
                }
                Box(Modifier.height(XpSpace.l))
            }

            // Footer: two halves, text links only — the sheet's single primary action is 确认 (accent text, 600).
            Row(Modifier.fillMaxWidth().padding(vertical = XpSpace.m)) {
                Text(
                    "重置",
                    style = MaterialTheme.typography.labelLarge,
                    color = XpColors.Accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(XpRadius.pill)).clickable { draft = LedgerFilter() }.padding(vertical = XpSpace.m),
                )
                Text(
                    "确认",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = XpColors.Accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(XpRadius.pill)).clickable { onApply(draft.normalized()) }.padding(vertical = XpSpace.m),
                )
            }
        }
    }
}

@Composable
private fun DateCapsule(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(XpRadius.pill))
            .background(if (active) XpColors.AccentSoft else XpColors.Fill)
            .clickable(onClick = onClick)
            .padding(horizontal = XpSpace.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) XpColors.Accent else XpColors.Ink,
            maxLines = 1,
        )
    }
}

@Composable
private fun AmountCapsule(cents: Cents?, placeholder: String, modifier: Modifier = Modifier, onChange: (Cents?) -> Unit) {
    // Local text is the source of truth while typing ("1." must survive), so the parent only pushes
    // a value back in when it changed for a reason other than this field (重置).
    var text by remember { mutableStateOf(cents?.let { AmountEntry.fromCents(it) } ?: "") }
    androidx.compose.runtime.LaunchedEffect(cents) {
        if (text.toCentsOrNull() != cents) text = cents?.let { AmountEntry.fromCents(it) } ?: ""
    }
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(XpRadius.pill))
            .background(XpColors.Fill)
            .padding(horizontal = XpSpace.m),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                val clean = raw.filter { it.isDigit() || it == '.' }.take(9)
                text = clean
                onChange(clean.toCentsOrNull())
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = XpColors.Ink),
            cursorBrush = SolidColor(XpColors.Accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = XpColors.InkTertiary)
                }
                inner()
            },
        )
    }
}

private fun String.toCentsOrNull(): Cents? {
    if (isEmpty() || this == ".") return null
    return AmountEntry.toCents(this)
}

private fun dateLabel(dayStart: Long, today: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = dayStart }
    val sameYear = java.util.Calendar.getInstance().apply { timeInMillis = today }.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR)
    return if (sameYear) "${c.get(java.util.Calendar.MONTH) + 1}/${c.get(java.util.Calendar.DAY_OF_MONTH)}"
    else "${c.get(java.util.Calendar.YEAR)}/${c.get(java.util.Calendar.MONTH) + 1}/${c.get(java.util.Calendar.DAY_OF_MONTH)}"
}
