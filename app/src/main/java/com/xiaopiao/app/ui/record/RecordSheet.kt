package com.xiaopiao.app.ui.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.xiaopiao.app.AppContainer

/** Placeholder — replaced by the r3-record lane. */
enum class RecordMode { Manual, Text, Voice }

class RecordSheetState {
    var mode by mutableStateOf<RecordMode?>(null)
        private set
    fun openManual() { mode = RecordMode.Manual }
    fun openText(@Suppress("UNUSED_PARAMETER") prefill: String? = null) { mode = RecordMode.Text }
    fun openVoice() { mode = RecordMode.Voice }
    fun dismiss() { mode = null }
}

@Composable
fun rememberRecordSheetState(): RecordSheetState = remember { RecordSheetState() }

@Composable
fun RecordSheetHost(@Suppress("UNUSED_PARAMETER") state: RecordSheetState, @Suppress("UNUSED_PARAMETER") container: AppContainer) {
    // no-op until the record lane lands
}
