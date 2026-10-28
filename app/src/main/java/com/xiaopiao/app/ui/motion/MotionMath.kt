package com.xiaopiao.app.ui.motion

import kotlin.math.sqrt

/**
 * Pure motion math. Kept free of Compose so it can be unit-tested on the JVM
 * (`MotionMathTest`): timings, stagger delays and the dt-driven spring integrator that the
 * splash hand-off and the press feedback use.
 */
object MotionMath {

    /** Entrance length for a page block; short enough to never feel like waiting. */
    const val ENTER_MS = 220

    /** Distance between two staggered siblings. */
    const val STAGGER_STEP_MS = 45

    /** Long lists stop staggering here, so item 40 does not start half a second late. */
    const val STAGGER_MAX_MS = 320

    /** Press feedback must land on the first frame (docs/APP-DESIGN.md §7.5). */
    const val PRESS_MS = 100

    /** Delay before sibling [index] may enter: 0, 45, 90, … capped at [STAGGER_MAX_MS]. */
    fun staggerDelayMs(index: Int, stepMs: Int = STAGGER_STEP_MS, maxMs: Int = STAGGER_MAX_MS): Int {
        if (index <= 0) return 0
        return minOf(index * stepMs, maxMs)
    }

    /**
     * Strong ease-out (≈ cubic-bezier(0.23, 1, 0.32, 1)): everything that enters moves fast first and
     * settles slowly. Matches `XpEase.out` closely enough that mixing the two is invisible.
     */
    fun easeOutStrong(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        val inv = 1f - x
        return 1f - inv * inv * inv
    }

    /** Linear 0..1 ramp for a one-shot animation, honouring a delay; returns 1 once finished. */
    fun progress(elapsedMs: Float, durationMs: Int, delayMs: Int = 0): Float {
        if (durationMs <= 0) return 1f
        val t = (elapsedMs - delayMs) / durationMs
        return t.coerceIn(0f, 1f)
    }

    /**
     * One step of a damped spring at [dtSeconds]. Integrates the same equation on every device, so a
     * 120Hz screen and a 60Hz screen cover the same wall-clock distance (the per-frame step differs).
     * [dampingRatio] 1 = critically damped (no overshoot), which is the app default.
     */
    fun springStep(
        position: Float,
        velocity: Float,
        target: Float,
        dtSeconds: Float,
        stiffness: Float = 380f,
        dampingRatio: Float = 1f,
    ): Pair<Float, Float> {
        if (dtSeconds <= 0f) return position to velocity
        // Clamp huge gaps (e.g. after a stall) so the integration stays stable.
        val dt = dtSeconds.coerceAtMost(1f / 30f)
        val damping = 2f * dampingRatio * sqrt(stiffness)
        val acceleration = -stiffness * (position - target) - damping * velocity
        val v = velocity + acceleration * dt
        val p = position + v * dt
        return p to v
    }

    /** True once the spring is visually at rest (0.5‰ of the travelling distance and 1 unit/s). */
    fun springSettled(position: Float, velocity: Float, target: Float, epsilon: Float = 0.0005f): Boolean =
        kotlin.math.abs(position - target) < epsilon && kotlin.math.abs(velocity) < 1f

    /** Frame-time delta in seconds from two `withFrameNanos` stamps, clamped for stalled frames. */
    fun dtSeconds(previousNanos: Long, nowNanos: Long): Float {
        if (previousNanos <= 0L) return 0f
        return ((nowNanos - previousNanos).coerceAtLeast(0L) / 1_000_000_000.0).toFloat().coerceAtMost(1f / 30f)
    }
}

/**
 * Splash timeline (docs/APP-DESIGN.md §2.5). Total is hard-capped at [MAX_TOTAL_MS] so the app is
 * always usable inside 1.2s, and skipping cuts straight to [FADE_MS].
 */
object SplashTimeline {
    /** Time to let the system splash icon (AVD) show before the hand-off starts. */
    const val HOLD_MS = 120

    /** Paper flies from the logo position to the receipt control. */
    const val FLIGHT_MS = 300

    /** Overlay fades away once the paper has landed (or when there is no control to land on). */
    const val FADE_MS = 160

    /** Hard cap for the whole cold-start animation. */
    const val MAX_TOTAL_MS = 1200

    /**
     * When to give up waiting for a receipt control to report its bounds and fade the paper out
     * anyway. Leaves room for the flight and the fade inside [MAX_TOTAL_MS].
     */
    const val TARGET_WAIT_MS = MAX_TOTAL_MS - FLIGHT_MS - FADE_MS - HOLD_MS

    /** Total wall-clock length for the given plan. */
    fun totalMs(hasTarget: Boolean): Int =
        if (hasTarget) HOLD_MS + FLIGHT_MS + FADE_MS else HOLD_MS + FADE_MS

    /** True when the plan still fits the 1.2s budget; used by the unit test as a guard. */
    fun fitsBudget(hasTarget: Boolean): Boolean = totalMs(hasTarget) <= MAX_TOTAL_MS
}
