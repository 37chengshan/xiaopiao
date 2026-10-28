package com.xiaopiao.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpSpace
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** One toast request; [id] makes repeated identical messages re-trigger the timer. */
@Stable
data class ToastData(
    val id: Long,
    val message: String,
    val actionLabel: String?,
    val durationMs: Long,
    val onAction: () -> Unit,
)

/** Holder for the single visible toast. Call [show] from anywhere; render it with [UndoToastHost]. */
@Stable
class UndoToastState {
    var current by mutableStateOf<ToastData?>(null)
        private set
    private var counter = 0L

    fun show(message: String, actionLabel: String? = null, durationMs: Long = 4000, onAction: () -> Unit = {}) {
        current = ToastData(++counter, message, actionLabel, durationMs, onAction)
    }

    fun dismiss() { current = null }
}

/** Remembers an [UndoToastState]. */
@Composable
fun rememberUndoToastState(): UndoToastState = remember { UndoToastState() }

/**
 * Bottom capsule toast with an optional action (e.g. "撤销"). Auto-dismisses and can be swiped sideways away.
 * Put it in a Box and align it to the bottom: `UndoToastHost(state, Modifier.align(Alignment.BottomCenter))`.
 */
@Composable
fun UndoToastHost(state: UndoToastState, modifier: Modifier = Modifier) {
    val data = state.current
    var last by remember { mutableStateOf<ToastData?>(null) }
    if (data != null) last = data
    val haptics = rememberXpHaptics()

    LaunchedEffect(data?.id) {
        if (data != null) {
            haptics.select()
            delay(data.durationMs)
            state.dismiss()
        }
    }
    Box(modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.l), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = data != null,
            enter = slideInVertically(tween(220, easing = XpEase.out)) { it * 2 } + fadeIn(tween(180, easing = XpEase.out)),
            exit = slideOutVertically(tween(160, easing = XpEase.out)) { it * 2 } + fadeOut(tween(120)),
        ) {
            last?.let { ToastCapsule(it, onDismiss = state::dismiss) }
        }
    }
}

@Composable
private fun ToastCapsule(data: ToastData, onDismiss: () -> Unit) {
    val dx = remember(data.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Row(
        Modifier
            .offset { IntOffset(dx.value.roundToInt(), 0) }
            .alpha(1f - (abs(dx.value) / 600f).coerceIn(0f, 0.8f))
            .pointerInput(data.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(dx.value) > 140.dp.toPx()) {
                                dx.animateTo(if (dx.value > 0) 1200f else -1200f, spring(1f, 400f))
                                onDismiss()
                            } else dx.animateTo(0f, spring(0.6f, 500f))
                        }
                    },
                ) { change, delta -> change.consume(); scope.launch { dx.snapTo(dx.value + delta) } }
            }
            .clip(RoundedCornerShape(50))
            .background(XpColors.ToastBackground)
            .padding(start = XpSpace.l + 2.dp, end = XpSpace.m, top = XpSpace.m, bottom = XpSpace.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(data.message, style = MaterialTheme.typography.bodyMedium, color = XpColors.ToastText, modifier = Modifier.padding(end = XpSpace.m))
        if (data.actionLabel != null) {
            Text(
                data.actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = XpColors.ToastText,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(XpColors.ToastText.copy(alpha = 0.16f))
                    .pressableClick { data.onAction(); onDismiss() }
                    .padding(horizontal = XpSpace.m, vertical = XpSpace.s),
            )
        }
    }
}
