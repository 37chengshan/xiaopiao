package com.xiaopiao.app.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.xiaopiao.app.ui.theme.XpColors
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 记一笔 ball (docs/APP-DESIGN.md §4.2): tap = manual form, long-press = voice.
 * It carries no state so it can sit on any page that needs a fast entry point.
 */
@Composable
fun RecordBall(onManual: () -> Unit, onVoice: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberXpHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(0.55f, 420f), label = "ballScale")
    // Draggable: the user can park the ball anywhere on its edge (tt#23).
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(
        modifier.size(56.dp)
            .offset { androidx.compose.ui.unit.IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offset = androidx.compose.ui.geometry.Offset(
                        (offset.x + drag.x).coerceIn(-260f, 0f),
                        (offset.y + drag.y).coerceIn(-900f, 0f),
                    )
                }
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape).background(XpColors.Primary)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val long = awaitLongPressOrCancellation(down.id)
                    if (long == null) {
                        haptics.select()
                        onManual()
                    } else {
                        haptics.thud()
                        onVoice()
                        waitForUpOrCancellation()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = "记一笔 · 长按说一句", tint = XpColors.OnPrimary, modifier = Modifier.size(24.dp))
    }
}
