package com.xiaopiao.app.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintTimelineTest {
    private val lines = listOf(
        ReceiptLine(LineKind.BLANK),
        ReceiptLine(LineKind.TITLE, "今日小票"),
        ReceiptLine(LineKind.DASH),
        ReceiptLine(LineKind.ITEM, "🍚 午饭", "¥25", 2500),
        ReceiptLine(LineKind.TOTAL, "合计", "¥25", 2500),
        ReceiptLine(LineKind.STAMP_SPACE, "钱包休眠者"),
    )

    @Test fun stepsAreMonotonicAndContiguousInHeight() {
        val tl = PrintTimeline.build(lines)
        assertEquals(lines.size, tl.steps.size)
        tl.steps.zipWithNext().forEach { (a, b) ->
            assertTrue(b.startMs >= a.endMs)
            assertEquals(a.topDp + a.heightDp, b.topDp, 0.001f)
        }
        assertEquals(ReceiptLayout.heightDp(lines), tl.totalHeightDp, 0.001f)
    }

    @Test fun stampComesAfterAllLinesAndTotalIncludesSlam() {
        val tl = PrintTimeline.build(lines)
        assertTrue(tl.stampStartMs > tl.feedEndMs)
        assertTrue(tl.totalMs > tl.stampStartMs)
    }

    @Test fun reducedMotionCollapsesToFinalState() {
        val tl = PrintTimeline.build(lines, reduced = true)
        assertEquals(0L, tl.totalMs)
        assertTrue(tl.steps.all { it.startMs == 0L && it.durationMs == 0L })
        assertEquals(tl.totalHeightDp, tl.revealedHeightDp(0), 0.001f)
        assertEquals(1f, tl.progress(tl.steps[3], 0), 0f)
    }

    @Test fun revealedHeightGrowsWithTime() {
        val tl = PrintTimeline.build(lines)
        assertEquals(0f, tl.revealedHeightDp(-1), 0f)
        var prev = 0f
        for (t in 0..tl.totalMs step 50) {
            val h = tl.revealedHeightDp(t)
            assertTrue(h >= prev - 0.001f)
            prev = h
        }
        assertEquals(tl.totalHeightDp, tl.revealedHeightDp(tl.feedEndMs), 0.001f)
    }

    @Test fun activeIndexFollowsClock() {
        val tl = PrintTimeline.build(lines)
        assertEquals(-1, tl.activeIndex(-5))
        assertEquals(lines.size - 1, tl.activeIndex(tl.feedEndMs))
    }

    @Test fun ticksSkipBlankDashStamp() {
        val tl = PrintTimeline.build(lines)
        assertFalse(tl.steps[0].ticks)
        assertFalse(tl.steps[2].ticks)
        assertTrue(tl.steps[3].ticks)
        assertFalse(tl.steps[5].ticks)
    }

    @Test fun emptyInputIsSafe() {
        val tl = PrintTimeline.build(emptyList())
        assertEquals(0f, tl.totalHeightDp, 0f)
        assertEquals(-1, tl.activeIndex(100))
    }
}
