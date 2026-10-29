package com.xiaopiao.app.ui.ledger

import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.xiaopiao.app.ui.components.pressableClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.Text
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
import com.xiaopiao.app.domain.yuanGrouped
import com.xiaopiao.app.ui.category.CategoryTile
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpRadius
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay

/** Tabular 13sp, the day card's second voice (dates, the 支/收 pair and the row time). */
private val TabularSmall: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum")

/** 17sp/600 tabular — the row amount (reference `041428`: 17sp). */
private val RowAmount = TextStyle(
    fontSize = 15.sp,
    lineHeight = 22.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)

/** Card header height, from the reference (44dp, 16dp inset). */
private val DayHeaderHeight = 44.dp

/** Hairline inset that lines the divider up with the row text (16dp pad + 32dp tile + 12dp gap). */
private val DividerInset = 60.dp

/**
 * One day (reference `041428`): a 44dp header line — 「10月28日 今日」 left, 「支 18.28 收 0.00」 right —
 * above a 10dp card of 48dp rows. Zero-spend days still print both numbers.
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
    /** Once per install: this card holds the row that peeks its 删除 action (see [SwipeableRow]). */
    hintFirstRow: Boolean = false,
    onHintShown: () -> Unit = {},
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
                        hint = hintFirstRow && index == 0,
                        onHintShown = onHintShown,
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

/** Which row has its delete action open; one at a time, and the list closes it when it scrolls. */
class SwipeRows {
    var openId by mutableStateOf<Long?>(null)
}

val LocalSwipeRows = staticCompositionLocalOf<SwipeRows?> { null }

private val SwipeActionWidth = 80.dp

/** How far the first row peeks on its own the first time (mobile rules §5: a gesture nobody has seen needs one hint). */
private val SwipeHintPeek = 56.dp

/** Past this share of the row width a swipe deletes straight away (iOS-style full swipe). */
private const val FullSwipe = 0.55f

/**
 * Swipe left to delete (tt: 左滑删除): the row stops with an 80dp 删除 button showing; tap it, or keep going past
 * 55% to delete in one move (a tick when that arms). Delete slides the row out and folds its height away, then
 * the host removes it and offers undo. One row open at a time; tapping an open row only closes it.
 *
 * [hint] plays the one-time discovery gesture: the row peeks [SwipeHintPeek] to the left, holds ~600ms, then
 * springs back. It is marked as shown up front (via [onHintShown]) so leaving the page mid-peek cannot repeat it.
 */
