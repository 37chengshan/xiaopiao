package com.xiaopiao.app.ui.components

import androidx.compose.foundation.gestures.snapping.SnapPosition
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
    val centred by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: selectedIndex
        }
    }
    // A tick per item crossing the centre while the finger (or fling) moves the row.
    LaunchedEffect(state) {
        snapshotFlow { centred }.drop(1).distinctUntilChanged().collect { if (state.isScrollInProgress) haptics.tick() }
    }
    // When the row settles, whatever sits in the centre is the choice.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().filter { !it }.drop(1).collect {
            if (centred in items.indices) select(centred)
        }
    }
    // Outside changes (a calendar pick, a reset) glide the row to the new choice.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices && selectedIndex != centred && !state.isScrollInProgress) state.animateScrollToItem(selectedIndex)
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Side padding lets the first and last items reach the centre.
        val side = ((maxWidth - itemWidth) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(items) { index, item ->
                val on = index == centred
                Box(
                    Modifier.width(itemWidth)
                        .graphicsLayer {
                            val k = if (on) 1f else 0.9f
                            scaleX = k; scaleY = k; alpha = if (on) 1f else 0.62f
                        }
                        .semantics { role = Role.Tab; selected = index == selectedIndex; contentDescription = label(item) }
                        .pressableClick(haptic = false) {
                            if (index != centred) scope.launch { state.animateScrollToItem(index) }
                            select(index)
                        },
                    contentAlignment = Alignment.Center,
                ) { itemContent(item, on) }
            }
        }
    }
}
