package com.xiaopiao.app.ui.ledger

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaopiao.app.domain.CategoryDef
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.resolve
import com.xiaopiao.app.domain.sourceChipLabel
import com.xiaopiao.app.domain.yuanGrouped
import com.xiaopiao.app.ui.category.CategoryTile
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay

/** Tabular 13sp, the day card's second voice (dates, the 支/收 pair and the 「时间 | 商户」 line). */
private val TabularSmall: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum")

/** 17sp/600 tabular — the row amount (reference `041428`: 17sp). */
private val RowAmount = TextStyle(
    fontSize = 17.sp,
    lineHeight = 22.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)

/** Card header height, from the reference (44dp, 16dp inset). */
private val DayHeaderHeight = 44.dp

/** Hairline inset that lines the divider up with the row text (16dp pad + 40dp tile + 12dp gap). */
private val DividerInset = 68.dp

/**
 * One day (reference `041428`): a 44dp header line — 「10月28日 今日」 left, 「支 18.28 收 0.00」 right —
 * above a 10dp card of 60dp rows. Zero-spend days still print both numbers.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LedgerDayCard(
    group: DayGroup,
    defs: List<CategoryDef>,
    hidden: Boolean,
    selection: Set<Long>?,
    onToggle: (Txn) -> Unit,
    onLongPress: (Txn) -> Unit,
    onEdit: (Txn) -> Unit,
    onDelete: (Txn) -> Unit,
    modifier: Modifier = Modifier,
    swipeToDelete: Boolean = true,
) {
    // Reference `041428`: the day header sits inside the white card, a hairline above the first row.
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XpRadius.listCard))
            .background(XpColors.Card),
    ) {
        Row(
            Modifier.fillMaxWidth().height(DayHeaderHeight).padding(horizontal = XpSpace.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                group.header,
                style = TabularSmall.copy(fontWeight = FontWeight.Medium),
                color = XpColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                DayFigure("支", group.outCents, hidden)
                DayFigure("收", group.inCents, hidden)
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(XpColors.Hairline))
        Column(Modifier.fillMaxWidth()) {
            group.items.forEachIndexed { index, txn ->
                if (index > 0) {
                    Box(Modifier.fillMaxWidth().padding(start = DividerInset).height(0.5.dp).background(XpColors.Hairline))
                }
                if (selection != null) {
                    LedgerRowContent(
                        txn = txn, defs = defs, hidden = hidden, selected = txn.id in selection,
                        onToggle = { onToggle(txn) }, onLongPress = { onToggle(txn) }, onClick = { onToggle(txn) },
                    )
                } else if (!swipeToDelete) {
                    LedgerRowContent(
                        txn = txn, defs = defs, hidden = hidden, selected = null,
                        onToggle = {}, onLongPress = { onLongPress(txn) }, onClick = { onEdit(txn) },
                    )
                } else {
                    SwipeableRow(
                        txn = txn, defs = defs, hidden = hidden,
                        onClick = { onEdit(txn) }, onLongPress = { onLongPress(txn) }, onDelete = { onDelete(txn) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DayFigure(prefix: String, cents: Long, hidden: Boolean) {
    Text(
        "$prefix ${if (hidden) AMOUNT_MASK else cents.yuanGrouped()}",
        style = TabularSmall,
        color = XpColors.InkSecondary,
        maxLines = 1,
    )
}

/** Swipe left to delete: Negative fill behind the row, then the row collapses and the host offers undo. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableRow(
    txn: Txn,
    defs: List<CategoryDef>,
    hidden: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = rememberXpHaptics()
    var gone by remember(txn.id) { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.4f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) { haptics.thud(); gone = true; true } else false
        },
    )
    LaunchedEffect(gone) {
        if (gone) {
            delay(180)
            onDelete()
        }
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(XpColors.Negative),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(Modifier.padding(end = XpSpace.page), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(XpSpace.xs))
                    Text("删除", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }
        },
    ) {
        LedgerRowContent(
            txn = txn, defs = defs, hidden = hidden, selected = null,
            onToggle = {}, onLongPress = onLongPress, onClick = onClick,
        )
    }
}

/**
 * The row (reference `041428`): 40dp coloured category tile, category name + source chip on line one,
 * 「18:12 | 商户」 on line two, amount right. 60dp single line, never wraps, never truncates the amount.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerRowContent(
    txn: Txn,
    defs: List<CategoryDef>,
    hidden: Boolean,
    selected: Boolean?,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
) {
    val income = txn.direction == Direction.IN
    val multiselect = selected != null
    val def = defs.resolve(txn)
    val clock = LedgerGrouping.clock(txn.occurredAt)
    val merchant = txn.merchant.trim()
    val secondLine = if (merchant.isEmpty()) clock else "$clock | $merchant"
    val chip = sourceChipLabel(txn.source, txn.channel)

    Row(
        Modifier.fillMaxWidth()
            // The row owns its surface: SwipeToDismissBox paints the red delete fill behind it.
            .background(if (selected == true) XpColors.AccentSoft.copy(alpha = 0.55f) else XpColors.Card)
            .combinedClickable(
                onClick = { if (multiselect) onToggle() else onClick() },
                onLongClick = { if (multiselect) onToggle() else onLongPress() },
            )
            .heightIn(min = 60.dp)
            .padding(horizontal = XpSpace.l, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiselect) {
            SelectBox(checked = selected == true)
            Spacer(Modifier.width(XpSpace.m))
        }
        CategoryTile(def, size = 40.dp)
        Spacer(Modifier.width(XpSpace.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    def.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = XpColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The category name wins the width; the source chip is the one that ellipsizes.
                if (chip != null) {
                    Spacer(Modifier.width(6.dp))
                    SourceChip(chip, Modifier.weight(1f, fill = false))
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                secondLine,
                style = TabularSmall,
                color = XpColors.InkTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(XpSpace.m))
        Text(
            if (hidden) "¥ $AMOUNT_MASK" else (if (income) "+" else "-") + txn.amountCents.yuanGrouped(),
            style = RowAmount,
            color = if (income) XpColors.Accent else XpColors.Ink,
            maxLines = 1,
        )
    }
}

/** 18dp outlined pill, 11sp secondary ink — a label, never a button (reference `041428`). */
@Composable
fun SourceChip(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier.heightIn(min = 18.dp).border(0.75.dp, XpColors.InkTertiary.copy(alpha = 0.5f), shape).padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = XpColors.InkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 20dp selection box: 4dp corners, 1.5dp outline; accent only while checked. */
@Composable
fun SelectBox(checked: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.size(20.dp)
            .clip(shape)
            .background(if (checked) XpColors.Accent else Color.Transparent)
            .border(1.5.dp, if (checked) XpColors.Accent else XpColors.InkTertiary.copy(alpha = 0.6f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Outlined.Check, contentDescription = null, tint = XpColors.OnAccent, modifier = Modifier.size(14.dp))
    }
}

/**
 * Staggered entrance: each card rises 8dp and fades in, 35ms apart (max 6 steps), first frame untouched
 * when the user turned animations off.
 */
@Composable
fun Modifier.staggerIn(index: Int, reduceMotion: Boolean): Modifier {
    val anim = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            delay((index.coerceIn(0, 6) * 35).toLong())
            anim.animateTo(1f, tween(220, easing = XpEase.out))
        }
    }
    return this.graphicsLayer {
        alpha = anim.value
        translationY = (1f - anim.value) * 8.dp.toPx()
    }
}