@Composable
private fun SwipeableRow(
    txn: Txn,
    defs: List<CategoryDef>,
    hidden: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    hint: Boolean = false,
    onHintShown: () -> Unit = {},
) {
    val haptics = rememberXpHaptics()
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val rows = LocalSwipeRows.current ?: remember { SwipeRows() }
    val actionPx = with(LocalDensity.current) { SwipeActionWidth.toPx() }
    var width by remember { mutableFloatStateOf(1f) }
    val offset = remember(txn.id) { Animatable(0f) }
    val fold = remember(txn.id) { Animatable(1f) }
    var armed by remember(txn.id) { mutableStateOf(false) }
    var deleting by remember(txn.id) { mutableStateOf(false) }
    val settle = spring<Float>(dampingRatio = 0.86f, stiffness = 520f)

    fun close() = scope.launch { if (reduced) offset.snapTo(0f) else offset.animateTo(0f, settle) }
    fun delete() {
        if (deleting) return
        deleting = true
        haptics.thud()
        scope.launch {
            if (!reduced) {
                offset.animateTo(-width, tween(160, easing = XpEase.out))
                fold.animateTo(0f, tween(200, easing = XpEase.out))
            }
            if (rows.openId == txn.id) rows.openId = null
            onDelete()
        }
    }
    // Another row opened (or the list scrolled): fold this one back.
    LaunchedEffect(rows.openId) { if (rows.openId != txn.id && offset.value != 0f && !deleting) close() }

    // The discovery peek: nothing is open, nothing is deleting, the row has never done this before.
    var hinted by remember(txn.id) { mutableStateOf(false) }
    val peekPx = with(LocalDensity.current) { SwipeHintPeek.toPx() }
    // Keyed on the hint flag only: writing [hinted] must not restart (and so cancel) this effect, and the
    // animation runs in the row's own scope so a later recomposition cannot cut it off mid-peek either.
    LaunchedEffect(hint, reduced) {
        if (!hint || hinted || reduced || deleting) return@LaunchedEffect
        hinted = true
        // Marked before the animation: a page left mid-peek must not replay it on the next visit.
        onHintShown()
        scope.launch {
            delay(320) // let the card's entrance land first, so the peek reads as its own gesture
            rows.openId = null
            offset.animateTo(-peekPx, tween(220, easing = XpEase.out))
            delay(600)
            offset.animateTo(0f, settle)
        }
    }

    Box(
        Modifier.fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val h = (placeable.height * fold.value).roundToInt()
                layout(placeable.width, h) { placeable.place(0, 0) }
            }
            .clipToBounds()
            .semantics { customActions = listOf(CustomAccessibilityAction("删除") { delete(); true }) },
    ) {
        // The action behind the row: red from the row's trailing edge, 删除 centred in whatever is revealed.
        val revealed = (-offset.value).coerceAtLeast(0f)
        if (revealed > 0.5f) {
            Box(
                Modifier.matchParentSize().background(XpColors.Negative)
                    .pressableClick(haptic = false) { delete() },
            ) {
                Row(
                    Modifier.align(Alignment.CenterEnd)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minWidth = 0))
                            // Centred in whatever is actually revealed (a 56dp peek still shows the whole 删除);
                            // once armed it rides the row's edge (iOS full swipe).
                            val x = if (armed) (constraints.maxWidth - revealed).roundToInt() + 16
                            else (constraints.maxWidth - revealed / 2f - placeable.width / 2f).roundToInt()
                            layout(constraints.maxWidth, placeable.height) { placeable.place(x, 0) }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Color.White, maxLines = 1)
                }
            }
        }
        Box(
            Modifier.fillMaxWidth()
                .graphicsLayer { translationX = offset.value }
                .pointerInput(txn.id) {
                    // Accumulate here: snapTo runs in a coroutine, so reading offset.value per event drops movement.
                    var x = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { x = offset.value; rows.openId = txn.id },
                        onDragEnd = {
                            val pulled = -x
                            when {
                                pulled > width * FullSwipe -> delete()
                                pulled > actionPx / 2f -> scope.launch { offset.animateTo(-actionPx, settle) }
                                else -> { close(); if (rows.openId == txn.id) rows.openId = null }
                            }
                        },
                        onDragCancel = { close() },
                    ) { change, dx ->
                        change.consume()
                        x = (x + dx).coerceIn(-width, 0f)
                        val nowArmed = -x > width * FullSwipe
                        if (nowArmed != armed) { armed = nowArmed; haptics.tick() }
                        val next = x
                        scope.launch { offset.snapTo(next) }
                    }
                },
        ) {
            LedgerRowContent(
                txn = txn, defs = defs, hidden = hidden, selected = null,
                onToggle = {}, onLongPress = onLongPress,
                onClick = { if (offset.value != 0f) { close(); rows.openId = null } else onClick() },
            )
        }
    }
}

/**
 * The row, one line (tt: 每一个东西一行就行了): 32dp category tile — the tile already says 吃饭 / 出行, so no
 * category name and no source chip — then merchant (else note, else the category), the time, the amount.
 * 48dp, never wraps, never truncates the amount.
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
    val name = txn.merchant.trim().ifEmpty { txn.note.trim() }.ifEmpty { def.label }

    Row(
        Modifier.fillMaxWidth()
            // The row owns its surface: the swipe action paints its red fill behind it.
            .background(if (selected == true) XpColors.AccentSoft.copy(alpha = 0.55f) else XpColors.Card)
            .combinedClickable(
                onClick = { if (multiselect) onToggle() else onClick() },
                onLongClick = { if (multiselect) onToggle() else onLongPress() },
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = XpSpace.l, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multiselect) {
            SelectBox(checked = selected == true)
            Spacer(Modifier.width(XpSpace.m))
        }
        CategoryTile(def, size = 32.dp)
        Spacer(Modifier.width(XpSpace.m))
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = XpColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(XpSpace.s))
        Text(clock, style = TabularSmall, color = XpColors.InkTertiary, maxLines = 1)
        Spacer(Modifier.width(XpSpace.m))
        Text(
            if (hidden) "¥ $AMOUNT_MASK" else (if (income) "+" else "-") + txn.amountCents.yuanGrouped(),
            style = RowAmount,
            color = if (income) XpColors.Accent else XpColors.Ink,
            maxLines = 1,
        )
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
