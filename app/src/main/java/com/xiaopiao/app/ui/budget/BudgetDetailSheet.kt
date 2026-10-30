package com.xiaopiao.app.ui.budget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.R
import com.xiaopiao.app.domain.*
import com.xiaopiao.app.ui.category.CategoryTile
import com.xiaopiao.app.ui.components.Segmented
import com.xiaopiao.app.ui.components.XpCard
import com.xiaopiao.app.ui.components.XpSheet
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class BudgetDetailTarget(val period: BudgetPeriod, val category: Category? = null)
private sealed interface Drill {
    data class Bucket(val value: BudgetBucket) : Drill
    data class Category(val id: String) : Drill
    data class Merchant(val name: String) : Drill
}

/** One drawer, with an internal back stack: budget -> day/week/rank -> transaction. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BudgetDetailSheet(
    target: BudgetDetailTarget,
    settings: BudgetSettings,
    txns: List<Txn>,
    defs: List<CategoryDef>,
    now: Long,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
) {
    var anchor by rememberSaveable(target) { mutableStateOf(BudgetMath.startOf(target.period, now)) }
    var drill by remember(target, anchor) { mutableStateOf<Drill?>(null) }
    var txnId by remember(target, anchor) { mutableStateOf<Long?>(null) }
    var ranking by rememberSaveable(target, anchor) { mutableIntStateOf(0) }
    var allRanks by remember(target, anchor, ranking) { mutableStateOf(false) }
    val snapshot = remember(target, anchor, txns, defs) {
        BudgetDetails.build(target.period, anchor, txns, target.category, defs)
    }
    val locale = LocalConfiguration.current.locales[0]
    val shortDate = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }
    val weekday = remember(locale) { SimpleDateFormat("M/d EEE", locale) }
    val dateTime = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
    val dayTime = remember(locale) { DateFormat.getTimeInstance(DateFormat.SHORT, locale) }
    fun range(from: Long, until: Long): String = if (target.period == BudgetPeriod.Day)
        shortDate.format(Date(from)) else "${shortDate.format(Date(from))} – ${shortDate.format(Date(until - 1))}"
    val visible = remember(snapshot, drill, defs) {
        snapshot.txns.filter { txn -> when (val value = drill) {
            is Drill.Bucket -> txn.occurredAt >= value.value.from && txn.occurredAt < value.value.until
            is Drill.Category -> defs.resolve(txn).id == value.id
            is Drill.Merchant -> BudgetDetails.merchantKey(txn, defs) == value.name
            null -> true
        } }
    }
    val selectedTxn = snapshot.txns.firstOrNull { it.id == txnId }
    LaunchedEffect(txnId, selectedTxn) { if (txnId != null && selectedTxn == null) txnId = null }
    val periodTitle = stringResource(when (target.period) {
        BudgetPeriod.Day -> R.string.budget_detail_day
        BudgetPeriod.Week -> R.string.budget_detail_week
        BudgetPeriod.Month -> R.string.budget_detail_month
    })
    val title = if (selectedTxn != null) stringResource(R.string.budget_detail_transaction)
        else when (val value = drill) {
            is Drill.Bucket -> if (target.period == BudgetPeriod.Week) weekday.format(Date(value.value.from)) else range(value.value.from, value.value.until)
            is Drill.Category -> defs.firstOrNull { it.id == value.id }?.label ?: stringResource(R.string.budget_detail_category)
            is Drill.Merchant -> value.name
            null -> target.category?.let { stringResource(R.string.budget_detail_category_title, it.label) } ?: periodTitle
        }
    val back = { if (txnId != null) txnId = null else drill = null }
    val nested = txnId != null || drill != null
    val listState = rememberLazyListState()
    LaunchedEffect(anchor, drill, txnId) { listState.scrollToItem(0) }

    XpSheet(onDismiss = onDismiss, containerColor = XpColors.CanvasGrouped) {
        BackHandler(enabled = nested, onBack = back)
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = XpSpace.s), verticalAlignment = Alignment.CenterVertically) {
                if (nested) IconButton(onClick = back) {
                    Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.budget_detail_back), tint = XpColors.Ink)
                } else Spacer(Modifier.width(48.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    color = XpColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.budget_detail_close), tint = XpColors.Ink)
                }
            }
            LazyColumn(Modifier.weight(1f), state = listState,
                contentPadding = PaddingValues(start = XpSpace.page, end = XpSpace.page, bottom = XpSpace.xl),
                verticalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                if (selectedTxn != null) {
                    item {
                        XpCard {
                            Text("¥${selectedTxn.amountCents.yuan()}", style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.SemiBold, color = XpColors.Ink)
                            DetailField(stringResource(R.string.budget_detail_merchant), BudgetDetails.merchantKey(selectedTxn, defs))
                            DetailField(stringResource(R.string.budget_detail_category), defs.resolve(selectedTxn).label)
                            DetailField(stringResource(R.string.budget_detail_time), dateTime.format(Date(selectedTxn.occurredAt)))
                            DetailField(stringResource(R.string.budget_detail_source), selectedTxn.source.label)
                            if (selectedTxn.note.isNotBlank()) DetailField(stringResource(R.string.budget_detail_note), selectedTxn.note)
                        }
                    }
                } else {
                    if (drill == null) item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { anchor = BudgetDetails.shift(target.period, anchor, -1) }) {
                                Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.budget_detail_previous), tint = XpColors.Ink)
                            }
                            Text(range(snapshot.from, snapshot.until), Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
                            IconButton(enabled = snapshot.until <= BudgetMath.startOf(target.period, now),
                                onClick = { anchor = BudgetDetails.shift(target.period, anchor, 1) }) {
                                Icon(Icons.Outlined.ChevronRight, stringResource(R.string.budget_detail_next),
                                    tint = if (snapshot.until <= BudgetMath.startOf(target.period, now)) XpColors.Ink else XpColors.InkTertiary)
                            }
                        }
                    }
                    item {
                        XpCard {
                            Text(stringResource(R.string.budget_detail_spent), style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                            Text("¥${visible.sumOf { it.amountCents }.yuan()}",
                                style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.SemiBold, color = XpColors.Ink)
                            Text(stringResource(R.string.budget_detail_count, visible.size), style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                            if (drill == null) {
                                val limit = if (target.category != null) settings.categoryLimits[target.category] else settings.limitOf(target.period)
                                if (limit != null) DetailField(stringResource(R.string.budget_detail_current_limit), "¥${limit.yuan()}")
                                TextButton(onClick = onEdit) { Text(stringResource(R.string.budget_detail_edit), color = XpColors.Accent) }
                            }
                        }
                    }
                    if (drill == null && snapshot.buckets.isNotEmpty()) {
                        item { DetailHeading(stringResource(if (target.period == BudgetPeriod.Week) R.string.budget_detail_daily_spend else R.string.budget_detail_weekly_spend)) }
                        items(snapshot.buckets, key = { "bucket:${it.from}" }) { bucket ->
                            BreakdownRow(
                                label = if (target.period == BudgetPeriod.Week) weekday.format(Date(bucket.from)) else range(bucket.from, bucket.until),
                                count = bucket.count, cents = bucket.spent,
                                fraction = bucket.spent.toFloat() / snapshot.buckets.maxOf { it.spent }.coerceAtLeast(1),
                                onClick = { drill = Drill.Bucket(bucket) },
                            )
                        }
                    }
                    if (drill == null && snapshot.txns.isNotEmpty()) {
                        item { DetailHeading(stringResource(R.string.budget_detail_ranking)) }
                        item { Segmented(listOf(stringResource(R.string.budget_detail_category), stringResource(R.string.budget_detail_merchant)), ranking, { ranking = it }) }
                        val ranks = if (ranking == 0) snapshot.categories else snapshot.merchants
                        items(if (allRanks) ranks else ranks.take(5), key = { "rank:$ranking:${it.key}" }) { rank ->
                            val index = ranks.indexOf(rank) + 1
                            BreakdownRow(label = "$index  ${if (ranking == 0) defs.firstOrNull { it.id == rank.key }?.label ?: rank.key else rank.key}",
                                count = rank.count, cents = rank.spent, fraction = rank.spent.toFloat() / ranks.first().spent.coerceAtLeast(1),
                                onClick = { drill = if (ranking == 0) Drill.Category(rank.key) else Drill.Merchant(rank.key) })
                        }
                        if (!allRanks && ranks.size > 5) item {
                            TextButton(onClick = { allRanks = true }) { Text(stringResource(R.string.budget_detail_all_ranks, ranks.size), color = XpColors.Accent) }
                        }
                    }
                    item { DetailHeading(stringResource(R.string.budget_detail_transactions)) }
                    if (visible.isEmpty()) item {
                        XpCard {
                            Text(stringResource(R.string.budget_detail_empty), style = MaterialTheme.typography.titleMedium, color = XpColors.Ink)
                            Text(stringResource(R.string.budget_detail_empty_hint), style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
                        }
                    }
                    visible.groupBy { startOfDay(it.occurredAt) }.forEach { (day, rows) ->
                        item(key = "day:$day") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(shortDate.format(Date(day)), style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                                Text("¥${rows.sumOf { it.amountCents }.yuan()}", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                            }
                        }
                        items(rows, key = { "txn:${it.id}" }) { txn ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.contentCard))
                                .background(XpColors.Card).clickable { txnId = txn.id }.heightIn(min = 64.dp).padding(XpSpace.m),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                                CategoryTile(defs.resolve(txn), size = 32.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(BudgetDetails.merchantKey(txn, defs), style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${defs.resolve(txn).label} · ${dayTime.format(Date(txn.occurredAt))}", style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
                                }
                                Text("¥${txn.amountCents.yuan()}", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = XpColors.Ink)
                                Icon(Icons.Outlined.ChevronRight, null, tint = XpColors.InkTertiary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeading(label: String) {
    Text(label, style = MaterialTheme.typography.titleSmall, color = XpColors.Ink,
        modifier = Modifier.padding(top = XpSpace.s))
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(top = XpSpace.m), verticalArrangement = Arrangement.spacedBy(XpSpace.xs)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
    }
}

@Composable
private fun BreakdownRow(label: String, count: Int, cents: Cents, fraction: Float, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(XpRadius.contentCard))
        .background(XpColors.Card).clickable(onClick = onClick).heightIn(min = 64.dp).padding(XpSpace.m),
        verticalArrangement = Arrangement.spacedBy(XpSpace.s)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(XpSpace.s)) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.budget_detail_count, count), style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary)
            }
            Text("¥${cents.yuan()}", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = XpColors.Ink)
            Icon(Icons.Outlined.ChevronRight, null, tint = XpColors.InkTertiary, modifier = Modifier.size(16.dp))
        }
        Box(Modifier.fillMaxWidth().height(XpSpace.xs).clip(RoundedCornerShape(XpRadius.pill)).background(XpColors.Fill)) {
            if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(XpSpace.xs).background(XpColors.Accent))
        }
    }
}
