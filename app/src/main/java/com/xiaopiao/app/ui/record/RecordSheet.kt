package com.xiaopiao.app.ui.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.parse.ParsedEntry
import com.xiaopiao.app.ui.components.XpSheet
import kotlinx.coroutines.launch

enum class RecordMode { Manual, Text, Voice }

/**
 * Where the record sheet currently is. Voice is not a sheet of its own any more: the dock expands
 * inline and hands its transcript to [openText], but the member stays so callers can express intent.
 */
@Stable
class RecordSheetState {
    var mode by mutableStateOf<RecordMode?>(null)
        private set
    var textPrefill by mutableStateOf<String?>(null)
        private set

    fun openManual() {
        textPrefill = null
        mode = RecordMode.Manual
    }

    fun openText(prefill: String? = null) {
        textPrefill = prefill
        mode = RecordMode.Text
    }

    /** Voice lives on the dock; opening it is always the text path with an optional transcript. */
    fun openVoice() = openText(null)

    fun dismiss() {
        mode = null
        textPrefill = null
    }
}

@Composable
fun rememberRecordSheetState(): RecordSheetState = remember { RecordSheetState() }

/**
 * Owns the manual / 一句话 bottom sheet. Renders nothing while closed, and writes to the ledger only
 * from an explicit confirm action (「记下」 or 「确认记账」).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheetHost(state: RecordSheetState, container: AppContainer) {
    val mode = state.mode ?: return
    val scope = rememberCoroutineScope()

    XpSheet(onDismiss = state::dismiss) {
        when (mode) {
            RecordMode.Manual -> ManualRecordPane(
                onSubmit = { txn ->
                    scope.launch {
                        container.ledger.add(txn)
                        state.dismiss()
                    }
                },
            )
            // Voice is handled inline by the dock; if it ever lands here it expects a transcript.
            RecordMode.Text, RecordMode.Voice -> TextRecordPane(
                prefill = state.textPrefill,
                onConfirm = { entries ->
                    scope.launch {
                        entries.forEach { entry -> container.ledger.add(entry.toTxn()) }
                        state.dismiss()
                    }
                },
            )
        }
    }
}

private fun ParsedEntry.toTxn(): Txn = Txn(
    amountCents = amountCents,
    direction = direction,
    merchant = merchant,
    category = category,
    occurredAt = occurredAt,
    source = Source.QUICK_ADD,
    note = note,
)
