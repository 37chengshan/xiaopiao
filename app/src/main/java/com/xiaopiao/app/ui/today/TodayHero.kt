package com.xiaopiao.app.ui.today

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.xiaopiao.app.ui.components.Pill
import com.xiaopiao.app.ui.components.XpButton
import com.xiaopiao.app.ui.components.XpButtonStyle
import com.xiaopiao.app.ui.theme.XpColors
import com.xiaopiao.app.ui.theme.XpSpace

private val TearThreshold = 110.dp
private val PullThreshold = 84.dp

/** Printer + paper stage: handles pull-to-print and swipe-up-to-tear gestures and the paper's physics. */
@Composable
fun ReceiptStage(
    session: PrintSession,
    lines: List<ReceiptLine>,
    timeline: PrintTimeline,
    stampText: String,
    layer: GraphicsLayer,
    expanded: Boolean,
    onToggleMore: () -> Unit,
    onPrint: () -> Unit,
    onTear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val phase = session.phase
    val clock = remember(timeline, phase) {
        PrintClock(timeline, instant = phase == PrintPhase.Printed || phase == PrintPhase.Tearing) { session.elapsed.value.toLong() }
    }
    val stamp = remember(session) { StampVisuals({ session.stampScale.value }, { session.stampAlpha.value }, { session.stampRing.value }) }
    val pullPx = with(density) { PullThreshold.toPx() }
    val tearPx = with(density) { TearThreshold.toPx() }

    Box(
        modifier
            .fillMaxWidth()
            .directionalDrag(
                up = false, enabled = phase == PrintPhase.Idle,
                onDrag = { session.onPull(it, pullPx) },
                onEnd = { total, ok -> session.onPullEnd(if (ok) total else 0f, pullPx, onPrint) },
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.padding(top = PrinterHeight - 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (phase) {
                PrintPhase.Idle -> IdleStub(session)
                PrintPhase.Gone -> Box(Modifier.height(20.dp))
                else -> ReceiptPaper(
                    lines, clock, stampText, stamp, layer, tornTop = session.tornTop, onToggleMore = onToggleMore,
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = session.dragY.value * 0.9f + session.tearY.value + session.kick.value * density.density
                            rotationZ = session.tearRot.value + session.dragY.value / 90f
                            alpha = session.tearAlpha.value
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        .directionalDrag(
                            up = true, enabled = phase == PrintPhase.Printed,
                            onDrag = { session.onTearDrag(it, tearPx) },
                            onEnd = { total, ok -> session.onTearDragEnd(if (ok) total else 0f, tearPx, onTear) },
                        ),
                )
            }
        }
        ReceiptPrinter(
            printing = phase == PrintPhase.Printing,
            modifier = Modifier.zIndex(1f).graphicsLayer { translationY = session.pull.value * 0.22f },
        )
    }
}

/** The little blank paper tongue peeking out of the idle printer; it lengthens as you pull. */
@Composable
private fun IdleStub(session: PrintSession) {
    val h = with(LocalDensity.current) { session.pull.value.toDp() } + 18.dp
    Canvas(Modifier.width(PaperWidth - 30.dp).height(h)) {
        drawPath(paperPath(size.width, size.height, 11.dp.toPx(), 5.dp.toPx(), false), XpColors.Paper)
    }
}

/** Buttons and hints under the stage; they change with the print phase. */
@Composable
fun StageControls(
    phase: PrintPhase,
    isToday: Boolean,
    demo: Boolean,
    canOfferDemo: Boolean,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onDemo: () -> Unit,
    onExitDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = XpSpace.l, vertical = XpSpace.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XpSpace.s),
    ) {
        when (phase) {
            PrintPhase.Idle -> {
                XpButton(if (isToday) "打印今日小票" else "打印这天的小票", onPrint, Modifier.fillMaxWidth(), leading = "🖨️")
                Hint("↓ 往下拉一下也能出票", bobDown = true)
                if (demo) Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill("演示数据")
                    XpButton("退出演示", onExitDemo, style = XpButtonStyle.Plain)
                } else if (canOfferDemo) XpButton("看看示例小票", onDemo, style = XpButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
            }
            PrintPhase.Printing -> Text("滋滋滋… 正在出票", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, modifier = Modifier.padding(vertical = XpSpace.m))
            PrintPhase.Printed -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(XpSpace.s), verticalAlignment = Alignment.CenterVertically) {
                    XpButton("撕下并分享", onShare, Modifier.weight(1f), leading = "✂️")
                    XpButton("重印", onPrint, style = XpButtonStyle.Secondary)
                }
                Hint("↑ 在小票上往上一滑，也能撕下来", bobDown = false)
                if (demo) Pill("演示数据")
            }
            PrintPhase.Tearing -> Text("咔嚓～", style = MaterialTheme.typography.bodyMedium, color = XpColors.InkSecondary, modifier = Modifier.padding(vertical = XpSpace.m))
            PrintPhase.Gone -> XpButton("再印一张", onPrint, Modifier.fillMaxWidth(), leading = "🖨️")
        }
    }
}

@Composable
private fun Hint(text: String, bobDown: Boolean) {
    val bob = rememberInfiniteTransition(label = "hint").animateFloat(
        0f, 1f, infiniteRepeatable(tween(900, easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)), RepeatMode.Reverse), label = "hintBob",
    )
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = XpColors.InkSecondary,
        modifier = Modifier.graphicsLayer { translationY = (if (bobDown) 1 else -1) * 3.dp.toPx() * bob.value },
    )
}
