package com.xiaopiao.app.ui.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.data.DemoSeed
import com.xiaopiao.app.domain.Direction
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.yuan
import com.xiaopiao.app.ui.components.IconTile
import com.xiaopiao.app.ui.components.MoneyText
import com.xiaopiao.app.ui.components.icon
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay

/** Tabular 13sp, the day card's second voice (dates and the 支/收 pair). */
private val TabularSmall: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")

/** Hairline inset that lines the divider up with the row text (40dp tile + 12dp gap + 6dp optical). */
private val DividerInset = 58.dp

/**
 * One day: a title line (date left, 支/收 right) above a 10dp card of rows (docs/APP-DESIGN.md §3.7).
 * Zero-spend days still print both numbers — never the word 「零消费」.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LedgerDayCard(
    group: DayGroup,
    hidden: Boolean,
    selection: Set<Long>?,
    onToggle: (Txn) -> Unit,
    onLongPress: (Txn) -> Unit,
    onEdit: (Txn) -> Unit,
    onDelete: (Txn) -> Unit,
    modifier: Modifier = Modifier,
    swipeToDelete: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = XpSpace.xs, end = XpSpace.xs, top = XpSpace.m, bottom = XpSpace.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(group.header, style = TabularSmall, color = XpColors.InkSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(XpSpace.m)) {
                DayFigure("支", group.outCents, hidden)
                DayFigure("收", group.inCents, hidden)
            }
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(XpRadius.listCard))
                .background(XpColors.Card),
        ) {
            group.items.forEachIndexed { index, txn ->
                if (index > 0) {
                    Box(Modifier.fillMaxWidth().padding(start = DividerInset).height(0.5.dp).background(XpColors.Hairline))
                }
                if (selection != null) {
                    LedgerRowContent(
                        txn = txn, hidden = hidden, selected = txn.id in selection,
                        onToggle = { onToggle(txn) }, onLongPress = { onToggle(txn) }, onClick = { onToggle(txn) },
                    )
                } else if (!swipeToDelete) {
                    LedgerRowContent(
                        txn = txn, hidden = hidden, selected = null,
                        onToggle = {}, onLongPress = { onLongPress(txn) }, onClick = { onEdit(txn) },
                    )
                } else {
                    SwipeableRow(
                        txn = txn, hidden = hidden,
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
        "$prefix ${if (hidden) AMOUNT_MASK else cents.yuan()}",
        style = TabularSmall,
        color = XpColors.InkSecondary,
    )
}

/** Swipe left to delete: Negative fill behind the row, then the row collapses and the host offers undo. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableRow(
    txn: Txn,
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
    // Let the row finish collapsing before the data change removes it from the list.
    LaunchedEffect(gone) { if (gone) { delay(180); onDelete() } }
    AnimatedVisibility(
        visible = !gone,
        exit = shrinkVertically(tween(160, easing = XpEase.out)) + fadeOut(tween(120)),
    ) {
        SwipeToDismissBox(
            state = state,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                val swiping = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
                Box(
                    Modifier.fillMaxWidth().background(if (swiping) XpColors.Negative else XpColors.Card).padding(horizontal = XpSpace.page),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (swiping) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", tint = XpColors.OnAccent, modifier = Modifier.size(22.dp))
                    }
                }
            },
        ) {
            LedgerRowContent(
                txn = txn, hidden = hidden, selected = null,
                onToggle = {}, onLongPress = onLongPress, onClick = onClick,
            )
        }
    }
}

/**
 * The three-element row: 40dp category tile, merchant (+ source chip) over an optional note, amount right.
 * Single-line rows are 56dp, rows with a note 76dp — both from the same padding, so the tile never jumps.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerRowContent(
    txn: Txn,
    hidden: Boolean,
    selected: Boolean?,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onClick: () -> Unit,
) {
    val income = txn.direction == Direction.IN
    val multiselect = selected != null
    val auto = txn.source == Source.NOTIFICATION || txn.source == Source.CSV
    val note = txn.note.takeIf { it.isNotBlank() && it != DemoSeed.NOTE }

    Row(
        Modifier.fillMaxWidth()
            .background(if (selected == true) XpColors.AccentSoft.copy(alpha = 0.55f) else Color.Transparent)
            .combinedClickable(
                onClick = { if (multiselect) onToggle() else onClick() },
                onLongClick = { if (multiselect) onToggle() else onLongPress() },
            )
            .heightIn(min = if (note != null) 76.dp else 56.dp)
            .padding(horizontal = XpSpace.l, vertical = XpSpace.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiselect) {
            SelectBox(checked = selected == true)
            Spacer(Modifier.width(XpSpace.m))
        }
        IconTile(txn.category.icon(), size = 40.dp)
        Spacer(Modifier.width(XpSpace.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    txn.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    color = XpColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (auto) {
                    Spacer(Modifier.width(6.dp))
                    SourceChip(txn.source.label)
                }
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = XpColors.InkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(XpSpace.m))
        if (hidden) {
            Text(
                "¥ $AMOUNT_MASK",
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = XpColors.Ink,
            )
        } else {
            MoneyText(
                txn.amountCents,
                style = MaterialTheme.typography.titleMedium,
                color = if (income) XpColors.Positive else XpColors.Ink,
                prefix = if (income) "+" else "-",
            )
        }
    }
}

/** 20dp chip: 4dp corners, hairline border, 11sp secondary ink — a label, never a button. */
@Composable
fun SourceChip(text: String) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.height(20.dp).clip(shape).border(1.dp, XpColors.Hairline, shape).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = XpColors.InkSecondary, maxLines = 1)
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
