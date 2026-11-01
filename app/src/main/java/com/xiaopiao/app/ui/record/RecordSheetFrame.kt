package com.xiaopiao.app.ui.record

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.xiaopiao.app.ui.components.Grabber
import com.xiaopiao.app.ui.components.GlassSurfaceAlpha
import com.xiaopiao.app.ui.components.LocalSheetGlass
import com.xiaopiao.app.ui.components.glassRim
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberWindowBlurBehind
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import com.xiaopiao.app.ui.theme.XpRadius
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos

/**
 * The 记一笔 sheet's own window. M3's ModalBottomSheet hard-codes its hide spec (FastEffects), so the
 * exit here is ours: a short lift, then a spring drop with a little rebound. The drag lives on the
 * grabber only, so the keypad and the category grid keep their own gestures.
 */
@Composable
internal fun RecordSheetFrame(
    closing: Boolean,
    onRequestClose: () -> Unit,
    onClosed: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(topStart = XpRadius.sheet, topEnd = XpRadius.sheet)
    var heightPx by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragJob by remember { mutableStateOf<Job?>(null) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // One frame first: the sheet's keypad and grids are composed and measured on this frame, so the move
        // itself starts from a laid-out surface instead of stuttering on frame one. Then a plain 200ms ease-out
        // (no spring overshoot: on a full-height sheet the rebound reads as jitter, not bounce).
        withFrameNanos { }
        if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(200, easing = XpEase.out))
    }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        // The first frame can arrive before the sheet has been measured. Wait for its height so the
        // exit starts from a real position instead of flashing at the bottom edge.
        if (heightPx == 0) androidx.compose.runtime.snapshotFlow { heightPx }.first { it > 0 }
        val height = heightPx.toFloat().coerceAtLeast(1f)
        val visible = (progress.value - dragOffset / height).coerceIn(0f, 1f)
        dragOffset = 0f
        progress.snapTo(visible)
        if (reduced) {
            progress.animateTo(0f, tween(120, easing = XpEase.out))
        } else {
            // Lift a hair before dropping only when the sheet is still fully open. A half-dragged
            // sheet should continue in the direction the finger left it.
            if (visible > 0.98f) progress.animateTo(1.008f, tween(40, easing = XpEase.out))
            progress.animateTo(
                0f,
                spring(dampingRatio = 0.92f, stiffness = 1600f, visibilityThreshold = 0.0001f),
            )
        }
        onClosed()
    }

    Dialog(
        onDismissRequest = onRequestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
        // Blur only once the sheet has landed (derivedStateOf: reading progress.value directly here would
        // recompose the whole sheet every frame of its own animation).
        val settled by remember { derivedStateOf { progress.value > 0.995f } }
        val blurred = rememberWindowBlurBehind(enabled = settled)
        Box(Modifier.fillMaxSize()) {
            // Scrim fades with the sheet, including while it is dragged down. The alpha is a draw-phase
            // read, so dragging never recomposes the page.
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        val h = heightPx.toFloat().coerceAtLeast(1f)
                        alpha = if (heightPx == 0) 0f
                        else ((progress.value * h - dragOffset) / h).coerceIn(0f, 1f)
                    }
                    .background(XpColors.Scrim.copy(alpha = XpColors.Scrim.alpha * 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRequestClose,
                    ),
            )
            Box(
                Modifier.fillMaxSize().imePadding().navigationBarsPadding(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { heightPx = it.height }
                        .graphicsLayer {
                            translationY = (1f - progress.value) * heightPx + dragOffset
                            alpha = if (heightPx == 0) 0f else 1f
                        }
                        .clip(shape)
                        .background(if (blurred) XpColors.Card.copy(alpha = GlassSurfaceAlpha) else XpColors.Card)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .then(if (blurred) Modifier.glassRim(shape) else Modifier),
                ) {
                    val dragState = rememberDraggableState { delta ->
                        dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                    }
                    Box(
                        Modifier.fillMaxWidth().draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            enabled = !closing,
                            onDragStarted = {
                                dragJob?.cancel()
                            },
                            onDragStopped = { velocity ->
                                val travel = dragOffset
                                if (travel > heightPx * 0.22f || velocity > 900f) {
                                    onRequestClose()
                                } else {
                                    dragJob?.cancel()
                                    dragJob = scope.launch {
                                        val runner = Animatable(travel)
                                        runner.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 420f)) {
                                            dragOffset = value
                                        }
                                    }
                                }
                            },
                        ),
                    ) { Grabber() }
                    CompositionLocalProvider(LocalSheetGlass provides blurred) {
                        content()
                    }
                }
            }
        }
    }
}
