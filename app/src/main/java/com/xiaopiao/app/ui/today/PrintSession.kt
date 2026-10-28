package com.xiaopiao.app.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import com.xiaopiao.app.ui.components.XpHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PrintPhase { Idle, Printing, Printed, Tearing, Gone }

/**
 * State machine + animatables for one printer: Idle -> Printing -> Printed -> Tearing -> Gone.
 * Animation values are Animatables read lazily by draw/layout blocks, so frames never recompose the screen.
 */
@Stable
class PrintSession(private val scope: CoroutineScope, private val haptics: XpHaptics) {
    var phase by mutableStateOf(PrintPhase.Idle); private set
    var tornTop by mutableStateOf(false); private set

    /** Print clock in ms (linear on purpose: the paper feed is the only linear motion in the app). */
    val elapsed = Animatable(0f)
    val stampScale = Animatable(1.6f)
    val stampAlpha = Animatable(0f)
    val stampRing = Animatable(0f)
    /** Little downward kick the paper takes when the stamp lands. */
    val kick = Animatable(0f)
    /** Pull-down on the idle printer, px. */
    val pull = Animatable(0f)
    /** Live upward drag while tearing, px (<= 0). */
    val dragY = Animatable(0f)
    val tearY = Animatable(0f)
    val tearRot = Animatable(0f)
    val tearAlpha = Animatable(1f)

    private var job: Job? = null
    private var thresholdCrossed = false

    private suspend fun resetVisuals() {
        elapsed.snapTo(0f); stampScale.snapTo(1.6f); stampAlpha.snapTo(0f); stampRing.snapTo(0f); kick.snapTo(0f)
        dragY.snapTo(0f); tearY.snapTo(0f); tearRot.snapTo(0f); tearAlpha.snapTo(1f)
        tornTop = false; thresholdCrossed = false
    }

    /** Back to the quiet printer. */
    fun reset() {
        job?.cancel()
        job = scope.launch { resetVisuals(); pull.snapTo(0f); phase = PrintPhase.Idle }
    }

    fun start(timeline: PrintTimeline, reduced: Boolean) {
        job?.cancel()
        job = scope.launch {
            resetVisuals()
            phase = PrintPhase.Printing
            if (reduced || timeline.totalMs == 0L) {
                elapsed.snapTo(timeline.totalMs.toFloat()); stampScale.snapTo(1f); stampAlpha.snapTo(1f)
                phase = PrintPhase.Printed
                return@launch
            }
            coroutineScope {
                launch { elapsed.animateTo(timeline.feedEndMs.toFloat(), tween(timeline.feedEndMs.toInt(), easing = LinearEasing)) }
                launch { tickHaptics(timeline) }
            }
            delay(timeline.stampStartMs - timeline.feedEndMs)
            slam()
            phase = PrintPhase.Printed
        }
    }

    private suspend fun tickHaptics(timeline: PrintTimeline) {
        var last = 0L
        for (s in timeline.steps) {
            if (!s.ticks) continue
            delay(s.startMs - last); last = s.startMs
            haptics.tick()
        }
    }

    /** Stamp slams down: 1.6x -> 1x on an underdamped spring, ink ring spreads, paper kicks, strong haptic. */
    private suspend fun slam() = coroutineScope {
        stampScale.snapTo(1.6f); stampAlpha.snapTo(0f); stampRing.snapTo(0f)
        launch { stampAlpha.animateTo(1f, tween(70)) }
        launch { stampRing.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) }
        launch {
            delay(85)
            haptics.thud()
            kick.snapTo(7f)
            kick.animateTo(0f, spring(0.3f, 650f))
        }
        stampScale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
    }

    // ---- pull to print -------------------------------------------------

    fun onPull(totalPx: Float, thresholdPx: Float) {
        scope.launch { pull.snapTo(totalPx.coerceIn(0f, thresholdPx * 1.6f)) }
        if (totalPx >= thresholdPx && !thresholdCrossed) { thresholdCrossed = true; haptics.threshold() }
        if (totalPx < thresholdPx) thresholdCrossed = false
    }

    fun onPullEnd(totalPx: Float, thresholdPx: Float, onTrigger: () -> Unit) {
        thresholdCrossed = false
        scope.launch { pull.animateTo(0f, spring(0.55f, 420f)) }
        if (totalPx >= thresholdPx) onTrigger()
    }

    // ---- tear to share ---------------------------------------------------

    fun onTearDrag(totalPx: Float, thresholdPx: Float) {
        scope.launch { dragY.snapTo(totalPx.coerceAtMost(0f)) }
        if (-totalPx >= thresholdPx && !thresholdCrossed) { thresholdCrossed = true; haptics.threshold() }
        if (-totalPx < thresholdPx) thresholdCrossed = false
    }

    fun onTearDragEnd(totalPx: Float, thresholdPx: Float, tear: () -> Unit) {
        thresholdCrossed = false
        if (-totalPx >= thresholdPx) tear() else scope.launch { dragY.animateTo(0f, spring(0.5f, 380f)) }
    }

    /**
     * Rips the sheet off: torn zigzag top appears, [capture] snapshots it, the paper spins away on springs,
     * then [onTorn] receives the snapshot (null if capture failed).
     */
    fun tear(flyPx: Float, capture: suspend () -> ImageBitmap?, onTorn: (ImageBitmap?) -> Unit) {
        if (phase != PrintPhase.Printed) return
        job?.cancel()
        job = scope.launch {
            phase = PrintPhase.Tearing
            tornTop = true
            haptics.thud()
            withFrameNanos { }; withFrameNanos { }
            val bitmap = runCatching { capture() }.getOrNull()
            launch { tearY.animateTo(-flyPx, spring(0.9f, 55f)) }
            launch { tearRot.animateTo(16f, spring(0.4f, 90f)) }
            launch { delay(160); tearAlpha.animateTo(0f, tween(380)) }
            delay(620)
            phase = PrintPhase.Gone
            onTorn(bitmap)
        }
    }
}
