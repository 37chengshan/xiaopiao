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
import com.xiaopiao.app.AppContainer
import com.xiaopiao.app.domain.Source
import com.xiaopiao.app.domain.Txn
import com.xiaopiao.app.domain.parse.ParsedEntry
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.xiaopiao.app.ui.tr

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
    /** Set while the manual pane is editing an existing row (docs/DESIGN.md §7). */
    var editing by mutableStateOf<Txn?>(null)
        private set
    internal var onDeleteEditing: (Txn) -> Unit = {}
        private set
    /** A new row pre-filled by voice; the pane saves it as an add. */
    var prefill by mutableStateOf<Txn?>(null)
        private set
    /** True while the sheet is playing its exit animation; the mode stays set until it lands. */
    var closing by mutableStateOf(false)
        private set

    fun openManual(prefill: Txn? = null) {
        textPrefill = null
        editing = null
        this.prefill = prefill
        closing = false
        mode = RecordMode.Manual
    }

    /** 编辑账单 = the same 记一笔 pane, pre-filled; delete goes back to the caller (it owns the undo toast). */
    fun openEdit(txn: Txn, onDelete: (Txn) -> Unit) {
        textPrefill = null
        editing = txn
        prefill = null
        onDeleteEditing = onDelete
        closing = false
        mode = RecordMode.Manual
    }

    fun openText(prefill: String? = null) {
        textPrefill = prefill
        closing = false
        mode = RecordMode.Text
    }

    /** Voice lives on the dock; opening it is always the text path with an optional transcript. */
    fun openVoice() = openText(null)

    /** Ask the sheet to leave; the host animates it and calls [finishDismiss] when it has landed. */
    fun dismiss() {
        if (mode != null) closing = true
    }

    internal fun finishDismiss() {
        mode = null
        textPrefill = null
        editing = null
        prefill = null
        closing = false
    }
}

@Composable
fun rememberRecordSheetState(): RecordSheetState = remember { RecordSheetState() }

/**
 * Owns the manual / 一句话 bottom sheet. Renders nothing while closed, and writes to the ledger only
 * from an explicit confirm action (「记下」 or 「确认记账」).
 */
@Composable
fun RecordSheetHost(state: RecordSheetState, container: AppContainer) {
    val mode = state.mode ?: return
    val scope = rememberCoroutineScope()
    val defs = com.xiaopiao.app.ui.category.rememberCategoryDefs(container.categories)
    var managerOpen by remember { mutableStateOf(false) }

    val toast = com.xiaopiao.app.ui.components.rememberXpToast()
    RecordSheetFrame(
        closing = state.closing,
        onRequestClose = state::dismiss,
        onClosed = state::finishDismiss,
    ) {
        Box(Modifier.fillMaxWidth()) {
        when (mode) {
            // Keyed by the row so switching from one edit to another (or to 记一笔) starts a fresh draft.
            RecordMode.Manual -> androidx.compose.runtime.key(state.editing?.id, state.prefill) {
                val editing = state.editing
                ManualRecordPane(
                    defs = defs,
                    onClose = state::dismiss,
                    onOpenCategoryManager = { managerOpen = true },
                    editing = editing,
                    prefill = state.prefill,
                    onDelete = { editing?.let { state.onDeleteEditing(it) }; state.dismiss() },
                    onSubmit = { txn, keepOpen ->
                        scope.launch {
                            if (editing != null) container.ledger.update(txn) else container.ledger.add(txn)
                            if (keepOpen) toast.show(tr("已记 1 笔", "1 entry recorded")) else state.dismiss()
                        }
                    },
                )
            }
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

    if (managerOpen) {
        com.xiaopiao.app.ui.category.CategoryManagerDialog(container.categories) { managerOpen = false }
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
