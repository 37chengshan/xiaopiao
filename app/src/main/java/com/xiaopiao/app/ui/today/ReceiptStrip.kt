package com.xiaopiao.app.ui.today

import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xiaopiao.app.ui.components.AnimatedNavGlyph
import com.xiaopiao.app.ui.components.NavGlyph
import com.xiaopiao.app.ui.components.rememberReducedMotion
import com.xiaopiao.app.ui.components.rememberXpHaptics
import com.xiaopiao.app.ui.theme.XpColors

/**
 * The resident receipt button: the same line glyph as the 小票 tab, sized like every other top action (tt: 小票就图标).
 * Tap = print today (the ceremony), long-press = the 小票页. Lives in the top-right of every root page, left of
 * the gear (docs/APP-DESIGN.md §3.1); pages only place it, all behaviour comes from [LocalReceiptHost].
 */
@Composable
fun ReceiptStrip(modifier: Modifier = Modifier) {
    val host = LocalReceiptHost.current
    val haptics = rememberXpHaptics()
    val reduced = rememberReducedMotion()
    val cents = host?.todayCents ?: 0L
    val text = remember(cents) { StripText.format(cents) }

    var bounds by remember { mutableStateOf<Rect?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val press by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.88f else 1f, spring(0.55f, 450f), label = "stripPress")
    val centre = { bounds?.center }

    Box(
        modifier
            .size(40.dp)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .semantics {
                contentDescription = "今日小票 $text，点按打印，长按打开小票页"
                onClick("打印今日小票") { host?.print(from = centre()); true }
                onLongClick("打开小票页") { host?.openReceiptPage(); true }
            }
            .pointerInput(host) {
                if (host == null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val long = awaitLongPressOrCancellation(down.id)
                    if (long == null) {
                        pressed = false
                        haptics.select()
                        host.print(from = centre())
                    } else {
                        haptics.thud()
                        host.openReceiptPage()
                        waitForUpOrCancellation()
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedNavGlyph(
            NavGlyph.Receipt, selected = false, tint = XpColors.Ink, reduced = reduced, replay = 0,
            modifier = Modifier.size(20.dp).graphicsLayer { scaleX = press; scaleY = press },
        )
    }
}
