package com.xiaopiao.app.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionMathTest {

    @Test
    fun staggerStartsAtZeroAndCapsSoLongListsNeverCrawl() {
        assertEquals(0, MotionMath.staggerDelayMs(0))
        assertEquals(0, MotionMath.staggerDelayMs(-3))
        assertEquals(45, MotionMath.staggerDelayMs(1))
        assertEquals(180, MotionMath.staggerDelayMs(4))
        // Cap: item 8 would be 360ms, the cap keeps it at 320.
        assertEquals(MotionMath.STAGGER_MAX_MS, MotionMath.staggerDelayMs(8))
        assertEquals(MotionMath.STAGGER_MAX_MS, MotionMath.staggerDelayMs(400))
    }

    @Test
    fun easeOutStrongIsClampedAndMonotonic() {
        assertEquals(0f, MotionMath.easeOutStrong(-1f), 0f)
        assertEquals(0f, MotionMath.easeOutStrong(0f), 0f)
        assertEquals(1f, MotionMath.easeOutStrong(2f), 0f)
        var previous = -1f
        for (i in 0..100) {
            val v = MotionMath.easeOutStrong(i / 100f)
            assertTrue("ease must not go backwards at $i", v >= previous)
            assertTrue("ease must stay in 0..1 at $i", v in 0f..1f)
            previous = v
        }
        // Strong ease-out: most of the distance is covered in the first third of the time.
        assertTrue(MotionMath.easeOutStrong(1f / 3f) > 0.55f)
    }

    @Test
    fun progressHonoursDelayAndDuration() {
        assertEquals(0f, MotionMath.progress(0f, 220), 0f)
        assertEquals(0f, MotionMath.progress(50f, 220, delayMs = 100), 0f)
        assertEquals(0.5f, MotionMath.progress(110f, 220), 0.001f)
        assertEquals(1f, MotionMath.progress(220f, 220), 0f)
        assertEquals(1f, MotionMath.progress(900f, 220), 0f)
        // A zero-length animation is already finished (reduced motion / disabled).
        assertEquals(1f, MotionMath.progress(0f, 0), 0f)
    }

    @Test
    fun criticallyDampedSpringConvergesWithoutOvershoot() {
        var p = 0f
        var v = 0f
        var frames = 0
        var maxValue = 0f
        while (!MotionMath.springSettled(p, v, 1f) && frames < 600) {
            val (np, nv) = MotionMath.springStep(p, v, 1f, 1f / 60f)
            p = np
            v = nv
            maxValue = maxOf(maxValue, p)
            frames++
        }
        assertTrue("spring must settle, took $frames frames", frames < 600)
        assertTrue("critically damped spring must not overshoot, peaked at $maxValue", maxValue <= 1.0005f)
        assertEquals(1f, p, 0.01f)
    }

    @Test
    fun springReachesTheTargetInAboutTheSameWallClockAt60And120Hz() {
        fun framesToSettle(hz: Int): Int {
            var p = 0f
            var v = 0f
            var n = 0
            while (!MotionMath.springSettled(p, v, 1f) && n < 2000) {
                val (np, nv) = MotionMath.springStep(p, v, 1f, 1f / hz)
                p = np
                v = nv
                n++
            }
            return n
        }
        val ms60 = framesToSettle(60) * (1000f / 60f)
        val ms120 = framesToSettle(120) * (1000f / 120f)
        // Time-based integration: the wall-clock difference stays far below one animation (a frame-counted
        // animation would finish in half the time at 120Hz, i.e. hundreds of ms apart).
        assertTrue("60Hz settled at ${ms60}ms, 120Hz at ${ms120}ms", kotlin.math.abs(ms60 - ms120) < 60f)
        assertTrue("both should land in the same ballpark", ms60 < 1200f && ms120 < 1200f)
    }

    @Test
    fun springStepIgnoresZeroOrNegativeDt() {
        val (p, v) = MotionMath.springStep(0.2f, 3f, 1f, 0f)
        assertEquals(0.2f, p, 0f)
        assertEquals(3f, v, 0f)
    }

    @Test
    fun dtSecondsClampsStalledFrames() {
        // First frame after a cold start has no previous stamp: no motion yet.
        assertEquals(0f, MotionMath.dtSeconds(0L, 1_000_000_000L), 0f)
        assertEquals(0.016f, MotionMath.dtSeconds(1_000_000_000L, 1_016_000_000L), 0.0005f)
        // A 2s stall must not be integrated in one step (that would explode the spring).
        assertEquals(1f / 30f, MotionMath.dtSeconds(2_000_000_000L, 4_000_000_000L), 0.0001f)
    }

    @Test
    fun splashTimelineFitsTheOneSecondTwoBudget() {
        assertTrue(SplashTimeline.fitsBudget(hasTarget = true))
        assertTrue(SplashTimeline.fitsBudget(hasTarget = false))
        assertTrue(SplashTimeline.totalMs(hasTarget = true) <= 1200)
        assertTrue(SplashTimeline.totalMs(hasTarget = false) < SplashTimeline.totalMs(hasTarget = true))
        assertFalse(SplashTimeline.TARGET_WAIT_MS <= 0)
        // Skips must be able to finish inside the budget too.
        assertTrue(SplashTimeline.FADE_MS < SplashTimeline.MAX_TOTAL_MS)
    }
}
