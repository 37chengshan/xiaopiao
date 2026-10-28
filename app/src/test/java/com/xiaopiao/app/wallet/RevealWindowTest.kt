package com.xiaopiao.app.wallet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 掩码自动遮回与剪贴板清理的计时规则（docs/APP-DESIGN.md §5.5）。 */
class RevealWindowTest {
    @Test fun theCardNumberIsVisibleForExactlyThirtySeconds() {
        assertTrue(RevealWindow.visible(startedAt = 1_000, now = 1_000))
        assertTrue(RevealWindow.visible(startedAt = 1_000, now = 1_000 + 29_999))
        assertFalse(RevealWindow.visible(startedAt = 1_000, now = 1_000 + 30_000))
        assertFalse(RevealWindow.visible(startedAt = 1_000, now = 900))
    }

    @Test fun theClipboardIsClearedAfterSixtySeconds() {
        assertFalse(RevealWindow.shouldClearClipboard(copiedAt = 5_000, now = 5_000 + 59_999))
        assertTrue(RevealWindow.shouldClearClipboard(copiedAt = 5_000, now = 5_000 + 60_000))
    }
}
