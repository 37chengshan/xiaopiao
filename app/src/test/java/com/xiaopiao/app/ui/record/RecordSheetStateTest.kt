package com.xiaopiao.app.ui.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordSheetStateTest {
    @Test
    fun dismissRequestsAnimationBeforeClearingMode() {
        val state = RecordSheetState()
        state.openManual()
        assertEquals(RecordMode.Manual, state.mode)

        state.dismiss()
        assertTrue(state.closing)
        assertEquals(RecordMode.Manual, state.mode)

        state.finishDismiss()
        assertNull(state.mode)
        assertFalse(state.closing)
    }
}
