package com.xiaopiao.app.ui.components

import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import com.xiaopiao.app.ui.motion.XpMotion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpEase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.fillMaxSize

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
    val glassBackdrop = rememberLayerBackdrop()
    // One finger light for the whole control: pressing anywhere lights the thumb, which tilts towards the
    // finger and swells a touch (the tab bar's recipe, see LiquidGlass.kt).
    val thumbLight = rememberGlassLight()
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
                runner.animateTo(target, XpMotion.snappy()) {
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
            pop.animateTo(1.03f, XpMotion.pressIn())
            pop.animateTo(1f, XpMotion.bouncy())
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
            .background(XpColors.Fill)
            // Watched on the Initial pass and never consumed, so the segments keep their taps and the
            // thumb still drags.
            .pointerInput(Unit) { trackGlassLight(thumbLight, scope) }
            .testTag("segmented"),
    ) {
        val segment = (maxWidth - inset * 2) / count
        val segmentPx = with(LocalDensity.current) { segment.toPx() }.coerceAtLeast(1f)
        val insetPx = with(LocalDensity.current) { inset.toPx() }
        // One size for every label: at 360dp / large font the widest label decides, so the row never mixes sizes.
        val baseStyle = MaterialTheme.typography.labelLarge
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val padPx = with(LocalDensity.current) { 8.dp.toPx() }
        val labelSize = remember(options, segmentPx, baseStyle) {
            val widest = options.maxOf { measurer.measure(it, baseStyle, maxLines = 1, softWrap = false).size.width }.coerceAtLeast(1)
            // The selected label is drawn at 1.06×.
            val fit = ((segmentPx - padPx) / 1.06f / widest).coerceAtMost(1f)
            (baseStyle.fontSize.value * fit).coerceAtLeast(9f).sp
        }

        val thumbShape = if (pill) RoundedCornerShape(50) else RoundedCornerShape(8.dp)

        // ── 标签（只画一次，作为玻璃的采样源）─────────────────────────────────────────────
        // Same recipe as the tab bar: the labels are recorded into a layer and the thumb samples that layer with
        // blur + lens, so the text under the glass is *refracted*, not copied — one draw, no doubled glyphs and
        // no shadow. The lens displacement is what magnifies/compresses the字 at the glass edge.
        Row(
            Modifier.fillMaxWidth().fillMaxHeight().clearAndSetSemantics { }.padding(inset)
                .layerBackdrop(glassBackdrop),
        ) {
            options.forEachIndexed { i, label ->
                val color by animateColorAsState(
                    if (i == visualIndex) XpColors.Ink else XpColors.InkSecondary,
                    XpMotion.fade(140),
                    label = "segText",
                )
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    // The selected glyph is drawn *above* the glass (crisp); skipping it here is what keeps
                    // exactly one copy of every字 — the glass only refracts its neighbours at the rim.
                    if (i != visualIndex) {
                        SegmentLabel(label, color, labelSize)
                    }
                }
            }
        }

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
                    // Drag leans the thumb the way the finger throws it; the press tilt below adds the 3D lean.
                    rotationZ = (motion * (if (dragging) 42f else 0.30f)).coerceIn(-7f, 7f)
                    scaleX = pop.value + stretch
                    scaleY = pop.value - stretch * 0.35f
                }
                .liquidGlass(
                    glassBackdrop,
                    thumbShape,
                    film = XpColors.Raised.copy(alpha = if (XpColors.isDark) 0.18f else 0.22f),
                    rim = Color.Black.copy(alpha = if (XpColors.isDark) 0.26f else 0.10f),
                    blur = 4.dp,
                    lensHeight = 10.dp,
                    lensAmount = 14.dp,
                    // No ordinary drop shadow: the rim, the lens and the finger light carry the glass.
                    shadow = 0.dp,
                    fallback = XpColors.Raised,
                    light = thumbLight,
                    track = false,
                    origin = { Offset(insetPx + thumbPos * segmentPx, insetPx) },
                    swell = 1.06f,
                    maxTilt = 7f,
                ),
        )

        // The selected label, on top of the glass: sharp (like the tab bar's icons over its frosted capsule) and
        // a touch magnified, which is the "seen through a thick pane" read without any second copy underneath.
        Row(Modifier.fillMaxWidth().fillMaxHeight().clearAndSetSemantics { }.padding(inset)) {
            options.forEachIndexed { i, label ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (i == visualIndex) {
                        SegmentLabel(label, XpColors.Ink, labelSize, Modifier.graphicsLayer { scaleX = 1.06f; scaleY = 1.06f })
                    }
                }
            }
        }

        // Taps only: the drag is handled by the container above, so a tap here is never eaten by a gesture.
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(inset)) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .semantics {
                            contentDescription = label
                            role = Role.Tab
                            this.selected = i == visualIndex
                            onClick(label = label) { commit(i); true }
                        }
                        // Gesture lives on the segment itself: it is the top-most target at its own x, so the
                        // stats page's horizontal pager (an ancestor) never wins the drag.
                        .pointerInput(i, count) {
                            val segPx = segmentPx
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                var dragged = false
                                var lastX = down.position.x
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val dx = change.position.x - lastX
                                    lastX = change.position.x
                                    if (!dragged && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                                        dragged = true
                                        moveJob?.cancel()
                                        dragVelocity = 0f
                                        dragging = true
                                    }
                                        if (dragged) {
                                        change.consume()
                                        thumbPos = (thumbPos + dx / segPx).coerceIn(0f, (count - 1).toFloat())
                                        dragVelocity = dx / segPx
                                        val idx = thumbPos.roundToInt().coerceIn(0, count - 1)
                                        if (idx != visualIndex) { visualIndex = idx; haptics.tick() }
                                    }
                                }
                                if (dragged) {
                                    dragging = false
                                    dragVelocity = 0f
                                    commit(thumbPos.roundToInt())
                                } else {
                                    commit(i)
                                }
                            }
                        },
                )
            }
        }

    }
}

/** One segment's label at the row's shared [size], so it fits instead of being cut ("Weekl", "Custo"). */
@Composable
private fun SegmentLabel(label: String, color: Color, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    Text(label, modifier = modifier, style = MaterialTheme.typography.labelLarge.copy(fontSize = size), color = color, maxLines = 1, softWrap = false)
}
