package com.xiaopiao.app.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.domain.Category
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.startOfDay
import com.xiaopiao.app.ui.components.SelectCapsule
import com.xiaopiao.app.ui.components.Segmented
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace
import java.util.Calendar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import com.xiaopiao.app.ui.theme.XpRadius

/** Which window the ledger shows (docs/APP-DESIGN.md §3.2). */
enum class LedgerRange(val label: String) { Week("本周"), Month("本月"), All("全部") }

data class LedgerFilter(
    val direction: Direction? = null,
    val range: LedgerRange = LedgerRange.Month,
    val categories: Set<Category> = emptySet(),
) {
    val active: Boolean get() = direction != null || range != LedgerRange.Month || categories.isNotEmpty()

    fun apply(list: List<Txn>, now: Long, query: String): List<Txn> {
        val q = query.trim()
        val from = when (range) {
            LedgerRange.All -> Long.MIN_VALUE
            LedgerRange.Month -> monthStart(now)
            LedgerRange.Week -> weekStart(now)
        }
        return list.filter { t ->
            (direction == null || t.direction == direction) &&
                t.occurredAt >= from &&
                (categories.isEmpty() || t.category in categories) &&
                (q.isEmpty() || t.merchant.contains(q, true) || t.note.contains(q, true))
        }
    }
}

private fun weekStart(now: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = startOfDay(now)
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }
    return startOfDay(cal.timeInMillis)
}

private fun monthStart(now: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = startOfDay(now)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    return startOfDay(cal.timeInMillis)
}

/** Filter sheet: 收支 / 时间 / 分类, applied on 确认 (docs/APP-DESIGN.md §3.2). */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LedgerFilterSheet(initial: LedgerFilter, onApply: (LedgerFilter) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    XpSheet(onDismiss = onDismiss) {
        androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = XpSpace.xl, vertical = XpSpace.s)) {
        Text("筛选", style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
        Label("收支")
        Segmented(listOf("全部", "支出", "收入"), when (draft.direction) {
            null -> 0; Direction.OUT -> 1; Direction.IN -> 2
        }, onSelect = { i ->
            draft = draft.copy(direction = when (i) { 1 -> Direction.OUT; 2 -> Direction.IN; else -> null })
        })
        Label("时间")
        Segmented(LedgerRange.entries.map { it.label }, draft.range.ordinal, onSelect = { i ->
            draft = draft.copy(range = LedgerRange.entries[i])
        })
        Label("分类")
        FlowRow(
            Modifier.fillMaxWidth().padding(top = XpSpace.xs),
            horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
            verticalArrangement = Arrangement.spacedBy(XpSpace.s),
        ) {
            Category.entries.forEach { c ->
                SelectCapsule(
                    text = "${c.emoji} ${c.label}",
                    selected = c in draft.categories,
                    onClick = {
                        draft = draft.copy(categories = if (c in draft.categories) draft.categories - c else draft.categories + c)
                    },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = XpSpace.l), horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            XpButton("重置", { draft = LedgerFilter() }, Modifier.weight(1f), style = XpButtonStyle.Secondary)
            XpButton("确认", {
                onApply(draft.copy(categories = draft.categories))
            }, Modifier.weight(1f))
        }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = XpSpace.l))
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = XpColors.InkSecondary,
        modifier = Modifier.padding(top = XpSpace.l, bottom = XpSpace.s),
    )
}

/** Search + filter row that sits at the top of the ledger (docs/APP-DESIGN.md §3.2). */
@Composable
fun LedgerSearchBar(query: String, onQuery: (String) -> Unit, filterActive: Boolean, onFilter: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.s),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(XpSpace.s),
    ) {
        Row(
            Modifier.weight(1f).height(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(XpRadius.pill))
                .background(XpColors.Fill).padding(horizontal = XpSpace.m),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.Search,
                contentDescription = null,
                tint = XpColors.InkSecondary,
                modifier = Modifier.size(18.dp),
            )
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = XpColors.Ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(XpColors.Accent),
                modifier = Modifier.weight(1f).padding(start = XpSpace.s),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("搜索商户或备注", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkTertiary)
                    }
                    inner()
                },
            )
        }
        Box(
            Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (filterActive) XpColors.AccentSoft else XpColors.Fill)
                .clickable { onFilter() },
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.Tune,
                contentDescription = "筛选",
                tint = if (filterActive) XpColors.Accent else XpColors.Ink,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
