package com.xiaopiao.app.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TearPhysicsTest {
    @Test fun downwardDragIsOneToOne() {
        assertEquals(137f, TearPhysics.offsetFor(137f, 2000f), 0f)
    }

    @Test fun upwardDragRubberBandsAndStaysBounded() {
        val a = TearPhysics.offsetFor(-100f, 2000f)
        val b = TearPhysics.offsetFor(-1000f, 2000f)
        assertTrue(a < 0f && b < a)
        assertTrue(-b < 1000f * 0.55f)
        assertEquals(0f, TearPhysics.rubberBand(-5f, 2000f), 0f)
    }

    @Test fun releasePastThresholdTears() {
        assertTrue(TearPhysics.shouldTear(offset = 130f, velocity = 0f, threshold = 120f))
    }

    @Test fun fastFlickTearsFromShortTravelButTinyNudgeDoesNot() {
        assertTrue(TearPhysics.shouldTear(offset = 60f, velocity = 3000f, threshold = 120f))
        assertFalse(TearPhysics.shouldTear(offset = 10f, velocity = 6000f, threshold = 120f))
    }

    @Test fun slowShortDragSpringsBack() {
        assertFalse(TearPhysics.shouldTear(offset = 60f, velocity = 100f, threshold = 120f))
    }

    @Test fun tiltIsCapped() {
        assertEquals(2.2f * 1.2f, TearPhysics.dragTilt(10_000f, 120f), 0.001f)
        assertEquals(0f, TearPhysics.dragTilt(-5f, 120f), 0f)
    }
}
