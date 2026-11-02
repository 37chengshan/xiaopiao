package com.xiaopiao.app.ui.wallet

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import com.xiaopiao.app.ui.motion.XpMotion
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.theme.XpColors

/**
 * 卡包的三维卡堆（tt 2026-10-30，2026-11-01 改为参考 vivo 卡包的方向）：最前面的卡在最下面完整露出，后面的卡
 * 往上叠、**只露出顶部一条** ~1/4–1/3 卡高；越往后越窄、越暗、越向后仰，读起来像一摞实体卡，而不是一列列表。
 *
 * - 点露出来的那条 → 那张卡拿到最前面（按身份做动画，不跳）；
 * - 竖直拖 → 一次翻一张（往下拉 = 上面那张后卡下来）；
 * - 前卡是自己的可点区域（详情、显示卡号、复制），这里只管露出来的那几条。
 */
@Composable
fun <T> CardStack(
    items: List<T>,
    frontIndex: Int,
    onBringToFront: (T) -> Unit,
    modifier: Modifier = Modifier,
    frontHeight: Dp = 240.dp,
    /** How much of each card underneath shows below the one above it (≈1/4–1/3 of [frontHeight]). */
    peek: Dp = 68.dp,
    /** Cards deeper than this are drawn off-pile; dragging still walks the pile one card at a time. */
    maxVisible: Int = 3,
    label: (T) -> String = { "" },
    front: @Composable (T) -> Unit,
    /** The top [peek] of a card that is not in front; it is clipped to that strip, never a whole card. */
    peekContent: @Composable (T) -> Unit,
) {
    val reduced = rememberReducedMotion()
    val count = items.size
    val safeFront = if (count == 0) 0 else frontIndex.mod(count)
    val visibleBehind = (count - 1).coerceIn(0, maxVisible)
    var drag by remember { mutableFloatStateOf(0f) }

    fun step(direction: Int) {
        if (count <= 1) return
        onBringToFront(items[(safeFront + direction).mod(count)])
    }

    fun depthOf(index: Int): Int = (index - safeFront + count).mod(count)

    Box(
        modifier
            .fillMaxWidth()
            .height(frontHeight + peek * visibleBehind)
            // No clipToBounds: nearer cards already cover the deeper ones, and a clip cut every card's 12dp shadow into
            // straight edges — the rectangle tt kept seeing around the pile (2026-11-02).
            .pointerInput(count, safeFront, peek) {
                if (count <= 1) return@pointerInput
                val threshold = peek.toPx() * 0.55f
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        // Pull the pile down → the card behind comes forward; push up → the next one.
                        if (drag > threshold) step(1) else if (drag < -threshold) step(-1)
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { change, dy ->
                    change.consume()
                    drag = (drag + dy).coerceIn(-threshold * 1.6f, threshold * 1.6f)
                }
            },
    ) {
        // Deepest first, so the front card is the top-most child. Each card owns its depth by identity, so
        // bringing one forward animates the pile instead of swapping pixels.
        // Layout (tt 2026-11-01, vivo 卡包参考): cards further back sit *higher*, each showing its top [peek]
        // above the next; the front card is at the bottom, whole. Every card is drawn full size and the nearer
        // card covers the rest, so edges stay consistent while it travels.
        val order = items.indices.sortedByDescending { depthOf(it) }
        order.forEach { index ->
            val item = items[index]
            key(index) {
                val depth = depthOf(index)
                val isFront = depth == 0
                val shown = depth <= maxVisible
                val slot = if (shown) visibleBehind - depth.coerceAtMost(visibleBehind) else 0
                val y by animateDpAsState(peek * slot, XpMotion.snappy(Dp.VisibilityThreshold), label = "stackY")
                val far by animateFloatAsState(depth.coerceAtMost(maxVisible + 1).toFloat(), XpMotion.snappy(0.001f), label = "stackDepth")
                val lean = if (reduced) 0f else drag * 0.12f * (if (isFront) 1f else 0.25f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .offset(y = y)
                        .height(frontHeight)
                        .graphicsLayer {
                            translationY = lean
                            // Perspective: further = narrower and tipped back from its bottom edge, so the pile
                            // recedes upwards like real cards in a wallet.
                            val s = 1f - 0.05f * far
                            scaleX = s
                            scaleY = s
                            rotationX = 5f * far
                            cameraDistance = 10f * density
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            alpha = if (shown) 1f else (1f - (far - maxVisible)).coerceIn(0f, 1f)
                        }
                        // Further cards sit a touch darker — a veil, not transparency (alpha would let the card
                        // above show through and read as a double image).
                        .drawWithContent {
                            drawContent()
                            // Same 16dp corners as the card face, or the veil shows as a sharp dark rectangle.
                            if (far > 0.01f) drawRoundRect(Color.Black.copy(alpha = (0.09f * far).coerceAtMost(0.3f)), cornerRadius = CornerRadius(16.dp.toPx()))
                        }
                ) {
                    if (isFront) front(item) else peekContent(item)
                    // Only the strip that shows is tappable; the rest of this card lies under the nearer ones.
                    if (!isFront) {
                        Box(
                            Modifier.fillMaxWidth().height(peek)
                                .semantics { contentDescription = label(item) }
                                .pressableClick(haptic = false, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) { onBringToFront(item) },
                        )
                    }
                }
            }
        }
    }
}

/** Height the panel must reserve for a stack of [count] cards. */
fun stackHeight(frontHeight: Dp, peek: Dp, count: Int, maxVisible: Int = 3): Dp =
    frontHeight + peek * (count - 1).coerceIn(0, maxVisible)
