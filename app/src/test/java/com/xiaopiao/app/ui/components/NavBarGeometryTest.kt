package com.xiaopiao.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavBarGeometryTest {
    private val widths = listOf(320f, 360f, 375f, 411f, 440f, 480f, 600f)

    @Test fun plusIsOnTheExactScreenCentreInTheLedgerState() {
        widths.forEach { assertEquals(it / 2f, NavBarGeometry.layout(it).plus0, 0.001f) }
    }

    @Test fun capsulesNeverOverlapAndStayOnScreen() {
        widths.forEach { w ->
            val g = NavBarGeometry.layout(w)
            assertTrue("ledger gap at $w", g.rightCap0.start - g.leftCap0.end >= NavBarGeometry.GAP - 0.001f)
            assertTrue("social gap at $w", g.rightCap1.start - g.leftCap1.end >= NavBarGeometry.GAP - 0.001f)
            listOf(g.leftCap0, g.rightCap0, g.leftCap1, g.rightCap1).forEach {
                assertTrue("on screen at $w", it.start >= NavBarGeometry.MARGIN - 0.001f && it.end <= w - NavBarGeometry.MARGIN + 0.001f)
            }
        }
    }

    @Test fun everyItemSitsInsideItsCapsule() {
        widths.forEach { w ->
            val g = NavBarGeometry.layout(w)
            val h = g.slot / 2f
            fun inside(c: Float, cap: NavSpan) = c - h >= cap.start - 0.001f && c + h <= cap.end + 0.001f
            assertTrue(inside(g.a0, g.leftCap0)); assertTrue(inside(g.b0, g.leftCap0)); assertTrue(inside(g.c0, g.leftCap0))
            assertTrue(inside(g.entry0, g.rightCap0))
            assertTrue(inside(g.fold1, g.leftCap1)); assertTrue(inside(g.plus1, g.leftCap1))
            assertTrue(inside(g.sa1, g.rightCap1)); assertTrue(inside(g.entry1, g.rightCap1)); assertTrue(inside(g.sb1, g.rightCap1))
        }
    }

    @Test fun slotIsWideEnoughForTwoCjkCharsAtBigFonts() {
        // 10sp label × 1.3 font scale × 2 CJK chars = 26dp; three chars (记一笔 on the folded ⊕) = 39dp. Real phone: 360dp.
        assertTrue(NavBarGeometry.slot(360f) >= 52f)
        // 记一笔 (3 chars) must fit its slot at 1.3x: 3 × 10sp × 1.3 = 39dp.
        assertTrue(NavBarGeometry.slot(360f) >= 39f)
        widths.filter { it >= 320f }.forEach { assertTrue("slot at $it", NavBarGeometry.slot(it) >= 44f) }
        assertEquals(NavBarGeometry.MAX_SLOT, NavBarGeometry.slot(600f), 0.001f)
    }

    @Test fun capsulesKeepRoomyAirBetweenThemAtPhoneWidths() {
        listOf(360f, 411f).forEach { w ->
            val g = NavBarGeometry.layout(w)
            assertTrue("ledger gap at $w", g.rightCap0.start - g.leftCap0.end >= NavBarGeometry.GAP - 0.001f)
        }
    }

    @Test fun raisedPlusStaysInsideTheBarHeadroom() {
        // Centre of the disc in the 记账 state, from the top of the bar box: capsule centre − RAISE; the ring must not leave the box.
        val centre = NavBarGeometry.TOP + NavBarGeometry.HEIGHT / 2f - NavBarGeometry.RAISE
        val outer = NavBarGeometry.PLUS + NavBarGeometry.RING * 2
        assertTrue(centre - outer / 2f >= 0f)
        // It stands proud of the capsule top, but not by more than a third of its own size.
        val proud = NavBarGeometry.TOP - (centre - outer / 2f)
        assertTrue(proud in 4f..outer / 3f)
    }

    @Test fun leftToRightOrderIsPreservedInBothStates() {
        widths.forEach { w ->
            val g = NavBarGeometry.layout(w)
            assertTrue(g.a0 < g.b0 && g.b0 < g.plus0 && g.plus0 < g.c0 && g.c0 < g.entry0)
            assertTrue(g.fold1 < g.plus1 && g.plus1 < g.sa1 && g.sa1 < g.entry1 && g.entry1 < g.sb1)
        }
    }
}
