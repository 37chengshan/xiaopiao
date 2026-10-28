package com.xiaopiao.app.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PrintPhase { Closed, Printing, Printed, Tearing }

/**
 * One print: Closed -> Printing -> Printed -> Tearing -> Closed.
 * Every moving value is Compose state / Animatable read lazily by layout, draw and graphicsLayer blocks,
 * so frames never recompose the screen. Haptics and sound always fire in the same call.
 */
@Stable
class PrintSession(
    private val scope: CoroutineScope,
    private val haptics: ReceiptHaptics,
    private val sound: ReceiptSound,
) {
    var phase by mutableStateOf(PrintPhase.Closed); private set
    /** True once the paper has been pulled past the perforation: its top edge shows the torn zigzag. */
    var tornTop by mutableStateOf(false); private set

    /** 0..1: how present the overlay is (scrim, slot, capsule, and the screen behind pushing back to 0.97). */
    val present = Animatable(0f)
    /** Print clock in ms. Linear on purpose: the paper feed is the one linear motion in the app. */
    val elapsed = Animatable(0f)
    val stampScale = Animatable(STAMP_FROM)
    val stampAlpha = Animatable(0f)
    val stampRing = Animatable(0f)
    /** Little downward kick the paper takes when the stamp lands (dp). */
    val kick = Animatable(0f)

    /** Paper offset in px: finger-tracked 1:1 while dragging, then spring / fling. */
    var dragY by mutableFloatStateOf(0f); private set
    var tilt by mutableFloatStateOf(0f); private set
    var paperAlpha by mutableFloatStateOf(1f); private set

    private var job: Job? = null
    private var settle: Job? = null
    private var crossed = false

    private suspend fun resetVisuals() {
        elapsed.snapTo(0f); stampScale.snapTo(STAMP_FROM); stampAlpha.snapTo(0f); stampRing.snapTo(0f); kick.snapTo(0f)
        dragY = 0f; tilt = 0f; paperAlpha = 1f; tornTop = false; crossed = false
    }

    // ---- print ----------------------------------------------------------

    fun open(timeline: PrintTimeline, reduced: Boolean) {
        job?.cancel(); settle?.cancel()
        job = scope.launch {
            resetVisuals()
            phase = PrintPhase.Printing
            if (reduced || timeline.totalMs == 0L) {
                present.animateTo(1f, tween(150))
                showFinal(timeline)
                return@launch
            }
            launch { present.animateTo(1f, tween(240, easing = EaseOut)) }
            delay(PAPER_DELAY_MS)
            coroutineScope {
                launch { elapsed.animateTo(timeline.feedEndMs.toFloat(), tween(timeline.feedEndMs.toInt(), easing = LinearEasing)) }
                launch { beats(timeline) }
            }
            delay(timeline.stampStartMs - timeline.feedEndMs)
            slam()
            phase = PrintPhase.Printed
        }
    }

    /** 跳过: straight to the finished paper. */
    fun skip(timeline: PrintTimeline) {
        if (phase != PrintPhase.Printing) return
        job?.cancel()
        job = scope.launch { showFinal(timeline); haptics.confirm() }
    }

    private suspend fun showFinal(timeline: PrintTimeline) {
        elapsed.snapTo(timeline.totalMs.coerceAtLeast(timeline.feedEndMs).toFloat())
        stampScale.snapTo(1f); stampAlpha.snapTo(1f); stampRing.snapTo(0f)
        phase = PrintPhase.Printed
    }

    /** Haptic tick + printer chatter for each printed line, driven by the same clock the paper reads. */
    private suspend fun beats(timeline: PrintTimeline) {
        val times = timeline.tickTimes(BEAT_GAP_MS)
        var i = 0
        while (i < times.size) {
            withFrameNanos { }
            while (i < times.size && elapsed.value >= times[i]) { haptics.tick(); sound.tick(); i++ }
        }
    }

    /** Stamp slams down: 1.5x -> 1x on an underdamped spring, ink ring spreads, paper kicks, strong haptic + thump. */
    private suspend fun slam() = coroutineScope {
        stampScale.snapTo(STAMP_FROM); stampAlpha.snapTo(0f); stampRing.snapTo(0f)
        launch { stampAlpha.animateTo(1f, tween(70)) }
        launch { stampRing.animateTo(1f, tween(560, easing = FastOutSlowInEasing)) }
        launch {
            delay(IMPACT_MS)
            haptics.thud(); sound.thump()
            kick.snapTo(6f)
            kick.animateTo(0f, spring(0.3f, 650f))
        }
        stampScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 480f))
    }

    // ---- tear -----------------------------------------------------------

    fun dragStart() {
        settle?.cancel()
        if (phase == PrintPhase.Printed) paperAlpha = 1f
    }

    /** [travel] is the finger's total vertical movement in px (down positive). */
    fun dragTo(travel: Float, thresholdPx: Float, dimensionPx: Float) {
        if (phase != PrintPhase.Printed) return
        dragY = TearPhysics.offsetFor(travel, dimensionPx)
        tilt = TearPhysics.dragTilt(dragY, thresholdPx)
        val past = dragY >= thresholdPx
        if (past != crossed) { crossed = past; haptics.threshold() }
        tornTop = past
    }

    /** Finger lifted at [velocityPx] px/s: tear if the projected landing clears the perforation, else spring back. */
    fun dragEnd(velocityPx: Float, thresholdPx: Float, flyToPx: Float, onRelease: () -> Unit) {
        if (phase != PrintPhase.Printed) return
        if (TearPhysics.shouldTear(dragY, velocityPx, thresholdPx)) tear(velocityPx, flyToPx, onRelease)
        else settle = scope.launch {
            tornTop = false; crossed = false
            launch { animate(tilt, 0f, 0f, spring(1f, 300f)) { v, _ -> tilt = v } }
            animate(dragY, 0f, velocityPx, spring(dampingRatio = 0.8f, stiffness = 330f)) { v, _ -> dragY = v }
        }
    }

    /**
     * Rips the sheet off along the zigzag and flings it away with rotation. [onRelease] fires while the paper is
     * still leaving (the share sheet rises as it goes); the overlay then closes by itself.
     */
    fun tear(velocityPx: Float, flyToPx: Float, onRelease: () -> Unit, reduced: Boolean = false) {
        if (phase != PrintPhase.Printed) return
        settle?.cancel(); job?.cancel()
        job = scope.launch {
            phase = PrintPhase.Tearing
            tornTop = true
            haptics.confirm(); sound.tear()
            if (reduced) {
                animate(1f, 0f, 0f, tween(150)) { v, _ -> paperAlpha = v }
                onRelease()
            } else coroutineScope {
                val fling = launch { animate(dragY, flyToPx, velocityPx.coerceAtLeast(MIN_FLING), spring(0.85f, 110f)) { v, _ -> dragY = v } }
                launch { animate(tilt, FLY_TILT, 0f, spring(0.5f, 55f)) { v, _ -> tilt = v } }
                delay(RELEASE_AFTER_MS)
                onRelease()
                fling.join()
            }
            closeNow()
        }
    }

    // ---- close ----------------------------------------------------------

    /** Back to the quiet Today screen. */
    fun close() {
        if (phase == PrintPhase.Closed) return
        job?.cancel(); settle?.cancel()
        job = scope.launch { closeNow() }
    }

    private suspend fun closeNow() {
        present.animateTo(0f, tween(180, easing = EaseOut))
        resetVisuals()
        phase = PrintPhase.Closed
    }

    private companion object {
        const val STAMP_FROM = 1.5f
        const val PAPER_DELAY_MS = 140L
        const val IMPACT_MS = 85L
        const val BEAT_GAP_MS = 240L
        const val RELEASE_AFTER_MS = 260L
        const val MIN_FLING = 900f
        const val FLY_TILT = 13f
        val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    }
}
