package com.xiaopiao.app.work

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 看门狗只在「读取开着、但确实没连上、且不是刚连上」时才去重连，避免和系统抢。 */
class CaptureWatchRuleTest {
    private val now = 1_800_000_000_000L

    @Test fun neverRepairsWhenListenerIsOff() {
        assertFalse(CaptureWatchRule.shouldRepair(enabled = false, connected = false, connectedSince = 0L, now = now))
    }

    @Test fun neverRepairsWhileConnected() {
        assertFalse(CaptureWatchRule.shouldRepair(enabled = true, connected = true, connectedSince = 0L, now = now))
    }

    @Test fun leavesAFreshBindingAlone() {
        val justNow = now - 5_000L
        assertFalse(CaptureWatchRule.shouldRepair(enabled = true, connected = false, connectedSince = justNow, now = now))
    }

    @Test fun repairsWhenTheBindingIsStale() {
        val hoursAgo = now - 6 * 3_600_000L
        assertTrue(CaptureWatchRule.shouldRepair(enabled = true, connected = false, connectedSince = hoursAgo, now = now))
    }

    @Test fun repairsWhenWeNeverConnectedInThisProcess() {
        assertTrue(CaptureWatchRule.shouldRepair(enabled = true, connected = false, connectedSince = 0L, now = now))
    }

    @Test fun repairsWhenTheClockWentBackwards() {
        val future = now + 3_600_000L
        assertTrue(CaptureWatchRule.shouldRepair(enabled = true, connected = false, connectedSince = future, now = now))
    }
}
