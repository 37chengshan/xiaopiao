package com.xiaopiao.app.ui.record

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    /** Tapping 「＋」: opens whichever input the user picked as default in settings. */
    fun openDefault(method: RecordMethod) = when (method) {
        RecordMethod.Manual -> openManual()
        RecordMethod.Text -> openText()
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

    val toast = com.xiaopiao.app.ui.components.rememberXpToast()
    XpSheet(onDismiss = state::dismiss) {
        Box(Modifier.fillMaxWidth()) {
        when (mode) {
            RecordMode.Manual -> ManualRecordPane(
                onSubmit = { txn, keepOpen ->
                    scope.launch {
                        container.ledger.add(txn)
                        if (keepOpen) toast.show("已记 1 笔") else state.dismiss()
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
            // Inside a sheet the "bottom of the screen" is the keypad, so the toast sits under the grabber
            // instead of covering the keys (§7.7 position adapted to the sheet, noted in the lane report).
            com.xiaopiao.app.ui.components.XpToastHost(
                toast,
                Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top = 24.dp),
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
