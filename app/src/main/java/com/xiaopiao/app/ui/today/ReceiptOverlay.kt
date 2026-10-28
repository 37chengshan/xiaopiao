package com.xiaopiao.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xiaopiao.app.ui.components.pressableClick
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

private val TearThreshold = 120.dp
private val SlotWidth = PaperWidth + 36.dp
private val SlotHeight = 14.dp
private val SlotTop = 10.dp

/**
 * Full-screen print stage (docs/DESIGN.md §7): scrim, the slot at the top, the paper hanging out of it and the
 * bottom status capsule. All motion values are read lazily in layout / graphicsLayer, so frames never recompose.
 */
@Composable
fun ReceiptOverlay(
    session: PrintSession,
    lines: List<ReceiptLine>,
    timeline: PrintTimeline,
    stampText: String,
    onTear: (velocityPx: Float) -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = session.phase
    if (phase == PrintPhase.Closed) return
    val density = LocalDensity.current
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val clock = remember(timeline, phase) {
        PrintClock(timeline, instant = phase == PrintPhase.Printed || phase == PrintPhase.Tearing) { session.elapsed.value.toLong() }
    }
    val stamp = remember(session) { StampVisuals({ session.stampScale.value }, { session.stampAlpha.value }, { session.stampRing.value }) }
    // Long receipts shrink to fit between the slot and the capsule instead of running off screen.
    val topInset = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    val fit = ((screenH - topInset - bottomInset - SlotTop - 16.dp - 96.dp).value / (timeline.totalHeightDp + PaperDepth.value)).coerceIn(0.5f, 1f)
    val thresholdPx = with(density) { TearThreshold.toPx() }
    val screenPx = with(density) { screenH.toPx() }
    val latestTear by rememberUpdatedState(onTear)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false),
    ) {
    (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(phase) {
                // Swallow touches behind the overlay; while Printed, a vertical drag moves the paper 1:1.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (phase != PrintPhase.Printed) return@awaitEachGesture
                    val tracker = VelocityTracker()
                    session.dragStart()
                    var travel = 0f
                    val slop = awaitVerticalTouchSlopOrCancellation(down.id) { change, over -> change.consume(); travel += over }
                        ?: return@awaitEachGesture
                    session.dragTo(travel, thresholdPx, screenPx)
                    tracker.addPosition(slop.uptimeMillis, slop.position)
                    val finished = verticalDrag(slop.id) { change ->
                        travel += change.positionChange().y
                        tracker.addPosition(change.uptimeMillis, change.position)
                        session.dragTo(travel, thresholdPx, screenPx)
                        change.consume()
                    }
                    val v = tracker.calculateVelocity().y
                    if (finished) session.dragEnd(v, thresholdPx, screenPx) { latestTear(v) }
                    else session.dragEnd(0f, Float.MAX_VALUE, screenPx) {}
                }
            },
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = session.present.value }.background(XpColors.Scrim))

        // Slot (always above the paper so the sheet reads as coming out of it).
        Box(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = SlotTop).zIndex(2f)
                .graphicsLayer { alpha = session.present.value; translationY = -8.dp.toPx() * (1f - session.present.value) }
                .width(SlotWidth).height(SlotHeight).clip(RoundedCornerShape(SlotHeight / 2))
                .background(XpColors.PrinterBody),
        ) {
            Box(Modifier.align(Alignment.Center).width(SlotWidth - 16.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(XpColors.PrinterSlit))
        }

        // Paper: starts at the slot's centre line and grows downward.
        Box(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = SlotTop + SlotHeight / 2)
                .graphicsLayer {
                    translationY = session.dragY + session.kick.value * density.density
                    rotationZ = session.tilt
                    scaleX = fit; scaleY = fit
                    alpha = session.paperAlpha * session.present.value
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .semantics { contentDescription = "今日小票，向下拖动可撕下" },
        ) {
            ReceiptPaper(lines, clock, stampText, stamp, session.tornTop)
        }

        StatusCapsule(
            phase = phase,
            onSkip = onSkip,
            onTear = { latestTear(0f) },
            onClose = onClose,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = XpSpace.xl).zIndex(3f)
                .graphicsLayer { alpha = session.present.value; translationY = 16.dp.toPx() * (1f - session.present.value) },
        )
    }
    }
}

/** Bottom glass capsule: 正在打印…  跳过  /  向下拖动撕下小票  撕下  ✕. */
@Composable
private fun StatusCapsule(
    phase: PrintPhase,
    onSkip: () -> Unit,
    onTear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape).background(XpColors.Card.copy(alpha = 0.95f)).border(0.5.dp, XpColors.Hairline, shape)
            .heightIn(min = 52.dp).padding(start = XpSpace.l, end = XpSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (phase) {
            PrintPhase.Printing -> {
                Text("正在打印小票…", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary)
                CapsuleAction("跳过 ✕", "跳过打印动画", onSkip)
            }
            PrintPhase.Printed -> {
                Text("向下拖动撕下小票", style = MaterialTheme.typography.bodyMedium, color = XpColors.Ink)
                CapsuleAction("撕下", "撕下小票并分享", onTear, accent = true)
                CapsuleAction("✕", "关闭小票", onClose)
            }
            else -> Text("咔～", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, modifier = Modifier.padding(end = XpSpace.l))
        }
    }
}

@Composable
private fun CapsuleAction(label: String, description: String, onClick: () -> Unit, accent: Boolean = false) {
    Box(
        Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).pressableClick(onClick = onClick).padding(horizontal = XpSpace.m)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (accent) XpColors.Accent else XpColors.InkSecondary)
    }
}
