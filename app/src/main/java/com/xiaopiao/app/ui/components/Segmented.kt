package com.xiaopiao.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Segmented control with a permanently glassy thumb.
 *
 * The thumb is a translucent capsule drawn *behind* one static label layer. Nothing samples or
 * refracts the text, so the labels never move, ghost, or double up. Drag the thumb and it follows
 * the finger, ticks as it crosses options, and springs to the nearest one on release.
 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    /** Capsule track and thumb instead of the 10dp-cornered ones. */
    pill: Boolean = false,
) {
    val haptics = rememberXpHaptics()
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val inset = 2.dp
    val count = options.size.coerceAtLeast(1)
    val safeSelected = selected.coerceIn(0, count - 1)

    // Position is in segment units. It updates inside the draw layer (or a drag), never during composition.
    var thumbPos by remember { mutableFloatStateOf(safeSelected.toFloat()) }
    var velocity by remember { mutableFloatStateOf(0f) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var visualIndex by remember { mutableIntStateOf(safeSelected) }
    var dragging by remember { mutableStateOf(false) }
    var pendingTarget by remember { mutableStateOf<Int?>(null) }
    val pop = remember { Animatable(1f) }
    var moveJob by remember { mutableStateOf<Job?>(null) }
    var popJob by remember { mutableStateOf<Job?>(null) }

    fun moveTo(target: Float) {
        moveJob?.cancel()
        moveJob = scope.launch {
            if (reduced) {
                thumbPos = target
                velocity = 0f
            } else {
                val runner = Animatable(thumbPos)
                runner.animateTo(target, spring(dampingRatio = 0.68f, stiffness = 420f)) {
                    thumbPos = value
                    velocity = this.velocity
                }
            }
        }
    }

    fun popThumb() {
        if (reduced) return
        popJob?.cancel()
        popJob = scope.launch {
            pop.snapTo(1f)
            pop.animateTo(1.06f, spring(dampingRatio = 0.5f, stiffness = 900f))
            pop.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 420f))
        }
    }

    fun commit(target: Int) {
        val index = target.coerceIn(0, count - 1)
        val changed = index != safeSelected
        visualIndex = index
        if (changed) {
            pendingTarget = index
            haptics.select()
            onSelect(index)
            popThumb()
        }
        moveTo(index.toFloat())
    }

    // The parent may write the choice asynchronously (theme / launch side / receipt settings). Keep the
    // visual where the user put it until the real value arrives; if it never does, fall back gently.
    LaunchedEffect(safeSelected) {
        if (pendingTarget == safeSelected) pendingTarget = null
        if (!dragging && pendingTarget == null && visualIndex != safeSelected) {
            visualIndex = safeSelected
            moveTo(safeSelected.toFloat())
        }
    }
    LaunchedEffect(pendingTarget) {
        val target = pendingTarget ?: return@LaunchedEffect
        delay(1_000)
        if (pendingTarget == target && safeSelected != target) {
            pendingTarget = null
            visualIndex = safeSelected
            moveTo(safeSelected.toFloat())
        }
    }
    LaunchedEffect(count) {
        if (visualIndex > count - 1) visualIndex = count - 1
        if (thumbPos > count - 1) thumbPos = (count - 1).toFloat()
    }

    BoxWithConstraints(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(if (pill) 50 else 28))
            .background(XpColors.Fill).testTag("segmented"),
    ) {
        val segment = (maxWidth - inset * 2) / count
        val segmentPx = with(LocalDensity.current) { segment.toPx() }.coerceAtLeast(1f)

        val thumbShape = if (pill) RoundedCornerShape(50) else RoundedCornerShape(8.dp)

        // The glass capsule sits *behind* the labels. It has no backdrop sampler, so the text below
        // (here: above) is never copied, refracted, or shifted.
        Box(
            Modifier
                .padding(inset)
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = thumbPos * segmentPx
                    translationY = if (dragging) -2.dp.toPx() else 0f
                    val motion = if (dragging) dragVelocity else velocity
                    val stretch = (abs(motion) * (if (dragging) 0.22f else 0.012f)).coerceAtMost(if (dragging) 0.10f else 0.06f)
                    val tilt = (motion * (if (dragging) 42f else 0.30f)).coerceIn(-7f, 7f)
                    rotationZ = tilt
                    val dragScale = if (dragging) 1.045f else 1f
                    scaleX = pop.value * dragScale + stretch
                    scaleY = pop.value * dragScale - stretch * 0.35f
                }
                .clip(thumbShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            XpColors.Raised.copy(alpha = if (XpColors.isDark) 0.52f else 0.68f),
                            XpColors.Raised.copy(alpha = if (XpColors.isDark) 0.26f else 0.38f),
                            XpColors.Raised.copy(alpha = if (XpColors.isDark) 0.42f else 0.56f),
                        ),
                    ),
                )
                // A specular band over the fill. It rotates and stretches with the thumb, giving the
                // glass a wet highlight without sampling (and therefore without copying) the labels.
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.linearGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (XpColors.isDark) 0.16f else 0.34f),
                                Color.Transparent,
                            ),
                            start = Offset(size.width * 0.18f, 0f),
                            end = Offset(size.width * 0.82f, size.height),
                        ),
                    )
                }
                .glassRim(thumbShape),
        )

        // One static label layer, drawn over the glass. No backdrop copy, no overlapping glyphs.
        Row(Modifier.fillMaxWidth().fillMaxHeight().clearAndSetSemantics { }.padding(inset)) {
            options.forEachIndexed { i, label ->
                val color by animateColorAsState(
                    if (i == visualIndex) XpColors.Ink else XpColors.InkSecondary,
                    tween(140, easing = XpEase.out),
                    label = "segText",
                )
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
                }
            }
        }

        // Taps: transparent targets keep the labels' semantics and never double-draw text.
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(inset)) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .selectable(
                            selected = i == visualIndex,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { commit(i) },
                        )
                        .semantics { contentDescription = label },
                )
            }
        }

        // Drag the thumb itself. It is a separate transparent layer above the taps, so a horizontal drag
        // never competes with the page's own swipe gesture.
        val dragState = rememberDraggableState { delta ->
            thumbPos = (thumbPos + delta / segmentPx).coerceIn(0f, (count - 1).toFloat())
            dragVelocity = delta / segmentPx
            val index = thumbPos.roundToInt().coerceIn(0, count - 1)
            if (index != visualIndex) {
                visualIndex = index
                haptics.tick()
            }
        }
        Box(
            Modifier
                .padding(inset)
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer { translationX = thumbPos * segmentPx }
                .testTag("segmented-thumb")
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        moveJob?.cancel()
                        dragVelocity = 0f
                        dragging = true
                    },
                    onDragStopped = {
                        dragging = false
                        dragVelocity = 0f
                        commit(thumbPos.roundToInt())
                    },
                ),
        )
    }
}
