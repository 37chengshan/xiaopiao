package com.xiaopiao.app.ui.components

import androidx.compose.animation.core.Animatable
import com.xiaopiao.app.ui.motion.XpMotion
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Horizontal snap picker (tt: 日期那种左右滑动吸附选择): swipe and the item that comes to rest in the centre is the
 * choice, with a tick as each item passes the centre; tapping an item glides it to the centre and picks it.
 * Items off-centre shrink and fade a touch, so the centre reads as the selection without extra chrome.
 * [itemContent] gets `centred` (the item currently at the centre while dragging) to draw its own selected state.
 */
@Composable
fun <T> SnapPicker(
    items: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 48.dp,
    spacing: Dp = 8.dp,
    label: (T) -> String,
    itemContent: @Composable (item: T, centred: Boolean) -> Unit,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    val scope = rememberCoroutineScope()
    val haptics = rememberXpHaptics()
    val select by rememberUpdatedState(onSelect)
    // A tap glides the row to its target; while that happens the target keeps the selected look so the
    // highlight never sweeps across the items in between. A finger drag clears it and follows the centre.
    var programmaticTarget by remember { mutableStateOf<Int?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val centred by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: selectedIndex
        }
    }
    val highlighted = programmaticTarget ?: centred
    val bounce = remember { Animatable(1f) }
    var bounceIndex by remember { mutableIntStateOf(-1) }

    // A real finger drag owns the selection; a programmatic glide does not.
    LaunchedEffect(state) {
        state.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    dragging = true
                    programmaticTarget = null
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> dragging = false
            }
        }
    }
    // A tick per item crossing the centre while the finger (or fling) moves the row.
    LaunchedEffect(state) {
        snapshotFlow { centred }.drop(1).distinctUntilChanged().collect {
            if (state.isScrollInProgress && programmaticTarget == null) haptics.tick()
        }
    }
    // When the row settles, whatever sits in the centre is the choice. The tap already committed its
    // target, so a programmatic glide only consumes the flag.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().filter { !it }.drop(1).collect {
            if (programmaticTarget != null) {
                programmaticTarget = null
            } else if (centred in items.indices) {
                select(centred)
            }
            if (centred in items.indices) {
                bounceIndex = centred
                bounce.snapTo(1.08f)
                bounce.animateTo(1f, XpMotion.bouncy())
            }
        }
    }
    // Outside changes (a calendar pick, a reset) glide the row to the new choice.
    LaunchedEffect(selectedIndex) {
        if (programmaticTarget == selectedIndex) return@LaunchedEffect
        if (selectedIndex in items.indices && selectedIndex != centred && !state.isScrollInProgress) {
            programmaticTarget = selectedIndex
            state.animateScrollToItem(selectedIndex)
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Side padding lets the first and last items reach the centre.
        val side = ((maxWidth - itemWidth) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxWidth().testTag("snap-picker"),
        ) {
            itemsIndexed(items) { index, item ->
                val on = index == highlighted
                Box(
                    Modifier.width(itemWidth).testTag("snap-item-$index")
                        .graphicsLayer {
                            val k = if (on) 1f else 0.9f
                            val p = if (index == bounceIndex) bounce.value else 1f
                            scaleX = k * p; scaleY = k * p; alpha = if (on) 1f else 0.62f
                        }
                        .semantics { role = Role.Tab; selected = index == highlighted; contentDescription = label(item) }
                        .pressableClick(haptic = false) {
                            if (index == centred) return@pressableClick
                            programmaticTarget = index
                            select(index)
                            scope.launch { state.animateScrollToItem(index) }
                        },
                    contentAlignment = Alignment.Center,
                ) { itemContent(item, on) }
            }
        }
    }
}
