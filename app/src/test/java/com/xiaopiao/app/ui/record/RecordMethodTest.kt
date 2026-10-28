package com.xiaopiao.app.ui.record

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordMethodTest {
    private fun prefs(): RecordMethodPrefs {
        val file = File.createTempFile("record_method", ".preferences_pb").also { it.delete(); it.deleteOnExit() }
        return RecordMethodPrefs(PreferenceDataStoreFactory.create(produceFile = { file }))
    }

    @Test fun unknownOrMissingFallsBackToManual() {
        assertEquals(RecordMethod.Manual, RecordMethod.fromStored(null))
        assertEquals(RecordMethod.Manual, RecordMethod.fromStored("voice"))
    }

    @Test fun storedValuesRoundTrip() {
        RecordMethod.entries.forEach { assertEquals(it, RecordMethod.fromStored(it.stored)) }
    }

    @Test fun defaultsToManualThenPersistsChoice() = runBlocking {
        val p = prefs()
        assertEquals(RecordMethod.Manual, p.observe().first())
        p.set(RecordMethod.Text)
        assertEquals(RecordMethod.Text, p.observe().first())
        p.set(RecordMethod.Manual)
        assertEquals(RecordMethod.Manual, p.observe().first())
    }

    @Test fun openDefaultRoutesToMatchingPane() {
        val state = RecordSheetState()
        state.openDefault(RecordMethod.Text)
        assertEquals(RecordMode.Text, state.mode)
        state.openDefault(RecordMethod.Manual)
        assertEquals(RecordMode.Manual, state.mode)
    }
}
